package com.mezzalia.mesa;

import com.mezzalia.mesa.excepcion.MesaExceptions;
import com.mezzalia.mesa.servicio.MesaService;
import com.mezzalia.sucursal.Sucursal;
import com.mezzalia.sucursal.SucursalContext;
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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Primera cobertura de MesaService (cero tests existian antes de este
 * archivo). Mismo patron de PedidoServiceTest/MenuServiceTest: @DataJpaTest,
 * servicio instanciado a mano (@DataJpaTest no cablea beans @Service).
 *
 * Foco: los tres invariantes de negocio explicitos en los comentarios de
 * MesaService/SesionMesa (RF-08/parrafo 9) que todavia no tenian una prueba
 * que los ejerciera de verdad:
 *  1. Regenerar el QR de una mesa invalida cualquier sesion previa vigente
 *     (es el mecanismo real de "revocar" un QR fisico perdido/fotografiado).
 *  2. El aislamiento de tenant en obtenerMesa() es una defensa EXPLICITA,
 *     independiente del filtro de Hibernate (@Filter tenantFilter) -- este
 *     test no activa ese filtro para nada, y aun asi una mesa de otro
 *     tenant debe ser invisible.
 *  3. resolverSesionValida (camino publico, sin TenantContext todavia) no
 *     debe distinguir "token nunca existio" de "token expirado" -- ambos
 *     casos tiran la misma excepcion con el mismo mensaje generico.
 *
 * RF-10 (ver Mesa.sucursalId/sucursal.Sucursal): crearMesa ahora exige un
 * sucursalId valido del tenant actual -- cada test que crea una mesa
 * primero crea (o reusa) una Sucursal para ese mismo tenant via
 * nuevaSucursal(tenantId).
 */
@DataJpaTest
class MesaServiceTest {

    @Autowired
    private TestEntityManager testEntityManager;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private MesaRepository mesaRepository;
    @Autowired
    private SesionMesaRepository sesionMesaRepository;
    @Autowired
    private SucursalRepository sucursalRepository;

    @AfterEach
    void limpiarContexto() {
        TenantContext.limpiar();
        SucursalContext.limpiar();
    }

    @Test
    void crearMesaSinTenantEnContextoLanzaExcepcion() {
        MesaService mesaService = nuevoMesaService();

        // El sucursalId (999L) no importa: tenantActual() se evalúa ANTES
        // que la validación de sucursal en MesaService.crearMesa -- ver el
        // comentario de ese método.
        assertThatThrownBy(() -> mesaService.crearMesa("M1", 999L))
                .isInstanceOf(MesaExceptions.SinTenantEnContextoException.class);
    }

    @Test
    void listarMesasSinTenantEnContextoLanzaExcepcion() {
        MesaService mesaService = nuevoMesaService();
        // A proposito NO se llama TenantContext.establecerTenantId(...): asi
        // llega un SUPER_ADMIN real (JwtAuthenticationFilter nunca activa
        // TenantContext para ese rol). Sin este guardia, listarMesas() dependia
        // solo del filtro de Hibernate -- que tampoco se activa para
        // SUPER_ADMIN -- y devolvia las mesas de TODOS los tenants.
        assertThatThrownBy(mesaService::listarMesas)
                .isInstanceOf(MesaExceptions.SinTenantEnContextoException.class);
    }

    @Test
    void crearMesaQuedaAsociadaAlTenantYALaSucursalDelContexto() {
        MesaService mesaService = nuevoMesaService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Mesa A"));
        Sucursal sucursal = nuevaSucursal(tenant.getId());
        TenantContext.establecerTenantId(tenant.getId());

        Mesa mesa = mesaService.crearMesa("M1", sucursal.getId());

        assertThat(mesa.getId()).isNotNull();
        assertThat(mesa.getTenantId()).isEqualTo(tenant.getId());
        assertThat(mesa.getSucursalId()).isEqualTo(sucursal.getId());
        assertThat(mesa.isActiva()).isTrue();
    }

