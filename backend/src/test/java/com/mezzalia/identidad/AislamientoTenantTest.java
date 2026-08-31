package com.mezzalia.identidad;

import com.mezzalia.tenant.Tenant;
import com.mezzalia.tenant.TenantRepository;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Este es EL test que documentacion-tecnica.md §6 llama "obligatorio, no
 * opcional": prueba automatizada de que un tenant no puede leer datos de
 * otro. Antes de este PR no existía ninguno — ni este ni ningún otro test
 * en el repo.
 *
 * Usa UsuarioInterno como entidad de prueba porque es la primera entidad
 * tenant-scoped real que extiende EntidadConTenant; el mecanismo probado acá
 * (Hibernate @Filter) aplica igual a cualquier entidad futura de menu/pedidos
 * que también extienda EntidadConTenant.
 */
@DataJpaTest
class AislamientoTenantTest {

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private UsuarioInternoRepository usuarioInternoRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Test
    void unTenantNoPuedeLeerUsuariosDeOtroTenantConElFiltroActivado() {
        Tenant tenantA = tenantRepository.save(nuevoTenant("Restaurante A"));
        Tenant tenantB = tenantRepository.save(nuevoTenant("Restaurante B"));

        usuarioInternoRepository.save(nuevoUsuario("admin.a", tenantA.getId()));
        usuarioInternoRepository.save(nuevoUsuario("admin.b", tenantB.getId()));
        testEntityManager.flush();
        testEntityManager.clear();

        Session session = testEntityManager.getEntityManager().unwrap(Session.class);
        session.enableFilter("tenantFilter").setParameter("tenantId", tenantA.getId());
        try {
            List<UsuarioInterno> visiblesParaTenantA = usuarioInternoRepository.findAll();

            assertThat(visiblesParaTenantA)
                    .extracting(UsuarioInterno::getUsername)
                    .containsExactly("admin.a");
        } finally {
            session.disableFilter("tenantFilter");
        }
    }

    /**
     * Documenta a propósito la otra cara del mecanismo: el filtro de
     * Hibernate es OPT-IN por sesión. Si nadie lo activa (por ejemplo, código
     * que abre su propio EntityManager fuera del ciclo de vida normal del
     * request HTTP — un job programado, un hilo async, este mismo test si no
     * llamara a enableFilter), la query NO queda protegida sola. No es un bug
     * de este test: es la razón por la que EntidadConTenant.java advierte
     * explícitamente que esto es defensa en profundidad, no el único control,
     * y por la que cualquier repositorio nuevo debería seguir aceptando
     * filtrar por tenant_id de forma explícita cuando el caso de uso lo pida.
     */
    @Test
    void sinActivarElFiltroSeVenTodosLosTenants_elFiltroEsOptInNoAutomatico() {
        Tenant tenantA = tenantRepository.save(nuevoTenant("Restaurante A"));
        Tenant tenantB = tenantRepository.save(nuevoTenant("Restaurante B"));

        usuarioInternoRepository.save(nuevoUsuario("admin.a", tenantA.getId()));
        usuarioInternoRepository.save(nuevoUsuario("admin.b", tenantB.getId()));
        testEntityManager.flush();
        testEntityManager.clear();

        List<UsuarioInterno> todos = usuarioInternoRepository.findAll();

        assertThat(todos).hasSize(2);
    }

    private Tenant nuevoTenant(String nombre) {
        Tenant tenant = new Tenant();
        tenant.setNombre(nombre);
        tenant.setPlan("STARTER");
        tenant.setMoneda("COP");
        tenant.setZonaHoraria("America/Bogota");
        return tenant;
    }

    private UsuarioInterno nuevoUsuario(String username, Long tenantId) {
        UsuarioInterno usuario = new UsuarioInterno();
        usuario.setUsername(username);
        usuario.setPasswordHash("hash-no-real-solo-para-el-test");
        usuario.setRol(RolUsuario.ADMIN_RESTAURANTE);
        usuario.setTenantId(tenantId);
        usuario.setActivo(true);
        return usuario;
    }
}
