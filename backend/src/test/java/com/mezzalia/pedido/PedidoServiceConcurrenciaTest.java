package com.mezzalia.pedido;

import com.mezzalia.menu.CategoriaMenu;
import com.mezzalia.menu.CategoriaMenuRepository;
import com.mezzalia.menu.HistorialPrecioItemRepository;
import com.mezzalia.menu.ItemMenu;
import com.mezzalia.menu.ItemMenuRepository;
import com.mezzalia.menu.servicio.AlmacenamientoImagenService;
import com.mezzalia.menu.servicio.AlmacenamientoModelo3dService;
import com.mezzalia.menu.servicio.MenuService;
import com.mezzalia.mesa.Mesa;
import com.mezzalia.mesa.MesaRepository;
import com.mezzalia.mesa.servicio.MesaService;
import com.mezzalia.pedido.dto.ItemSolicitadoRequest;
import com.mezzalia.pedido.excepcion.PedidoExceptions;
import com.mezzalia.pedido.servicio.PedidoService;
import com.mezzalia.sucursal.SucursalContext;
import com.mezzalia.sucursal.servicio.SucursalService;
import com.mezzalia.tenant.Tenant;
import com.mezzalia.tenant.TenantContext;
import com.mezzalia.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF-04, la mitad que PedidoServiceTest NO puede probar: la carrera real
 * entre dos requests casi simultaneos con la MISMA clave de idempotencia.
 * Ver el comentario largo en PedidoService.crearPedido -- lo que cierra
 * esa carrera es la restriccion UNIQUE(tenant_id, clave_idempotencia), no
 * el chequeo de aplicacion "leer antes de escribir", y ese chequeo por si
 * solo (que es todo lo que un test de un solo hilo puede ejercer) nunca
 * habria detectado una regresion que rompiera la restriccion real.
 *
 * Por que esto SI prueba el mecanismo real y no una aproximacion: la
 * restriccion UNIQUE esta declarada tambien a nivel de entidad JPA
 * (@Table(uniqueConstraints=...) en Pedido.java), a proposito, precisamente
 * porque el perfil h2 de desarrollo local no corre Flyway y genera el
 * esquema desde las entidades -- ver el comentario en Pedido.java. Este
 * test corre contra ese mismo H2 en memoria (application.yml de test), asi
 * que ejerce el mismo indice unico que existe en MySQL en produccion, no
 * una simulacion. Lo que SI queda fuera (y no se resuelve aca): diferencias
 * finas de bloqueo/espera entre motores bajo contencion muy alta -- eso
 * necesitaria Testcontainers+MySQL real, deliberadamente NO usado aca
 * porque el mecanismo que importa (el segundo insert con la misma clave
 * falla, nunca hay dos filas) es comportamiento SQL estandar, no algo
 * especifico de un motor.
 *
 * Setup distinto de PedidoServiceTest a proposito:
 *  - PedidoService/MenuService son los beans REALES de Spring
 *    (@Import + @Autowired), no `new PedidoService(...)`: hace falta el
 *    proxy @Transactional real para que cada hilo abra su PROPIA
 *    transaccion/conexion -- un objeto instanciado a mano no tiene proxy,
 *    su @Transactional queda inerte (ver comentario de MenuServiceTest).
 *  - @Transactional(NOT_SUPPORTED) en el metodo de test desactiva el
 *    wrapping transaccional por defecto de @DataJpaTest para ESTE test: si
 *    el fixture (tenant,
 *    categoria, item, mesa) quedara sin commitear dentro de la transaccion
 *    del metodo de test, los hilos de fondo (con su propia
 *    transaccion/conexion) no lo verian todavia -- @Transactional usa
 *    ThreadLocal para propagar el contexto transaccional, no cruza hilos.
 *  - TenantContext tambien es ThreadLocal (ver TenantContext.java): hay que
 *    establecerlo DENTRO de cada hilo de fondo, no alcanza con haberlo
 *    puesto en el hilo principal antes de lanzarlos.
 *  - 2026-09-11: mismo razonamiento aplica ahora a SucursalContext -- tambien
 *    es ThreadLocal, y MenuService.obtenerItem (llamado desde
 *    PedidoService.crearPedido via obtenerItemDisponibleParaPedido) exige
 *    que coincida con la sucursal del item. Se establece en el hilo
 *    principal para crear la categoria/item, y de nuevo DENTRO de cada hilo
 *    de fondo antes de crearPedido -- exactamente el mismo patron que
 *    TenantContext aca abajo.
 */
@DataJpaTest
@Import({MenuService.class, PedidoService.class, MesaService.class, SucursalService.class})
class PedidoServiceConcurrenciaTest {

    @Autowired
    private PedidoService pedidoService;
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
    private MenuService menuService;

