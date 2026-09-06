package com.mezzalia.pedido;

import com.mezzalia.mesa.Mesa;
import com.mezzalia.mesa.MesaRepository;
import com.mezzalia.pedido.excepcion.PedidoExceptions;
import com.mezzalia.pedido.servicio.ComensalService;
import com.mezzalia.tenant.Tenant;
import com.mezzalia.tenant.TenantContext;
import com.mezzalia.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Habeas Data (RF-13, documentacion-tecnica.md §10): cobertura del
 * mecanismo de borrado real -- no ejercita el texto legal ni el flujo de
 * consentimiento (§15.2, fuera de alcance), solo que ComensalService borra
 * la fila de verdad y no deja pedidos huerfanos apuntando a un comensal
 * que ya no existe. Mismo patron de @DataJpaTest + servicio instanciado a
 * mano que el resto del modulo pedido.
 */
@DataJpaTest
class ComensalServiceTest {

    @Autowired
    private TestEntityManager testEntityManager;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private MesaRepository mesaRepository;
    @Autowired
    private PedidoRepository pedidoRepository;
    @Autowired
    private ComensalRepository comensalRepository;

    @AfterEach
    void limpiarContexto() {
        TenantContext.limpiar();
    }

    @Test
    void eliminarDatosComensalBorraLaFilaYDesvinculaSusPedidos() {
        ComensalService comensalService = nuevoComensalService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Borrado"));
        TenantContext.establecerTenantId(tenant.getId());
        Mesa mesa = mesaRepository.save(nuevaMesa(tenant.getId(), "M1"));
        Comensal comensal = comensalRepository.save(nuevoComensal(tenant.getId(), "Juan", "3001234567"));
        Pedido pedido = pedidoRepository.save(nuevoPedido(tenant.getId(), mesa.getId(), comensal.getId()));
        testEntityManager.flush();
        testEntityManager.clear();

        comensalService.eliminarDatosComensal(comensal.getId());
        testEntityManager.flush();
        testEntityManager.clear();

        assertThat(comensalRepository.findById(comensal.getId())).isEmpty();
        // El pedido en si SOBREVIVE (es informacion operativa/de facturacion
        // del restaurante) -- lo unico que se borra es el vinculo al dato
        // personal, ver el javadoc de ComensalService.eliminarDatosComensal.
        Pedido recargado = pedidoRepository.findById(pedido.getId()).orElseThrow();
        assertThat(recargado.getComensalId()).isNull();
    }

    @Test
    void eliminarDatosDeUnComensalSinPedidosVinculadosFunciona() {
        ComensalService comensalService = nuevoComensalService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Sin Pedidos"));
        TenantContext.establecerTenantId(tenant.getId());
        Comensal comensal = comensalRepository.save(nuevoComensal(tenant.getId(), "Sin Pedidos", "3007654321"));
        testEntityManager.flush();

        comensalService.eliminarDatosComensal(comensal.getId());

        assertThat(comensalRepository.findById(comensal.getId())).isEmpty();
    }

    /**
     * Mismo criterio que obtenerPedido/obtenerMesa/obtenerItem en el resto
     * del sistema: un id de otro tenant es "no encontrado", nunca algo que
     * confirme que el recurso existe en otro tenant -- y sobre todo, no se
     * borra nada.
     */
    @Test
    void eliminarDatosDeUnComensalDeOtroTenantLanzaNoEncontradoYNoBorraNada() {
        ComensalService comensalService = nuevoComensalService();
        Tenant tenantA = tenantRepository.save(nuevoTenant("Restaurante A"));
        Tenant tenantB = tenantRepository.save(nuevoTenant("Restaurante B"));

        TenantContext.establecerTenantId(tenantA.getId());
        Comensal comensalDeA = comensalRepository.save(nuevoComensal(tenantA.getId(), "Ana", "3009999999"));
        testEntityManager.flush();

        TenantContext.establecerTenantId(tenantB.getId());

        assertThatThrownBy(() -> comensalService.eliminarDatosComensal(comensalDeA.getId()))
                .isInstanceOf(PedidoExceptions.ComensalNoEncontradoException.class);

        assertThat(comensalRepository.findById(comensalDeA.getId())).isPresent();
    }

    @Test
    void eliminarDatosConIdInexistenteLanzaComensalNoEncontrado() {
        ComensalService comensalService = nuevoComensalService();
        Tenant tenant = tenantRepository.save(nuevoTenant("Restaurante Id Inexistente"));
        TenantContext.establecerTenantId(tenant.getId());

        assertThatThrownBy(() -> comensalService.eliminarDatosComensal(999999L))
                .isInstanceOf(PedidoExceptions.ComensalNoEncontradoException.class);
    }

    /** Mismo caso que el resto de servicios del modulo: SUPER_ADMIN no
     * tiene tenant_id propio, esta operacion la exige. */
    @Test
    void unSuperAdminSinTenantEnContextoNoPuedeEliminarDatosDeComensal() {
        ComensalService comensalService = nuevoComensalService();

        assertThatThrownBy(() -> comensalService.eliminarDatosComensal(1L))
                .isInstanceOf(PedidoExceptions.SinTenantEnContextoException.class);
    }

    private ComensalService nuevoComensalService() {
        return new ComensalService(comensalRepository, pedidoRepository);
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
        mesa.setCodigo(codigo);
        mesa.setCodigoUnico(codigo);
        mesa.setActiva(true);
        // RF-10 (feature/sucursales-multi-tenant, PR #16): Mesa.sucursalId
        // ahora es NOT NULL a nivel de columna, pero sin FK a nivel de JPA
        // (ver Mesa.java) -- este test no ejerce nada de Sucursal, un valor
        // fijo alcanza para satisfacer la restriccion NOT NULL sin crear
        // una Sucursal real de mas. Mismo criterio que
        // PedidoServiceTest/PedidoServiceConcurrenciaTest.nuevaMesa().
        mesa.setSucursalId(1L);
        return mesa;
    }

    private Comensal nuevoComensal(Long tenantId, String nombre, String telefono) {
        Comensal comensal = new Comensal();
        comensal.setTenantId(tenantId);
        comensal.setNombre(nombre);
        comensal.setTelefono(telefono);
        return comensal;
    }

    private Pedido nuevoPedido(Long tenantId, Long mesaId, Long comensalId) {
        Pedido pedido = new Pedido();
        pedido.setTenantId(tenantId);
        pedido.setMesaId(mesaId);
        pedido.setComensalId(comensalId);
        pedido.setEstado(EstadoPedido.RECIBIDO);
        pedido.setClaveIdempotencia(UUID.randomUUID().toString());
        pedido.setTotal(new BigDecimal("10000"));
        return pedido;
    }
}
