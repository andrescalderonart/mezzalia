package com.mezzalia.menu.controlador;

import com.mezzalia.mesa.SesionMesa;
import com.mezzalia.mesa.servicio.MesaService;
import com.mezzalia.menu.servicio.MenuService;
import com.mezzalia.pedido.servicio.PedidoService;
import com.mezzalia.tenant.Tenant;
import com.mezzalia.tenant.TenantContext;
import com.mezzalia.tenant.TenantFilterActivator;
import com.mezzalia.tenant.servicio.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regresión de un bug real encontrado el 2026-09-18 probando el QR de una
 * mesa en un dispositivo real: obtenerCarta (y crearPedido, mismo patrón)
 * llamaba mesaService.obtenerSucursalIdDeMesa(...) ANTES de entrar a
 * tenantFilterActivator.ejecutarConTenant(...). En ese momento
 * TenantContext.obtenerTenantId() todavía es null -- este es el camino
 * público, sin JWT, así que JwtAuthenticationFilter nunca lo puebla.
 * MesaService.obtenerSucursalIdDeMesa filtra explícitamente por
 * "tenantId != null && tenantId.equals(m.getTenantId())", así que con
 * TenantContext en null esa condición es SIEMPRE falsa y el método
 * devolvía null sin importar la mesa real. Con sucursal ahora obligatoria
 * en MenuService (sucursalActual() nunca acepta null, RF-10 PR4), esto
 * rompía el 100% de las cartas públicas con SucursalNoSeleccionadaException
 * -- un 500 disfrazado de "selecciona sucursal de trabajo", un mensaje
 * pensado para el panel admin, filtrándose a un comensal anónimo.
 *
 * Nadie lo detectó porque ningún test del repo ejercita este controlador:
 * MenuServiceTest prueba MenuService saltándose el controlador por completo
 * (setea SucursalContext a mano). Este es el primer test que instancia
 * MenuPublicoController; usa Mockito (ya viene con spring-boot-starter-test,
 * pero es el primer uso en este repo) porque las dependencias son clases
 * concretas de servicio, no interfaces, y @DataJpaTest no las wirea.
 *
 * A propósito NO verifica el contenido de negocio de la carta (mockea
 * MenuService por completo) -- verifica exactamente la causa del bug: que
 * TenantContext ya tiene el tenant correcto en el instante exacto en que se
 * resuelve la sucursal de la mesa.
 */
class MenuPublicoControllerTest {

    private final MesaService mesaService = mock(MesaService.class);
    private final MenuService menuService = mock(MenuService.class);
    private final TenantService tenantService = mock(TenantService.class);
    private final PedidoService pedidoService = mock(PedidoService.class);
    private final TenantFilterActivator tenantFilterActivator = new TenantFilterActivatorFalso();

    private final MenuPublicoController controller =
            new MenuPublicoController(mesaService, menuService, tenantService, tenantFilterActivator, pedidoService);

    @AfterEach
    void limpiarContexto() {
        TenantContext.limpiar();
    }

    @Test
    void resuelveLaSucursalDeLaMesaConElTenantContextYaPoblado() {
        Long tenantIdEsperado = 42L;
        Long mesaId = 7L;

        SesionMesa sesion = mock(SesionMesa.class);
        when(sesion.getTenantId()).thenReturn(tenantIdEsperado);
        when(sesion.getMesaId()).thenReturn(mesaId);
        when(mesaService.resolverSesionValida("token-valido")).thenReturn(sesion);

        Tenant tenant = mock(Tenant.class);
        when(tenant.estaSuspendido()).thenReturn(false);
        when(tenantService.obtenerPorId(tenantIdEsperado)).thenReturn(tenant);

        // El corazón del test: si el controlador vuelve a resolver la
        // sucursal ANTES de poblar TenantContext, esto falla comparando
        // null contra 42L, reproduciendo el bug real.
        when(mesaService.obtenerSucursalIdDeMesa(mesaId)).thenAnswer(invocacion -> {
            assertThat(TenantContext.obtenerTenantId())
                    .as("TenantContext debe estar poblado con el tenant de la mesa ANTES de resolver su sucursal")
                    .isEqualTo(tenantIdEsperado);
            return 99L;
        });

        when(menuService.listarCategorias()).thenReturn(List.of());

        controller.obtenerCarta("token-valido");

        verify(mesaService).obtenerSucursalIdDeMesa(mesaId);
    }

    /**
     * Fake mínimo de TenantFilterActivator: puebla TenantContext igual que
     * el real, sin el filtro de Hibernate (que necesita un EntityManager
     * real) -- no hace falta para lo que este test prueba.
     */
    private static class TenantFilterActivatorFalso extends TenantFilterActivator {
        @Override
        public <T> T ejecutarConTenant(Long tenantId, Supplier<T> accion) {
            TenantContext.establecerTenantId(tenantId);
            try {
                return accion.get();
            } finally {
                TenantContext.limpiar();
            }
        }
    }
}
