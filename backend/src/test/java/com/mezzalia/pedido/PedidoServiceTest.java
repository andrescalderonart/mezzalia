package com.mezzalia.pedido;

import com.mezzalia.menu.CategoriaMenu;
import com.mezzalia.menu.CategoriaMenuRepository;
import com.mezzalia.menu.HistorialPrecioItemRepository;
import com.mezzalia.menu.ItemMenu;
import com.mezzalia.menu.ItemMenuRepository;
import com.mezzalia.menu.servicio.MenuService;
import com.mezzalia.mesa.Mesa;
import com.mezzalia.mesa.MesaRepository;
import com.mezzalia.pedido.dto.ComensalRequest;
import com.mezzalia.pedido.dto.ItemSolicitadoRequest;
import com.mezzalia.pedido.excepcion.PedidoExceptions;
import com.mezzalia.pedido.servicio.PedidoService;
import com.mezzalia.tenant.Tenant;
import com.mezzalia.tenant.TenantContext;
import com.mezzalia.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de PedidoService: RF-04 (idempotencia, la parte secuencial -- la
 * carrera real de dos hilos concurrentes vive aparte en
 * PedidoServiceConcurrenciaTest, porque necesita un setup distinto), RF-05
 * (precio siempre del servidor), RF-06 (tabla de transiciones de
 * EstadoPedido) y RF-13 (consentimiento del comensal).
 *
 * Mismo patron que MenuServiceTest: @DataJpaTest, PedidoService y
 * MenuService instanciados a mano (new) -- @DataJpaTest no wirea @Service
 * beans, y sus @Transactional quedan como metadata inerte aca, pero el
 * propio @DataJpaTest ya envuelve cada test en una transaccion con rollback
 * al final, asi que la persistencia real igual se prueba. Tampoco activa el
 * filtro de Hibernate a proposito: MenuService.obtenerItem hace su propia
 * defensa explicita contra TenantContext (ver ese comentario), no depende
 * del filtro de sesion para este flujo.
 */
@DataJpaTest
class PedidoServiceTest {

    @Autowired
    private TestEntityManager testEntityManager;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private CategoriaMenuRepository categoriaMenuRepository;
    @Autowired
    private ItemMenuRepository itemMenuRepository;
    @Autowired
    private HistorialPrecioItemRepository historialPrecioItemRepository;
    @Autowired
    private MesaRepository mesaRepository;
    @Autowired
    private PedidoRepository pedidoRepository;
    @Autowired
    private PedidoItemRepository pedidoItemRepository;
    @Autowired
    private PedidoEstadoHistorialRepository historialRepository;
    @Autowired
    private ComensalRepository comensalRepository;

    @AfterEach
    void limpiarContexto() {
        TenantContext.limpiar();
        SecurityContextHolder.clearContext();
    }

    @Test
    void elTotalDelPedidoSeCalculaSiempreDesdeElPrecioVigenteEnElServidor() {
        PedidoService pedidoService = nuevoPedidoService();
        Escenario e = prepararEscenario("Restaurante Total");

        ItemMenu ceviche = crearItem(e.categoria, "Ceviche", "18000");
        ItemMenu limonada = crearItem(e.categoria, "Limonada", "6000");
        testEntityManager.flush();

        Pedido pedido = pedidoService.crearPedido(
                e.mesa.getId(), UUID.randomUUID().toString(),
                List.of(new ItemSolicitadoRequest(ceviche.getId(), 2), new ItemSolicitadoRequest(limonada.getId(), 3)),
                null, null);

        // 2*18000 + 3*6000 = 54000. El request no tiene forma de mandar un
        // precio distinto -- ItemSolicitadoRequest no tiene ese campo (RF-05).
        assertThat(pedido.getTotal()).isEqualByComparingTo("54000");
    }

    @Test
    void laMismaClaveIdempotenciaEnDosLlamadasSecuencialesDevuelveElMismoPedidoSinCrearUnoNuevo() {
        PedidoService pedidoService = nuevoPedidoService();
        Escenario e = prepararEscenario("Restaurante Idempotencia");
        ItemMenu item = crearItem(e.categoria, "Sopa", "12000");
        testEntityManager.flush();

        String clave = UUID.randomUUID().toString();
        List<ItemSolicitadoRequest> items = List.of(new ItemSolicitadoRequest(item.getId(), 1));

        Pedido primero = pedidoService.crearPedido(e.mesa.getId(), clave, items, null, null);
        testEntityManager.flush();
        Pedido segundo = pedidoService.crearPedido(e.mesa.getId(), clave, items, "notas distintas ignoradas", null);

        assertThat(segundo.getId()).isEqualTo(primero.getId());
        assertThat(pedidoRepository.findByTenantIdAndClaveIdempotencia(e.tenant.getId(), clave)).isPresent();
        // Si el chequeo "leer antes de escribir" fallara y se creara una
        // segunda fila, este finder (que devuelve Optional, no List) ya
        // habria lanzado IncorrectResultSizeDataAccessException arriba --
        // llegar aca sin excepcion ya es parte de la prueba.
    }

