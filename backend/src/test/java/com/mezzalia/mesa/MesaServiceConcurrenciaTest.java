package com.mezzalia.mesa;

import com.mezzalia.mesa.excepcion.MesaExceptions;
import com.mezzalia.mesa.servicio.MesaService;
import com.mezzalia.sucursal.Sucursal;
import com.mezzalia.sucursal.SucursalRepository;
import com.mezzalia.sucursal.servicio.SucursalService;
import com.mezzalia.tenant.Tenant;
import com.mezzalia.tenant.TenantContext;
import com.mezzalia.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
 * RF-09, la mitad que MesaServiceTest NO puede probar: la carrera real
 * entre dos altas casi simultaneas del mismo codigo de mesa para el mismo
 * tenant. Ver el comentario largo en MesaService.crearMesa -- lo que cierra
 * esa carrera es la restriccion UNIQUE(tenant_id, codigo_unico), no el
 * chequeo de aplicacion "leer antes de escribir" (que es todo lo que un
 * test de un solo hilo, como los de MesaServiceTest, puede ejercer).
 *
 * Mismo patron exacto que PedidoServiceConcurrenciaTest, con el mismo
 * motivo: la restriccion esta declarada tambien a nivel de entidad JPA
 * (uniqueConstraints en Mesa.java), a proposito, porque el perfil h2 de
 * desarrollo local (y este test, que corre contra ese mismo H2 en memoria
 * via application.yml de test) no ejecuta Flyway -- genera el esquema
 * desde las entidades. Este test SI ejerce el indice unico real que existe
 * en MySQL en produccion, no una simulacion.
 *
 * Lo que este test NO cubre, y no puede cubrir desde aca: la sintaxis
 * concreta de V14__mesas_unicidad_codigo_activo.sql (ALTER TABLE/UPDATE
 * contra MySQL real) -- ninguna suite de este proyecto ejecuta esa
 * migracion (ver el comentario en el propio archivo .sql). Lo que este
 * test SI prueba es el mecanismo (el segundo insert con el mismo
 * tenant_id+codigo_unico falla, nunca hay dos filas activas), que es
 * comportamiento SQL estandar y por eso portable entre H2 y MySQL.
 *
 * RF-10: @Import agrega SucursalService ademas de MesaService -- desde que
 * crearMesa exige un sucursalId valido, MesaService depende de
 * SucursalService (ver su constructor), asi que este slice @DataJpaTest
 * necesita ese bean tambien para poder cablear MesaService automaticamente.
 */
@DataJpaTest
@Import({MesaService.class, SucursalService.class})
class MesaServiceConcurrenciaTest {

    @Autowired
    private MesaService mesaService;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private MesaRepository mesaRepository;
    @Autowired
    private SucursalRepository sucursalRepository;

    @AfterEach
    void limpiarContexto() {
        TenantContext.limpiar();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void dosAltasSimultaneasConElMismoCodigoNuncaCreanDosMesasActivas() throws Exception {
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Concurrencia Mesas"));
        Long tenantId = tenant.getId();
        Long sucursalId = nuevaSucursal(tenantId).getId();
        String codigoCompartido = "M-" + UUID.randomUUID();

        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch salida = new CountDownLatch(1);

        Callable<Resultado> tarea = () -> {
            TenantContext.establecerTenantId(tenantId);
            try {
                listos.countDown();
                salida.await(5, TimeUnit.SECONDS);
                Mesa mesa = mesaService.crearMesa(codigoCompartido, sucursalId);
                return Resultado.exito(mesa.getId());
            } catch (MesaExceptions.CodigoMesaDuplicadoException ex) {
                return Resultado.duplicado();
            } finally {
                TenantContext.limpiar();
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

            // La propiedad que este fix promete: nunca dos mesas activas con
            // el mismo codigo para el mismo tenant. Exactamente uno de los
            // dos hilos gana la carrera; el otro recibe
            // CodigoMesaDuplicadoException (409) -- el mismo 409 que ya
            // devolvia el chequeo de aplicacion para el caso comun, nunca un
            // 500 ni una segunda mesa.
            assertThat(exitosos).as("exactamente un hilo debe crear la mesa").isEqualTo(1);
            assertThat(duplicados).as("el otro hilo debe recibir CodigoMesaDuplicadoException, no un error generico ni una segunda mesa").isEqualTo(1);

            List<Mesa> mesasConEseCodigo = mesaRepository.findAll().stream()
                    .filter(m -> tenantId.equals(m.getTenantId()) && codigoCompartido.equals(m.getCodigo()))
                    .toList();
            assertThat(mesasConEseCodigo).as("en base de datos solo debe existir una fila con este codigo, no dos").hasSize(1);

            Long idGanador = resultados.stream().filter(Resultado::esExito).findFirst().orElseThrow().mesaId();
            assertThat(mesasConEseCodigo.get(0).getId()).isEqualTo(idGanador);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * No es solo el chequeo de aplicacion (ya cubierto por
     * MesaServiceTest.unCodigoQuedaDisponibleDeNuevoUnaVezQueLaMesaOriginalSeDesactiva)
     * -- esto confirma que `codigoUnico` de verdad queda en null en la fila
     * persistida al desactivar, que es lo que de verdad libera el codigo
     * frente a la restriccion UNIQUE nueva. Si alguien borrara la linea
     * `mesa.setCodigoUnico(null)` de MesaService.desactivarMesa, esta
     * restriccion bloquearia para siempre la reutilizacion legitima de un
     * codigo -- este test es el que lo detectaria.
     */
    @Test
    void desactivarMesaLimpiaCodigoUnicoEnBaseDeDatos() {
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Codigo Unico"));
        Sucursal sucursal = nuevaSucursal(tenant.getId());
        TenantContext.establecerTenantId(tenant.getId());
        Mesa mesa = mesaService.crearMesa("M1", sucursal.getId());
        assertThat(mesa.getCodigoUnico()).isEqualTo("M1");

        mesaService.desactivarMesa(mesa.getId());

        Mesa enBaseDeDatos = mesaRepository.findById(mesa.getId()).orElseThrow();
        assertThat(enBaseDeDatos.isActiva()).isFalse();
        assertThat(enBaseDeDatos.getCodigoUnico()).isNull();
        assertThat(enBaseDeDatos.getCodigo()).isEqualTo("M1");
    }

    private record Resultado(boolean esExito, Long mesaId) {
        static Resultado exito(Long mesaId) {
            return new Resultado(true, mesaId);
        }

        static Resultado duplicado() {
            return new Resultado(false, null);
        }
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