    @Test
    void crearMesaConUnaSucursalDeOtroTenantLanzaSucursalNoEncontrada() {
        MesaService mesaService = nuevoMesaService();
        Tenant tenantA = tenantRepository.save(nuevoTenant("Restaurante Sucursal A"));
        Tenant tenantB = tenantRepository.save(nuevoTenant("Restaurante Sucursal B"));
        Sucursal sucursalDeB = nuevaSucursal(tenantB.getId());

        TenantContext.establecerTenantId(tenantA.getId());

        assertThatThrownBy(() -> mesaService.crearMesa("M1", sucursalDeB.getId()))
                .isInstanceOf(com.mezzalia.sucursal.excepcion.SucursalExceptions.SucursalNoEncontradaException.class);
    }

    @Test
    void listarMesasSoloDevuelveActivasOrdenadasPorCodigo() {
        MesaService mesaService = nuevoMesaService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Listado"));
        Sucursal sucursal = nuevaSucursal(tenant.getId());
        TenantContext.establecerTenantId(tenant.getId());

        Mesa m3 = mesaService.crearMesa("M3", sucursal.getId());
        mesaService.crearMesa("M1", sucursal.getId());
        Mesa m2Desactivada = mesaService.crearMesa("M2-desactivada", sucursal.getId());
        mesaService.desactivarMesa(m2Desactivada.getId());
        testEntityManager.flush();

        List<Mesa> activas = mesaService.listarMesas();

        assertThat(activas).extracting(Mesa::getCodigo).containsExactly("M1", "M3");
        assertThat(m3.getCodigo()).isEqualTo("M3"); // sanity: no se toco el objeto original
    }

    @Test
    void desactivarMesaLaExcluyeDeOperacionesFuturasIncluidoRegenerarSesion() {
        MesaService mesaService = nuevoMesaService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Desactivada"));
        Sucursal sucursal = nuevaSucursal(tenant.getId());
        TenantContext.establecerTenantId(tenant.getId());
        Mesa mesa = mesaService.crearMesa("M1", sucursal.getId());
        mesaService.desactivarMesa(mesa.getId());
        testEntityManager.flush();

        // obtenerMesa() filtra por isActiva -- una vez desactivada, ninguna
        // operacion que pase por ahi deberia poder seguir usandola, ni
        // siquiera dentro del mismo tenant.
        assertThatThrownBy(() -> mesaService.generarSesion(mesa.getId()))
                .isInstanceOf(MesaExceptions.MesaNoEncontradaException.class);
        assertThatThrownBy(() -> mesaService.desactivarMesa(mesa.getId()))
                .as("desactivar una mesa ya desactivada deberia comportarse como 'no encontrada', no ser un no-op silencioso")
                .isInstanceOf(MesaExceptions.MesaNoEncontradaException.class);
    }

    @Test
    void unaMesaDeOtroTenantEsInvisibleAunSinFiltroDeHibernateActivado() {
        MesaService mesaService = nuevoMesaService();
        Tenant tenantA = tenantRepository.save(nuevoTenant("Restaurante A"));
        Tenant tenantB = tenantRepository.save(nuevoTenant("Restaurante B"));
        Sucursal sucursalA = nuevaSucursal(tenantA.getId());

        TenantContext.establecerTenantId(tenantA.getId());
        Mesa mesaDeA = mesaService.crearMesa("M1", sucursalA.getId());
        testEntityManager.flush();

        // Este test NO activa el filtro tenantFilter de Hibernate (ver
        // AislamientoTenantTest para esa prueba, a otro nivel) -- lo que se
        // prueba aca es la defensa EXPLICITA de MesaService.obtenerMesa(),
        // que sigue funcionando aunque el filtro este apagado.
        TenantContext.establecerTenantId(tenantB.getId());

        assertThatThrownBy(() -> mesaService.generarSesion(mesaDeA.getId()))
                .as("una mesa de otro tenant debe ser 'no encontrada', nunca un 403 que confirme que existe")
                .isInstanceOf(MesaExceptions.MesaNoEncontradaException.class);
        assertThatThrownBy(() -> mesaService.desactivarMesa(mesaDeA.getId()))
                .isInstanceOf(MesaExceptions.MesaNoEncontradaException.class);
    }

