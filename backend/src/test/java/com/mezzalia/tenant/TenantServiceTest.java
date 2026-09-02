package com.mezzalia.tenant;

import com.mezzalia.common.excepcion.DomainExceptions;
import com.mezzalia.tenant.servicio.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Primera cobertura de TenantService (cero tests existian antes de este
 * archivo). Mismo patron de MesaServiceTest/PedidoServiceTest: @DataJpaTest,
 * servicio instanciado a mano.
 *
 * Foco: la distincion deliberada entre obtenerPorId (camino publico, respeta
 * RF-11/suspension) y obtenerParaAdministracion (camino admin, no la
 * respeta a proposito -- hace falta poder leer un tenant suspendido para
 * poder gestionarlo/reactivarlo). Ver el javadoc de TenantService para el
 * contrato completo.
 */
@DataJpaTest
class TenantServiceTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Test
    void obtenerPorIdDevuelveElTenantSiEstaActivo() {
        TenantService tenantService = nuevoTenantService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Activo", Tenant.ESTADO_ACTIVO));

        Tenant obtenido = tenantService.obtenerPorId(tenant.getId());

        assertThat(obtenido.getId()).isEqualTo(tenant.getId());
    }

    @Test
    void obtenerPorIdLanzaExcepcionSiElTenantEstaSuspendido() {
        TenantService tenantService = nuevoTenantService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Suspendido", Tenant.ESTADO_SUSPENDIDO));

        assertThatThrownBy(() -> tenantService.obtenerPorId(tenant.getId()))
                .isInstanceOf(DomainExceptions.TenantSuspendidoException.class);
    }

    @Test
    void obtenerPorIdConIdInexistenteLanzaTenantNoEncontrado() {
        TenantService tenantService = nuevoTenantService();

        assertThatThrownBy(() -> tenantService.obtenerPorId(999999L))
                .isInstanceOf(DomainExceptions.TenantNoEncontradoException.class);
    }

    /**
     * Este es el contrato que documenta el javadoc de TenantService: el
     * camino administrativo SI puede leer un tenant suspendido (para poder
     * gestionarlo/reactivarlo), a diferencia de obtenerPorId. Si esto
     * alguna vez lanzara TenantSuspendidoException tambien, quedaria sin
     * forma de administrar un tenant ya suspendido.
     */
    @Test
    void obtenerParaAdministracionDevuelveElTenantAunSuspendido() {
        TenantService tenantService = nuevoTenantService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Suspendido Admin", Tenant.ESTADO_SUSPENDIDO));

        Tenant obtenido = tenantService.obtenerParaAdministracion(tenant.getId());

        assertThat(obtenido.getId()).isEqualTo(tenant.getId());
        assertThat(obtenido.estaSuspendido()).isTrue();
    }

    @Test
    void obtenerParaAdministracionConIdInexistenteLanzaTenantNoEncontrado() {
        TenantService tenantService = nuevoTenantService();

        assertThatThrownBy(() -> tenantService.obtenerParaAdministracion(999999L))
                .isInstanceOf(DomainExceptions.TenantNoEncontradoException.class);
    }

    @Test
    void actualizarBrandingConValoresValidosLosPersiste() {
        TenantService tenantService = nuevoTenantService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Branding", Tenant.ESTADO_ACTIVO));

        tenantService.actualizarBranding(tenant.getId(), "https://cdn.example.com/logo.png", "#FF00AA", "#00FF11");

        Tenant actualizado = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(actualizado.getLogoUrl()).isEqualTo("https://cdn.example.com/logo.png");
        assertThat(actualizado.getColorPrimario()).isEqualTo("#FF00AA");
        assertThat(actualizado.getColorSecundario()).isEqualTo("#00FF11");
    }

    /**
     * Mismo contrato que obtenerParaAdministracion: actualizarBranding usa
     * ese metodo internamente (no obtenerPorId), a proposito -- un
     * SUPER_ADMIN debe poder seguir administrando el branding de un tenant
     * suspendido.
     */
    @Test
    void actualizarBrandingFuncionaAunConElTenantSuspendido() {
        TenantService tenantService = nuevoTenantService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Suspendido Branding", Tenant.ESTADO_SUSPENDIDO));

        tenantService.actualizarBranding(tenant.getId(), null, "#123456", null);

        Tenant actualizado = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(actualizado.getColorPrimario()).isEqualTo("#123456");
    }

    @Test
    void actualizarBrandingAceptaColoresNulos() {
        TenantService tenantService = nuevoTenantService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Sin Colores", Tenant.ESTADO_ACTIVO));

        tenantService.actualizarBranding(tenant.getId(), null, null, null);

        Tenant actualizado = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(actualizado.getColorPrimario()).isNull();
        assertThat(actualizado.getColorSecundario()).isNull();
    }

    @Test
    void actualizarBrandingConColorPrimarioInvalidoLanzaExcepcionYNoModificaNada() {
        TenantService tenantService = nuevoTenantService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Color Invalido", Tenant.ESTADO_ACTIVO));

        assertThatThrownBy(() -> tenantService.actualizarBranding(tenant.getId(), null, "azul", "#00FF11"))
                .isInstanceOf(DomainExceptions.ColorHexInvalidoException.class);

        Tenant sinCambios = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(sinCambios.getColorPrimario()).isNull();
        assertThat(sinCambios.getColorSecundario()).isNull();
    }

    @Test
    void actualizarBrandingConColorSecundarioInvalidoTambienLanzaExcepcion() {
        TenantService tenantService = nuevoTenantService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Color Secundario Invalido", Tenant.ESTADO_ACTIVO));

        assertThatThrownBy(() -> tenantService.actualizarBranding(tenant.getId(), null, "#FFFFFF", "#ZZZZZZ"))
                .isInstanceOf(DomainExceptions.ColorHexInvalidoException.class);
    }

    /**
     * El formato corto de 3 digitos (#FFF) es valido en CSS pero el patron
     * de TenantService (^#[0-9A-Fa-f]{6}$) exige exactamente 6 -- este test
     * fija ese comportamiento actual a proposito, no lo que "deberia" ser:
     * si algun dia se decide aceptar el formato corto, este test tiene que
     * cambiar explicitamente, no romperse por sorpresa.
     */
    @Test
    void elFormatoCortoDeTresDigitosNoEsValidoHoy() {
        TenantService tenantService = nuevoTenantService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Formato Corto", Tenant.ESTADO_ACTIVO));

        assertThatThrownBy(() -> tenantService.actualizarBranding(tenant.getId(), null, "#FFF", null))
                .isInstanceOf(DomainExceptions.ColorHexInvalidoException.class);
    }

    @Test
    void actualizarBrandingConTenantInexistenteLanzaTenantNoEncontrado() {
        TenantService tenantService = nuevoTenantService();

        assertThatThrownBy(() -> tenantService.actualizarBranding(999999L, null, "#FFFFFF", null))
                .isInstanceOf(DomainExceptions.TenantNoEncontradoException.class);
    }

    @Test
    void crearTenantConNombreNuevoLoPersisteConEstadoActivo() {
        TenantService tenantService = nuevoTenantService();

        Tenant creado = tenantService.crearTenant("Restaurante Nuevo", "STARTER", "COP", "America/Bogota");

        assertThat(creado.getId()).isNotNull();
        assertThat(creado.getEstado()).isEqualTo(Tenant.ESTADO_ACTIVO);
        assertThat(tenantRepository.findByNombre("Restaurante Nuevo")).isPresent();
    }

    @Test
    void crearTenantConNombreYaExistenteLanzaTenantYaExisteException() {
        TenantService tenantService = nuevoTenantService();
        tenantRepository.save(nuevoTenant("Restaurante Repetido", Tenant.ESTADO_ACTIVO));

        assertThatThrownBy(() -> tenantService.crearTenant("Restaurante Repetido", "STARTER", "COP", "America/Bogota"))
                .isInstanceOf(DomainExceptions.TenantYaExisteException.class);
    }

    /**
     * Esto no ejercita crearTenant -- ejercita que la restriccion UNIQUE de
     * la columna nombre (Tenant.java, @Column(unique=true)) existe de
     * verdad en el esquema, no solo en el comentario de crearTenant. Sin
     * este test, un cambio que borrara "unique = true" por accidente
     * dejaria el catch de DataIntegrityViolationException en crearTenant
     * como codigo muerto sin que ningun test lo notara.
     */
    @Test
    void laRestriccionUnicaDeNombreExisteRealmenteEnElEsquema() {
        tenantRepository.save(nuevoTenant("Restaurante Unico", Tenant.ESTADO_ACTIVO));

        assertThatThrownBy(() -> {
            tenantRepository.save(nuevoTenant("Restaurante Unico", Tenant.ESTADO_ACTIVO));
            tenantRepository.flush();
        }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void suspenderTenantMarcaElEstadoComoSuspendido() {
        TenantService tenantService = nuevoTenantService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante A Suspender", Tenant.ESTADO_ACTIVO));

        tenantService.suspenderTenant(tenant.getId());

        Tenant actualizado = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(actualizado.estaSuspendido()).isTrue();
    }

    @Test
    void suspenderUnTenantYaSuspendidoEsUnNoOpExitoso() {
        TenantService tenantService = nuevoTenantService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Doble Suspension", Tenant.ESTADO_SUSPENDIDO));

        assertThatCode(() -> tenantService.suspenderTenant(tenant.getId())).doesNotThrowAnyException();

        Tenant actualizado = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(actualizado.estaSuspendido()).isTrue();
    }

    @Test
    void reactivarTenantMarcaElEstadoComoActivo() {
        TenantService tenantService = nuevoTenantService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante A Reactivar", Tenant.ESTADO_SUSPENDIDO));

        tenantService.reactivarTenant(tenant.getId());

        Tenant actualizado = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(actualizado.estaSuspendido()).isFalse();
    }

    @Test
    void reactivarUnTenantYaActivoEsUnNoOpExitoso() {
        TenantService tenantService = nuevoTenantService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Doble Reactivacion", Tenant.ESTADO_ACTIVO));

        assertThatCode(() -> tenantService.reactivarTenant(tenant.getId())).doesNotThrowAnyException();

        Tenant actualizado = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(actualizado.estaSuspendido()).isFalse();
    }

    @Test
    void suspenderTenantConIdInexistenteLanzaTenantNoEncontrado() {
        TenantService tenantService = nuevoTenantService();

        assertThatThrownBy(() -> tenantService.suspenderTenant(999999L))
                .isInstanceOf(DomainExceptions.TenantNoEncontradoException.class);
    }

    @Test
    void listarTenantsDevuelveTodosOrdenadosPorNombreIncluidosLosSuspendidos() {
        TenantService tenantService = nuevoTenantService();
        tenantRepository.save(nuevoTenant("Zeta Restaurante", Tenant.ESTADO_ACTIVO));
        tenantRepository.save(nuevoTenant("Alfa Restaurante", Tenant.ESTADO_SUSPENDIDO));

        java.util.List<Tenant> tenants = tenantService.listarTenants();

        assertThat(tenants).extracting(Tenant::getNombre).containsExactly("Alfa Restaurante", "Zeta Restaurante");
        assertThat(tenants).anyMatch(Tenant::estaSuspendido);
    }

    @Test
    void listarTenantsSinTenantsCreadosDevuelveListaVacia() {
        TenantService tenantService = nuevoTenantService();

        assertThat(tenantService.listarTenants()).isEmpty();
    }

    private TenantService nuevoTenantService() {
        return new TenantService(tenantRepository);
    }

    private Tenant nuevoTenant(String nombre, String estado) {
        Tenant tenant = new Tenant();
        tenant.setNombre(nombre);
        tenant.setPlan("STARTER");
        tenant.setMoneda("COP");
        tenant.setZonaHoraria("America/Bogota");
        tenant.setEstado(estado);
        return tenant;
    }
}
