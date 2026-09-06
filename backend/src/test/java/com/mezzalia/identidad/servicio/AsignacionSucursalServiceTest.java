package com.mezzalia.identidad.servicio;

import com.mezzalia.identidad.AsignacionSucursalRepository;
import com.mezzalia.identidad.RolUsuario;
import com.mezzalia.identidad.UsuarioInterno;
import com.mezzalia.identidad.UsuarioInternoRepository;
import com.mezzalia.identidad.excepcion.AsignacionSucursalExceptions;
import com.mezzalia.identidad.excepcion.UsuarioInternoExceptions;
import com.mezzalia.sucursal.Sucursal;
import com.mezzalia.sucursal.SucursalRepository;
import com.mezzalia.sucursal.excepcion.SucursalExceptions;
import com.mezzalia.sucursal.servicio.SucursalService;
import com.mezzalia.tenant.Tenant;
import com.mezzalia.tenant.TenantContext;
import com.mezzalia.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RF-10 PR4 (docs/propuesta-multisucursal-rf10.md §3, decision de negocio
 * ya resuelta): primera cobertura de AsignacionSucursalService (cero tests
 * existian antes, es un servicio nuevo de esta rama). Mismo patron que
 * SucursalServiceTest/UsuarioInternoAdminServiceTest: @DataJpaTest,
 * servicio instanciado a mano.
 *
 * Foco: los invariantes de negocio que un smoke test del camino feliz NUNCA
 * ejercería -- aislamiento de tenant explicito (usuario y sucursal pueden
 * pertenecer a tenants distintos por error de aplicacion), el rol invalido
 * (RolNoPermitidoParaAsignacionException para ADMIN_RESTAURANTE), y la
 * idempotencia real de asignar/desasignar (no solo declarada en un
 * comentario).
 */
@DataJpaTest
class AsignacionSucursalServiceTest {

    @Autowired
    private TestEntityManager testEntityManager;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private UsuarioInternoRepository usuarioInternoRepository;
    @Autowired
    private SucursalRepository sucursalRepository;
    @Autowired
    private AsignacionSucursalRepository asignacionSucursalRepository;

    @AfterEach
    void limpiarContexto() {
        TenantContext.limpiar();
    }

    @Test
    void asignarLeDaLaSucursalAlMeseroYQuedaVisibleEnElListado() {
        AsignacionSucursalService servicio = nuevoServicio();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Asignacion"));
        TenantContext.establecerTenantId(tenant.getId());
        Sucursal sucursal = nuevaSucursal(tenant.getId());
        UsuarioInterno mesero = usuarioInternoRepository.save(nuevoUsuario(tenant.getId(), "mesero1", RolUsuario.MESERO));

        servicio.asignar(mesero.getId(), sucursal.getId());

        assertThat(servicio.listarSucursalesDe(mesero.getId()))
                .extracting(Sucursal::getId)
                .containsExactly(sucursal.getId());
    }

    @Test
    void asignarDosVecesLaMismaSucursalEsUnNoOpSinLanzarNiDuplicar() {
        AsignacionSucursalService servicio = nuevoServicio();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Asignacion Idempotente"));
        TenantContext.establecerTenantId(tenant.getId());
        Sucursal sucursal = nuevaSucursal(tenant.getId());
        UsuarioInterno mesero = usuarioInternoRepository.save(nuevoUsuario(tenant.getId(), "mesero2", RolUsuario.MESERO));

        servicio.asignar(mesero.getId(), sucursal.getId());
        servicio.asignar(mesero.getId(), sucursal.getId());

        assertThat(servicio.listarSucursalesDe(mesero.getId())).hasSize(1);
    }

    @Test
    void desasignarUnaSucursalQueNuncaSeAsignoEsUnNoOpSinLanzar() {
        AsignacionSucursalService servicio = nuevoServicio();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Desasignacion Idempotente"));
        TenantContext.establecerTenantId(tenant.getId());
        Sucursal sucursal = nuevaSucursal(tenant.getId());
        UsuarioInterno mesero = usuarioInternoRepository.save(nuevoUsuario(tenant.getId(), "mesero3", RolUsuario.MESERO));

        servicio.desasignar(mesero.getId(), sucursal.getId());

        assertThat(servicio.listarSucursalesDe(mesero.getId())).isEmpty();
    }

    @Test
    void desasignarQuitaLaSucursalDelListado() {
        AsignacionSucursalService servicio = nuevoServicio();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Desasignacion"));
        TenantContext.establecerTenantId(tenant.getId());
        Sucursal sucursalA = nuevaSucursal(tenant.getId());
        Sucursal sucursalB = nuevaSucursal(tenant.getId());
        UsuarioInterno mesero = usuarioInternoRepository.save(nuevoUsuario(tenant.getId(), "mesero4", RolUsuario.MESERO));
        servicio.asignar(mesero.getId(), sucursalA.getId());
        servicio.asignar(mesero.getId(), sucursalB.getId());

        servicio.desasignar(mesero.getId(), sucursalA.getId());

        assertThat(servicio.listarSucursalesDe(mesero.getId()))
                .extracting(Sucursal::getId)
                .containsExactly(sucursalB.getId());
    }