    @Test
    void generarSesionInvalidaCualquierSesionVigentePrevia() {
        MesaService mesaService = nuevoMesaService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante QR"));
        Sucursal sucursal = nuevaSucursal(tenant.getId());
        TenantContext.establecerTenantId(tenant.getId());
        Mesa mesa = mesaService.crearMesa("M1", sucursal.getId());
        testEntityManager.flush();

        SesionMesa sesionVieja = mesaService.generarSesion(mesa.getId());
        testEntityManager.flush();
        SesionMesa sesionNueva = mesaService.generarSesion(mesa.getId());
        testEntityManager.flush();
        testEntityManager.clear();

        // La sesion vieja ya no debe resolver -- es el mecanismo real de
        // "revocar" un QR fotografiado (ver comentario de generarSesion).
        assertThatThrownBy(() -> mesaService.resolverSesionValida(sesionVieja.getToken()))
                .isInstanceOf(MesaExceptions.SesionMesaInvalidaException.class);

        SesionMesa resuelta = mesaService.resolverSesionValida(sesionNueva.getToken());
        assertThat(resuelta.getToken()).isEqualTo(sesionNueva.getToken());
    }

    @Test
    void generarSesionEstableceExpiracionACuatroHoras() {
        MesaService mesaService = nuevoMesaService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Vigencia"));
        Sucursal sucursal = nuevaSucursal(tenant.getId());
        TenantContext.establecerTenantId(tenant.getId());
        Mesa mesa = mesaService.crearMesa("M1", sucursal.getId());

        LocalDateTime antes = LocalDateTime.now();
        SesionMesa sesion = mesaService.generarSesion(mesa.getId());
        LocalDateTime despues = LocalDateTime.now();

        assertThat(sesion.getExpiraEn())
                .isAfterOrEqualTo(antes.plusHours(4))
                .isBeforeOrEqualTo(despues.plusHours(4).plusSeconds(1));
    }

    @Test
    void resolverSesionValidaConTokenInexistenteLanzaLaMismaExcepcionQueUnoExpirado() {
        MesaService mesaService = nuevoMesaService();

        assertThatThrownBy(() -> mesaService.resolverSesionValida(UUID.randomUUID().toString()))
                .isInstanceOf(MesaExceptions.SesionMesaInvalidaException.class)
                .hasMessage(new MesaExceptions.SesionMesaInvalidaException().getMessage());
    }

    @Test
    void unaSesionExpiradaNoEsResueltaAunConTokenCorrecto() {
        MesaService mesaService = nuevoMesaService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Expirada"));
        SesionMesa sesionExpirada = new SesionMesa();
        sesionExpirada.setTenantId(tenant.getId());
        sesionExpirada.setMesaId(999L);
        sesionExpirada.setToken(UUID.randomUUID().toString());
        sesionExpirada.setExpiraEn(LocalDateTime.now().minusMinutes(1));
        sesionMesaRepository.save(sesionExpirada);
        testEntityManager.flush();

        assertThatThrownBy(() -> mesaService.resolverSesionValida(sesionExpirada.getToken()))
                .isInstanceOf(MesaExceptions.SesionMesaInvalidaException.class);
    }

