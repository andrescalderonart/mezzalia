package com.mezzalia.identidad.servicio;

import com.mezzalia.identidad.RolUsuario;
import com.mezzalia.identidad.UsuarioInterno;
import com.mezzalia.identidad.UsuarioInternoRepository;
import com.mezzalia.identidad.excepcion.UsuarioInternoExceptions;
import com.mezzalia.tenant.Tenant;
import com.mezzalia.tenant.TenantContext;
import com.mezzalia.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RF-09/documentacion-tecnica.md §2: cobertura del CRUD de usuarios
 * internos que hasta ahora no existía (solo seeds fijos, ver
 * SeedDatosDemoConfig). Mismo patrón de @DataJpaTest + servicio
 * instanciado a mano que el resto del proyecto (ver ComensalServiceTest).
 */
@DataJpaTest
class UsuarioInternoAdminServiceTest {

    @Autowired
    private TestEntityManager testEntityManager;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private UsuarioInternoRepository usuarioInternoRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @AfterEach
    void limpiarContexto() {
        TenantContext.limpiar();
        SecurityContextHolder.clearContext();
    }

    @Test
    void crearUsuarioLoGuardaConElRolYElTenantDelContextoActual() {
        UsuarioInternoAdminService servicio = nuevoServicio();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Usuarios"));
        TenantContext.establecerTenantId(tenant.getId());

        UsuarioInterno creado = servicio.crearUsuario("mesero.nuevo", "Password1234", RolUsuario.MESERO);

        assertThat(creado.getId()).isNotNull();
        assertThat(creado.getTenantId()).isEqualTo(tenant.getId());
        assertThat(creado.isActivo()).isTrue();
        assertThat(passwordEncoder.matches("Password1234", creado.getPasswordHash())).isTrue();
    }

    @Test
    void noSePuedeCrearUnUsuarioConRolSuperAdminDesdeEsteEndpoint() {
        UsuarioInternoAdminService servicio = nuevoServicio();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Sin SuperAdmin"));
        TenantContext.establecerTenantId(tenant.getId());

        assertThatThrownBy(() -> servicio.crearUsuario("intento.superadmin", "Password1234", RolUsuario.SUPER_ADMIN))
                .isInstanceOf(UsuarioInternoExceptions.RolNoPermitidoException.class);
        assertThat(usuarioInternoRepository.existsByUsername("intento.superadmin")).isFalse();
    }

    @Test
    void noSePuedeCrearDosUsuariosConElMismoUsername() {
        UsuarioInternoAdminService servicio = nuevoServicio();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Duplicado"));
        TenantContext.establecerTenantId(tenant.getId());
        servicio.crearUsuario("mesero.uno", "Password1234", RolUsuario.MESERO);
        testEntityManager.flush();

        assertThatThrownBy(() -> servicio.crearUsuario("mesero.uno", "OtraPassword1234", RolUsuario.MESERO))
                .isInstanceOf(UsuarioInternoExceptions.UsuarioYaExisteException.class);
    }

    @Test
    void listarUsuariosSoloDevuelveLosDelTenantActual() {
        UsuarioInternoAdminService servicio = nuevoServicio();
        Tenant tenantA = tenantRepository.save(nuevoTenant("Restaurante A"));
        Tenant tenantB = tenantRepository.save(nuevoTenant("Restaurante B"));

        TenantContext.establecerTenantId(tenantA.getId());
        servicio.crearUsuario("mesero.a", "Password1234", RolUsuario.MESERO);

        TenantContext.establecerTenantId(tenantB.getId());
        servicio.crearUsuario("mesero.b", "Password1234", RolUsuario.MESERO);
        testEntityManager.flush();

        TenantContext.establecerTenantId(tenantA.getId());
        List<UsuarioInterno> usuariosDeA = servicio.listarUsuarios();

        assertThat(usuariosDeA).extracting(UsuarioInterno::getUsername).containsExactly("mesero.a");
    }