    @MockBean
    private AlmacenamientoImagenService almacenamientoImagenService; // no se usa en este flujo, solo satisface el constructor de MenuService

    @MockBean
    private AlmacenamientoModelo3dService almacenamientoModelo3dService; // idem, ningun test de concurrencia de pedidos sube modelos 3D

    @AfterEach
    void limpiarContexto() {
        TenantContext.limpiar();
        SucursalContext.limpiar();
        SecurityContextHolder.clearContext();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void dosRequestsSimultaneosConLaMismaClaveDeIdempotenciaNuncaCreanDosPedidos() throws Exception {
        autenticarComo("admin.test");
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Concurrencia"));
        TenantContext.establecerTenantId(tenant.getId());
        // Mismo placeholder que nuevaMesa() usa para Mesa.sucursalId (1L):
        // MenuService.sucursalActual() nunca acepta null (ver su javadoc), y
        // el item creado aca debe coincidir con la sucursal de la mesa.
        SucursalContext.establecerSucursalId(1L);
        CategoriaMenu categoria = menuService.crearCategoria("Categoria", 0);
        ItemMenu item = menuService.crearItem(categoria.getId(), "Item", null, new BigDecimal("10000"), null);
        Mesa mesa = mesaRepository.save(nuevaMesa(tenant.getId(), "M1"));
        TenantContext.limpiar(); // el hilo principal no vuelve a llamar a crearPedido: cada hilo de fondo pone la suya
        SucursalContext.limpiar();

        String claveCompartida = UUID.randomUUID().toString();
        List<ItemSolicitadoRequest> items = List.of(new ItemSolicitadoRequest(item.getId(), 1));

        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch salida = new CountDownLatch(1);

        Callable<Resultado> tarea = () -> {
            autenticarComo("admin.test");
            TenantContext.establecerTenantId(tenant.getId());
            SucursalContext.establecerSucursalId(1L);
            try {
                listos.countDown();
                salida.await(5, TimeUnit.SECONDS);
                Pedido pedido = pedidoService.crearPedido(mesa.getId(), claveCompartida, items, null, null);
                return Resultado.exito(pedido.getId());
            } catch (PedidoExceptions.PedidoDuplicadoException ex) {
                return Resultado.duplicado();
            } finally {
                TenantContext.limpiar();
                SucursalContext.limpiar();
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Resultado> f1 = executor.submit(tarea);
            Future<Resultado> f2 = executor.submit(tarea);

            assertThat(listos.await(5, TimeUnit.SECONDS)).as("ambos hilos deberian llegar listos a tiempo").isTrue();
            salida.countDown(); // suelta a los dos casi al mismo instante -- aca es donde ocurre la carrera real

            Resultado r1 = f1.get(10, TimeUnit.SECONDS);
            Resultado r2 = f2.get(10, TimeUnit.SECONDS);

            List<Resultado> resultados = List.of(r1, r2);
            long exitosos = resultados.stream().filter(Resultado::esExito).count();
            long duplicados = resultados.stream().filter(r -> !r.esExito()).count();

            // La propiedad que RF-04 promete: nunca dos pedidos. Exactamente
            // uno de los dos hilos gana la carrera; el otro recibe
            // PedidoDuplicadoException (409) -- ver el comentario largo en
            // PedidoService.crearPedido para por que esto es el trade-off
            // aceptado (el perdedor reintenta, no se "autosana" en su
            // primera respuesta).
            assertThat(exitosos).as("exactamente un hilo debe crear el pedido").isEqualTo(1);
            assertThat(duplicados).as("el otro hilo debe recibir PedidoDuplicadoException, no un error generico ni un segundo pedido").isEqualTo(1);

            // Y en base de datos, con TenantContext ya limpio en el hilo
            // principal, confirmar que de verdad solo existe una fila: si
            // hubiera dos, este finder (Optional, no List) ya habria
            // lanzado IncorrectResultSizeDataAccessException.
            Pedido enBaseDeDatos = pedidoRepository.findByTenantIdAndClaveIdempotencia(tenant.getId(), claveCompartida)
                    .orElseThrow();
            Long idGanador = resultados.stream().filter(Resultado::esExito).findFirst().orElseThrow().pedidoId();
            assertThat(enBaseDeDatos.getId()).isEqualTo(idGanador);
        } finally {
            executor.shutdownNow();
        }
    }

    private record Resultado(boolean esExito, Long pedidoId) {
        static Resultado exito(Long pedidoId) {
            return new Resultado(true, pedidoId);
        }

        static Resultado duplicado() {
            return new Resultado(false, null);
        }
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
        // la restriccion NOT NULL sin crear una Sucursal real de mas. Debe
        // coincidir con el SucursalContext establecido arriba.
        mesa.setSucursalId(1L);
        mesa.setCodigo(codigo);
        return mesa;
    }
}