    /**
     * Boundary exacto de findByTokenAndExpiraEnAfter: "After" es estrictamente
     * mayor que, no >=. Una sesion cuyo expiraEn es EXACTAMENTE el instante
     * consultado ya no deberia considerarse vigente -- si esto alguna vez
     * cambiara a un query mal escrito con >=, una sesion expirando "ahora
     * mismo" seguiria siendo aceptada un instante de mas.
     */
    @Test
    void unaSesionEnElLimiteExactoDeSuExpiracionNoSeConsideraVigente() {
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Limite"));
        LocalDateTime limite = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        SesionMesa sesion = new SesionMesa();
        sesion.setTenantId(tenant.getId());
        sesion.setMesaId(999L);
        sesion.setToken(UUID.randomUUID().toString());
        sesion.setExpiraEn(limite);
        sesionMesaRepository.save(sesion);
        testEntityManager.flush();

        assertThat(sesionMesaRepository.findByTokenAndExpiraEnAfter(sesion.getToken(), limite))
                .as("expiraEn == ahora debe considerarse ya expirada, no vigente")
                .isEmpty();
    }

    @Test
    void crearMesaConCodigoYaActivoParaElMismoTenantLanzaExcepcion() {
        MesaService mesaService = nuevoMesaService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Duplicado"));
        Sucursal sucursal = nuevaSucursal(tenant.getId());
        TenantContext.establecerTenantId(tenant.getId());
        mesaService.crearMesa("M1", sucursal.getId());
        testEntityManager.flush();

        assertThatThrownBy(() -> mesaService.crearMesa("M1", sucursal.getId()))
                .isInstanceOf(MesaExceptions.CodigoMesaDuplicadoException.class);
    }

    @Test
    void elMismoCodigoEsValidoEnTenantsDistintos() {
        MesaService mesaService = nuevoMesaService();
        Tenant tenantA = tenantRepository.save(nuevoTenant("Restaurante Codigo A"));
        Tenant tenantB = tenantRepository.save(nuevoTenant("Restaurante Codigo B"));
        Sucursal sucursalA = nuevaSucursal(tenantA.getId());
        Sucursal sucursalB = nuevaSucursal(tenantB.getId());

        TenantContext.establecerTenantId(tenantA.getId());
        mesaService.crearMesa("M1", sucursalA.getId());
        testEntityManager.flush();

        TenantContext.establecerTenantId(tenantB.getId());
        Mesa mesaDeB = mesaService.crearMesa("M1", sucursalB.getId());

        assertThat(mesaDeB.getId()).isNotNull();
    }

    @Test
    void unCodigoQuedaDisponibleDeNuevoUnaVezQueLaMesaOriginalSeDesactiva() {
        MesaService mesaService = nuevoMesaService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Reutiliza Codigo"));
        Sucursal sucursal = nuevaSucursal(tenant.getId());
        TenantContext.establecerTenantId(tenant.getId());
        Mesa original = mesaService.crearMesa("M1", sucursal.getId());
        mesaService.desactivarMesa(original.getId());
        testEntityManager.flush();

        Mesa nueva = mesaService.crearMesa("M1", sucursal.getId());

        assertThat(nueva.getId()).isNotEqualTo(original.getId());
        assertThat(nueva.isActiva()).isTrue();
    }