    @Test
    void unPedidoSinItemsLanzaExcepcionYNoPersisteNada() {
        PedidoService pedidoService = nuevoPedidoService();
        Escenario e = prepararEscenario("Restaurante SinItems");

        assertThatThrownBy(() ->
                pedidoService.crearPedido(e.mesa.getId(), UUID.randomUUID().toString(), List.of(), null, null))
                .isInstanceOf(PedidoExceptions.PedidoSinItemsException.class);

        assertThat(pedidoRepository.findAllByOrderByCreadoEnAsc())
                .filteredOn(p -> p.getTenantId().equals(e.tenant.getId()))
                .isEmpty();
    }

    @Test
    void sinConsentimientoExplicitoNoSePersisteNingunComensalAunqueVenganNombreYTelefono() {
        PedidoService pedidoService = nuevoPedidoService();
        Escenario e = prepararEscenario("Restaurante Habeas Data");
        ItemMenu item = crearItem(e.categoria, "Jugo", "5000");
        testEntityManager.flush();

        ComensalRequest sinConsentimiento = new ComensalRequest("Juan Sebastian", "3130000000", false);

        Pedido pedido = pedidoService.crearPedido(
                e.mesa.getId(), UUID.randomUUID().toString(),
                List.of(new ItemSolicitadoRequest(item.getId(), 1)), null, sinConsentimiento);

        assertThat(pedido.getComensalId()).isNull();
        assertThat(comensalRepository.findAll())
                .filteredOn(c -> c.getTenantId().equals(e.tenant.getId()))
                .isEmpty();
    }

    @Test
    void conConsentimientoExplicitoSiPersisteElComensal() {
        PedidoService pedidoService = nuevoPedidoService();
        Escenario e = prepararEscenario("Restaurante Habeas Data Con Consentimiento");
        ItemMenu item = crearItem(e.categoria, "Jugo", "5000");
        testEntityManager.flush();

        ComensalRequest conConsentimiento = new ComensalRequest("Juan Sebastian", "3130000000", true);

        Pedido pedido = pedidoService.crearPedido(
                e.mesa.getId(), UUID.randomUUID().toString(),
                List.of(new ItemSolicitadoRequest(item.getId(), 1)), null, conConsentimiento);

        assertThat(pedido.getComensalId()).isNotNull();
        assertThat(comensalRepository.findById(pedido.getComensalId())).isPresent();
    }

    @Test
    void masDeVeinteNuevosPedidosEnCincoMinutosParaLaMismaMesaLanzaExcepcionDeLimite() {
        // 20 es el limite documentado en PedidoService.LIMITE_PEDIDOS_POR_VENTANA
        // (privado, hardcodeado aca a proposito -- ver el comentario en
        // tablaCompletaDeTransicionesValidasEInvalidas sobre por que un test
        // no deberia leer la constante privada de la implementacion).
        PedidoService pedidoService = nuevoPedidoService();
        Escenario e = prepararEscenario("Restaurante Limite Volumen");
        ItemMenu item = crearItem(e.categoria, "Agua", "3000");
        testEntityManager.flush();

        for (int i = 0; i < 20; i++) {
            pedidoService.crearPedido(e.mesa.getId(), UUID.randomUUID().toString(),
                    List.of(new ItemSolicitadoRequest(item.getId(), 1)), null, null);
        }
        testEntityManager.flush();

        assertThatThrownBy(() -> pedidoService.crearPedido(e.mesa.getId(), UUID.randomUUID().toString(),
                List.of(new ItemSolicitadoRequest(item.getId(), 1)), null, null))
                .isInstanceOf(PedidoExceptions.DemasiadosPedidosException.class);
    }