    @Test
    void desactivarYReactivarUnUsuarioEsIdempotente() {
        UsuarioInternoAdminService servicio = nuevoServicio();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Toggle"));
        TenantContext.establecerTenantId(tenant.getId());
        UsuarioInterno usuario = servicio.crearUsuario("mesero.toggle", "Password1234", RolUsuario.MESERO);
        testEntityManager.flush();
        testEntityManager.clear();

        servicio.desactivarUsuario(usuario.getId());
        servicio.desactivarUsuario(usuario.getId());
        assertThat(usuarioInternoRepository.findById(usuario.getId()).orElseThrow().isActivo()).isFalse();

        servicio.reactivarUsuario(usuario.getId());
        assertThat(usuarioInternoRepository.findById(usuario.getId()).orElseThrow().isActivo()).isTrue();
    }

    @Test
    void unAdminNoPuedeDesactivarseASiMismo() {
        UsuarioInternoAdminService servicio = nuevoServicio();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Autodesactivar"));
        TenantContext.establecerTenantId(tenant.getId());
        UsuarioInterno admin = servicio.crearUsuario("admin.propio", "Password1234", RolUsuario.ADMIN_RESTAURANTE);
        testEntityManager.flush();
        autenticarComo("admin.propio");

        assertThatThrownBy(() -> servicio.desactivarUsuario(admin.getId()))
                .isInstanceOf(UsuarioInternoExceptions.NoPuedeDesactivarseASiMismoException.class);
        assertThat(usuarioInternoRepository.findById(admin.getId()).orElseThrow().isActivo()).isTrue();
    }

    @Test
    void resetearPasswordCambiaElHashSinRequerirLaAnterior() {
        UsuarioInternoAdminService servicio = nuevoServicio();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Reset"));
        TenantContext.establecerTenantId(tenant.getId());
        UsuarioInterno usuario = servicio.crearUsuario("mesero.reset", "PasswordVieja1", RolUsuario.MESERO);
        testEntityManager.flush();

        servicio.resetearPassword(usuario.getId(), "PasswordNueva1");

        UsuarioInterno recargado = usuarioInternoRepository.findById(usuario.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("PasswordNueva1", recargado.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("PasswordVieja1", recargado.getPasswordHash())).isFalse();
    }

    @Test
    void operarSobreUnUsuarioDeOtroTenantLanzaNoEncontrado() {
        UsuarioInternoAdminService servicio = nuevoServicio();
        Tenant tenantA = tenantRepository.save(nuevoTenant("Restaurante Aislado A"));
        Tenant tenantB = tenantRepository.save(nuevoTenant("Restaurante Aislado B"));

        TenantContext.establecerTenantId(tenantA.getId());
        UsuarioInterno usuarioDeA = servicio.crearUsuario("mesero.aislado", "Password1234", RolUsuario.MESERO);
        testEntityManager.flush();

        TenantContext.establecerTenantId(tenantB.getId());
        assertThatThrownBy(() -> servicio.desactivarUsuario(usuarioDeA.getId()))
                .isInstanceOf(UsuarioInternoExceptions.UsuarioNoEncontradoException.class);
    }

    @Test
    void unSuperAdminSinTenantEnContextoNoPuedeListarUsuarios() {
        UsuarioInternoAdminService servicio = nuevoServicio();

        assertThatThrownBy(servicio::listarUsuarios)
                .isInstanceOf(UsuarioInternoExceptions.SinTenantEnContextoException.class);
    }

    private void autenticarComo(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    private UsuarioInternoAdminService nuevoServicio() {
        return new UsuarioInternoAdminService(usuarioInternoRepository, passwordEncoder);
    }

    private Tenant nuevoTenant(String nombre) {
        Tenant tenant = new Tenant();
        tenant.setNombre(nombre);
        tenant.setPlan("STARTER");
        tenant.setMoneda("COP");
        tenant.setZonaHoraria("America/Bogota");
        return tenant;
    }
}