    /**
     * RF-10 PR4 (docs/propuesta-multisucursal-rf10.md §7 -- el test que esa
     * propuesta pedia explicitamente y todavia no existia): el caso real
     * que motivo agregar sucursalActual() a este servicio. Sin el chequeo
     * de sucursal en obtenerMesa(), un MESERO de la sucursal A podia
     * desactivar o regenerar el QR de una mesa de la sucursal B del MISMO
     * tenant con solo adivinar/conocer su id.
     */
    @Test
    void unMeseroConSucursalSeleccionadaNoPuedeOperarMesasDeOtraSucursalDelMismoTenant() {
        MesaService mesaService = nuevoMesaService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Multisucursal"));
        Sucursal sucursalA = nuevaSucursal(tenant.getId());
        Sucursal sucursalB = nuevaSucursal(tenant.getId());
        TenantContext.establecerTenantId(tenant.getId());
        Mesa mesaDeB = mesaService.crearMesa("M1", sucursalB.getId());
        testEntityManager.flush();

        // Simula un MESERO que ya selecciono la sucursal A (ver
        // JwtAuthenticationFilter -- esto es exactamente lo que ese filtro
        // establece a partir del JWT, reproducido a mano aca porque este
        // test no monta la capa HTTP).
        SucursalContext.marcarRequiereSeleccion(true);
        SucursalContext.establecerSucursalId(sucursalA.getId());

        assertThatThrownBy(() -> mesaService.desactivarMesa(mesaDeB.getId()))
                .as("una mesa de otra sucursal del mismo tenant debe ser 'no encontrada', nunca un 403 que confirme que existe")
                .isInstanceOf(MesaExceptions.MesaNoEncontradaException.class);
        assertThatThrownBy(() -> mesaService.generarSesion(mesaDeB.getId()))
                .isInstanceOf(MesaExceptions.MesaNoEncontradaException.class);
    }

    @Test
    void listarMesasAcotaPorSucursalCuandoUnMeseroYaSeleccionoUna() {
        MesaService mesaService = nuevoMesaService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Listado Multisucursal"));
        Sucursal sucursalA = nuevaSucursal(tenant.getId());
        Sucursal sucursalB = nuevaSucursal(tenant.getId());
        TenantContext.establecerTenantId(tenant.getId());
        mesaService.crearMesa("A1", sucursalA.getId());
        mesaService.crearMesa("B1", sucursalB.getId());
        testEntityManager.flush();

        SucursalContext.marcarRequiereSeleccion(true);
        SucursalContext.establecerSucursalId(sucursalA.getId());

        assertThat(mesaService.listarMesas()).extracting(Mesa::getCodigo).containsExactly("A1");
    }

    @Test
    void unMeseroSinSucursalSeleccionadaNoPuedeListarMesas() {
        MesaService mesaService = nuevoMesaService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Sin Seleccion"));
        TenantContext.establecerTenantId(tenant.getId());

        // requiereSeleccion=true sin sucursalId establecido -- reproduce un
        // MESERO recien logueado (o recien refrescado, ver el javadoc de
        // AuthService.emitirPar) que todavia no llamo a
        // POST /api/admin/sesion/sucursal.
        SucursalContext.marcarRequiereSeleccion(true);

        assertThatThrownBy(mesaService::listarMesas)
                .isInstanceOf(SucursalExceptions.SucursalNoSeleccionadaException.class);
    }

    @Test
    void unAdminRestauranteSinSucursalSeleccionadaSigueViendoTodasLasSucursales() {
        MesaService mesaService = nuevoMesaService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Admin Sin Enfocar"));
        Sucursal sucursalA = nuevaSucursal(tenant.getId());
        Sucursal sucursalB = nuevaSucursal(tenant.getId());
        TenantContext.establecerTenantId(tenant.getId());
        mesaService.crearMesa("A1", sucursalA.getId());
        mesaService.crearMesa("B1", sucursalB.getId());
        testEntityManager.flush();

        // requiereSeleccion queda en su default (false): ADMIN_RESTAURANTE
        // sin enfocar ninguna sucursal -- comportamiento identico al que
        // existia antes de esta rama.
        assertThat(mesaService.listarMesas()).extracting(Mesa::getCodigo)
                .containsExactlyInAnyOrder("A1", "B1");
    }

    private MesaService nuevoMesaService() {
        return new MesaService(mesaRepository, sesionMesaRepository, new SucursalService(sucursalRepository));
    }

    private Sucursal nuevaSucursal(Long tenantId) {
        Sucursal sucursal = new Sucursal();
        sucursal.setTenantId(tenantId);
        sucursal.setNombre("Sucursal Principal");
        sucursal.setActiva(true);
        return sucursalRepository.save(sucursal);
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