    @Test
    void elLimiteDeVolumenEsPorMesaNoGlobal() {
        PedidoService pedidoService = nuevoPedidoService();
        Escenario mesaAgotada = prepararEscenario("Restaurante Limite A");
        ItemMenu itemA = crearItem(mesaAgotada.categoria, "Agua", "3000");
        Escenario otraMesa = prepararEscenario("Restaurante Limite B");
        ItemMenu itemB = crearItem(otraMesa.categoria, "Agua", "3000");
        testEntityManager.flush();

        // prepararEscenario establece TenantContext (ThreadLocal) con el
        // tenant que acaba de crear -- la segunda llamada (otraMesa) pisa el
        // valor dejado por la primera. Sin re-establecerlo aca antes del
        // loop, MenuService.obtenerItem busca itemA bajo el tenant de
        // otraMesa y falla con ItemMenuNoEncontradoException.
        TenantContext.establecerTenantId(mesaAgotada.tenant.getId());
        for (int i = 0; i < 20; i++) {
            pedidoService.crearPedido(mesaAgotada.mesa.getId(), UUID.randomUUID().toString(),
                    List.of(new ItemSolicitadoRequest(itemA.getId(), 1)), null, null);
        }
        testEntityManager.flush();

        // La mesa A ya esta en el limite; la mesa B (otro tenant en este
        // fixture, pero el punto es que es otro mesaId) no deberia verse
        // afectada.
        TenantContext.establecerTenantId(otraMesa.tenant.getId());
        Pedido pedidoDeOtraMesa = pedidoService.crearPedido(otraMesa.mesa.getId(), UUID.randomUUID().toString(),
                List.of(new ItemSolicitadoRequest(itemB.getId(), 1)), null, null);

        assertThat(pedidoDeOtraMesa.getId()).isNotNull();
    }

    @Test
    void cambiarAUnaTransicionInvalidaLanzaExcepcionYNoModificaElEstadoPersistido() {
        PedidoService pedidoService = nuevoPedidoService();
        Escenario e = prepararEscenario("Restaurante Transicion Invalida");
        ItemMenu item = crearItem(e.categoria, "Cafe", "3000");
        testEntityManager.flush();

        Pedido pedido = pedidoService.crearPedido(
                e.mesa.getId(), UUID.randomUUID().toString(),
                List.of(new ItemSolicitadoRequest(item.getId(), 1)), null, null);
        testEntityManager.flush();
        testEntityManager.clear();

        // RECIBIDO -> ENTREGADO se salta EN_PREPARACION y LISTO: invalida.
        assertThatThrownBy(() -> pedidoService.cambiarEstado(pedido.getId(), EstadoPedido.ENTREGADO))
                .isInstanceOf(PedidoExceptions.TransicionEstadoInvalidaException.class);

        Pedido recargado = pedidoRepository.findById(pedido.getId()).orElseThrow();
        assertThat(recargado.getEstado()).isEqualTo(EstadoPedido.RECIBIDO);
        assertThat(historialRepository.findByPedidoIdOrderByCreadoEnAsc(pedido.getId())).hasSize(1); // solo el alta
    }

    /**
     * RF-06 completo: las 25 combinaciones posibles de (origen, destino)
     * sobre los 5 valores de EstadoPedido. La lista de "validas" se
     * hardcodea aca a mano, a proposito, en vez de leerla de
     * PedidoService.TRANSICIONES_VALIDAS (que es privado, ademas) -- un
     * test que compara el mapa contra si mismo nunca puede fallar. Esto
     * compara el comportamiento real contra la especificacion de RF-06,
     * no contra la implementacion.
     */
    @ParameterizedTest(name = "{0} -> {1} deberia ser {2}")
    @MethodSource("todasLasCombinacionesDeEstados")
    void tablaCompletaDeTransicionesValidasEInvalidas(EstadoPedido origen, EstadoPedido destino, boolean esValida) {
        PedidoService pedidoService = nuevoPedidoService();
        Escenario e = prepararEscenario("Restaurante Transiciones " + origen + "-" + destino + "-" + UUID.randomUUID());
        ItemMenu item = crearItem(e.categoria, "Item", "1000");
        testEntityManager.flush();

        Pedido pedido = pedidoService.crearPedido(
                e.mesa.getId(), UUID.randomUUID().toString(),
                List.of(new ItemSolicitadoRequest(item.getId(), 1)), null, null);
        testEntityManager.flush();

        // Llevar el pedido hasta `origen` pasando por las transiciones
        // validas intermedias (todas parten de RECIBIDO al crear).
        for (EstadoPedido paso : caminoValidoHasta(origen)) {
            pedidoService.cambiarEstado(pedido.getId(), paso);
        }
        testEntityManager.flush();
        testEntityManager.clear();

        if (esValida) {
            Pedido resultado = pedidoService.cambiarEstado(pedido.getId(), destino);
            assertThat(resultado.getEstado()).isEqualTo(destino);
        } else {
            assertThatThrownBy(() -> pedidoService.cambiarEstado(pedido.getId(), destino))
                    .isInstanceOf(PedidoExceptions.TransicionEstadoInvalidaException.class);
        }
    }