    @Test
    void noSePuedeAsignarUnaSucursalAUnAdminRestaurante() {
        AsignacionSucursalService servicio = nuevoServicio();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Rol Invalido"));
        TenantContext.establecerTenantId(tenant.getId());
        Sucursal sucursal = nuevaSucursal(tenant.getId());
        UsuarioInterno admin = usuarioInternoRepository.save(nuevoUsuario(tenant.getId(), "admin1", RolUsuario.ADMIN_RESTAURANTE));

        assertThatThrownBy(() -> servicio.asignar(admin.getId(), sucursal.getId()))
                .isInstanceOf(AsignacionSucursalExceptions.RolNoPermitidoParaAsignacionException.class);
    }

    @Test
    void noSePuedeAsignarUnaSucursalDeOtroTenant() {
        AsignacionSucursalService servicio = nuevoServicio();
        Tenant tenantDueno = tenantRepository.save(nuevoTenant("Restaurante Dueno Asignacion"));
        Tenant tenantIntruso = tenantRepository.save(nuevoTenant("Restaurante Intruso Asignacion"));
        TenantContext.establecerTenantId(tenantDueno.getId());
        Sucursal sucursalDelDueno = nuevaSucursal(tenantDueno.getId());

        TenantContext.establecerTenantId(tenantIntruso.getId());
        UsuarioInterno meseroDelIntruso = usuarioInternoRepository.save(
                nuevoUsuario(tenantIntruso.getId(), "mesero.intruso", RolUsuario.MESERO));

        assertThatThrownBy(() -> servicio.asignar(meseroDelIntruso.getId(), sucursalDelDueno.getId()))
                .as("una sucursal de otro tenant debe ser 'no encontrada', nunca un 403 que confirme que existe")
                .isInstanceOf(SucursalExceptions.SucursalNoEncontradaException.class);
    }

    @Test
    void noSePuedeAsignarUnUsuarioDeOtroTenant() {
        AsignacionSucursalService servicio = nuevoServicio();
        Tenant tenantDueno = tenantRepository.save(nuevoTenant("Restaurante Dueno Usuario"));
        Tenant tenantIntruso = tenantRepository.save(nuevoTenant("Restaurante Intruso Usuario"));
        TenantContext.establecerTenantId(tenantIntruso.getId());
        UsuarioInterno meseroDeOtroTenant = usuarioInternoRepository.save(
                nuevoUsuario(tenantIntruso.getId(), "mesero.ajeno", RolUsuario.MESERO));

        TenantContext.establecerTenantId(tenantDueno.getId());
        Sucursal sucursalDelDueno = nuevaSucursal(tenantDueno.getId());

        assertThatThrownBy(() -> servicio.asignar(meseroDeOtroTenant.getId(), sucursalDelDueno.getId()))
                .isInstanceOf(UsuarioInternoExceptions.UsuarioNoEncontradoException.class);
    }

    @Test
    void noSePuedeAsignarUnaSucursalDesactivada() {
        AsignacionSucursalService servicio = nuevoServicio();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Sucursal Desactivada"));
        TenantContext.establecerTenantId(tenant.getId());
        Sucursal principal = nuevaSucursal(tenant.getId());
        Sucursal secundaria = nuevaSucursal(tenant.getId());
        new SucursalService(sucursalRepository).desactivarSucursal(secundaria.getId());
        UsuarioInterno mesero = usuarioInternoRepository.save(nuevoUsuario(tenant.getId(), "mesero5", RolUsuario.MESERO));

        assertThatThrownBy(() -> servicio.asignar(mesero.getId(), secundaria.getId()))
                .isInstanceOf(SucursalExceptions.SucursalNoEncontradaException.class);
    }

    private AsignacionSucursalService nuevoServicio() {
        return new AsignacionSucursalService(asignacionSucursalRepository, usuarioInternoRepository,
                new SucursalService(sucursalRepository));
    }

    private Sucursal nuevaSucursal(Long tenantId) {
        Sucursal sucursal = new Sucursal();
        sucursal.setTenantId(tenantId);
        sucursal.setNombre("Sede " + java.util.UUID.randomUUID());
        sucursal.setActiva(true);
        return sucursalRepository.save(sucursal);
    }

    private UsuarioInterno nuevoUsuario(Long tenantId, String username, RolUsuario rol) {
        UsuarioInterno usuario = new UsuarioInterno();
        usuario.setTenantId(tenantId);
        usuario.setUsername(username);
        usuario.setPasswordHash("hash-no-relevante-para-este-test");
        usuario.setRol(rol);
        usuario.setActivo(true);
        return usuario;
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