    static Stream<Arguments> todasLasCombinacionesDeEstados() {
        Map<EstadoPedido, Set<EstadoPedido>> validasSegunRf06 = new EnumMap<>(EstadoPedido.class);
        validasSegunRf06.put(EstadoPedido.RECIBIDO, EnumSet.of(EstadoPedido.EN_PREPARACION, EstadoPedido.CANCELADO));
        validasSegunRf06.put(EstadoPedido.EN_PREPARACION, EnumSet.of(EstadoPedido.LISTO, EstadoPedido.CANCELADO));
        validasSegunRf06.put(EstadoPedido.LISTO, EnumSet.of(EstadoPedido.ENTREGADO, EstadoPedido.CANCELADO));
        validasSegunRf06.put(EstadoPedido.ENTREGADO, EnumSet.noneOf(EstadoPedido.class));
        validasSegunRf06.put(EstadoPedido.CANCELADO, EnumSet.noneOf(EstadoPedido.class));

        return java.util.Arrays.stream(EstadoPedido.values()).flatMap(origen ->
                java.util.Arrays.stream(EstadoPedido.values()).map(destino ->
                        Arguments.of(origen, destino, validasSegunRf06.get(origen).contains(destino))));
    }

    /** Camino valido minimo desde RECIBIDO (estado inicial real de todo
     * pedido creado) hasta `origen`, para poder ejercer la transicion bajo
     * prueba desde ese estado real. */
    private List<EstadoPedido> caminoValidoHasta(EstadoPedido origen) {
        return switch (origen) {
            case RECIBIDO -> List.of();
            case EN_PREPARACION -> List.of(EstadoPedido.EN_PREPARACION);
            case LISTO -> List.of(EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO);
            case ENTREGADO -> List.of(EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO, EstadoPedido.ENTREGADO);
            case CANCELADO -> List.of(EstadoPedido.CANCELADO);
        };
    }

    /**
     * Mismo caso que las pruebas equivalentes de MenuService/MesaService:
     * listarPedidos() dependia solo del filtro de Hibernate para el
     * aislamiento entre tenants -- que nunca se activa para SUPER_ADMIN
     * (TenantContext vacio a proposito) -- y devolvia los pedidos de TODOS
     * los restaurantes en una sola lista. A proposito NO se llama
     * TenantContext.establecerTenantId(...): asi llega un SUPER_ADMIN real.
     */
    @Test
    void unSuperAdminSinTenantEnContextoNoPuedeListarPedidos() {
        PedidoService pedidoService = nuevoPedidoService();

        assertThatThrownBy(() -> pedidoService.listarPedidos(null))
                .isInstanceOf(PedidoExceptions.SinTenantEnContextoException.class);
    }

    private PedidoService nuevoPedidoService() {
        return new PedidoService(pedidoRepository, pedidoItemRepository, historialRepository, comensalRepository,
                nuevoMenuService());
    }

    private MenuService nuevoMenuService() {
        // AlmacenamientoImagenService en null a proposito, igual que en
        // MenuServiceTest: ningun test de esta clase sube imagenes.
        return new MenuService(categoriaMenuRepository, itemMenuRepository, historialPrecioItemRepository, null);
    }

    private Escenario prepararEscenario(String nombreTenant) {
        Tenant tenant = tenantRepository.save(nuevoTenant(nombreTenant));
        autenticarComo("admin.test");
        TenantContext.establecerTenantId(tenant.getId());

        CategoriaMenu categoria = nuevoMenuService().crearCategoria("Categoria", 0);
        Mesa mesa = mesaRepository.save(nuevaMesa(tenant.getId(), "M1"));
        return new Escenario(tenant, categoria, mesa);
    }

    private ItemMenu crearItem(CategoriaMenu categoria, String nombre, String precio) {
        return nuevoMenuService().crearItem(categoria.getId(), nombre, null, new BigDecimal(precio), null);
    }

    private void autenticarComo(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    private Tenant nuevoTenant(String nombre) {
        Tenant tenant = new Tenant();
        tenant.setNombre(nombre);
        tenant.setPlan("STARTER");
        tenant.setMoneda("COP");
        tenant.setZonaHoraria("America/Bogota");
        return tenant;
    }

    private Mesa nuevaMesa(Long tenantId, String codigo) {
        Mesa mesa = new Mesa();
        mesa.setTenantId(tenantId);
        // RF-10: Mesa.sucursalId es NOT NULL desde V16__mesas_sucursal_id.sql,
        // pero sin FK a nivel de JPA (ver Mesa.java) -- estos tests de pedido
        // no ejercen nada de Sucursal, un valor fijo alcanza para satisfacer
        // la restriccion NOT NULL sin crear una Sucursal real de mas.
        mesa.setSucursalId(1L);
        mesa.setCodigo(codigo);
        return mesa;
    }

    private record Escenario(Tenant tenant, CategoriaMenu categoria, Mesa mesa) {}
}
