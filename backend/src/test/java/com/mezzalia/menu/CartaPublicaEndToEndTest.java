package com.mezzalia.menu;

import com.mezzalia.mesa.Mesa;
import com.mezzalia.mesa.MesaRepository;
import com.mezzalia.mesa.servicio.MesaService;
import com.mezzalia.menu.servicio.MenuService;
import com.mezzalia.sucursal.Sucursal;
import com.mezzalia.sucursal.SucursalContext;
import com.mezzalia.sucursal.SucursalRepository;
import com.mezzalia.tenant.Tenant;
import com.mezzalia.tenant.TenantContext;
import com.mezzalia.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regresion 2026-11-25: el flujo del COMENSAL (abrir la carta por QR y pedir)
 * estaba roto en main desde 427d8d5 (menus por sucursal, PR #30) y ningun test
 * lo detecto.
 *
 * Causa: MenuPublicoController llamaba a MesaService.obtenerSucursalIdDeMesa()
 * ANTES de activar el tenant (TenantFilterActivator.ejecutarConTenant). Ese
 * metodo compara la mesa contra TenantContext, que en un request anonimo esta
 * vacio, asi que devolvia null; con sucursal null, GET /carta respondia 409
 * SUCURSAL_NO_SELECCIONADA y POST /pedidos 404 "Item de carta no encontrado".
 *
 * Por que paso desapercibido: MenuServiceTest/PedidoServiceTest llaman a los
 * servicios con TenantContext/SucursalContext ya poblados a mano, asi que
 * nunca ejercitan el ORDEN en que el controller publico los puebla. Este test
 * va por HTTP, anonimo, con el contexto real completo: exactamente lo que hace
 * el celular del comensal.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CartaPublicaEndToEndTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private SucursalRepository sucursalRepository;
    @Autowired private MesaRepository mesaRepository;
    @Autowired private MenuService menuService;
    @Autowired private MesaService mesaService;

    @AfterEach
    void limpiar() {
        TenantContext.limpiar();
        SucursalContext.limpiar();
        SecurityContextHolder.clearContext();
    }

    @Test
    void elComensalAnonimoVeLaCartaDeLaSucursalDeSuMesaYPuedePedir() throws Exception {
        Escenario e = prepararRestauranteConCarta();

        mockMvc.perform(get("/api/publico/mesas/{token}/carta", e.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias[0].nombre").value("Platos fuertes"))
                .andExpect(jsonPath("$.categorias[0].items[0].nombre").value("Bandeja paisa"));

        mockMvc.perform(post("/api/publico/mesas/{token}/pedidos", e.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"claveIdempotencia":"%s","items":[{"itemMenuId":%d,"cantidad":2}]}
                                """.formatted(UUID.randomUUID(), e.itemId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(76000));
    }

    @Test
    void laCartaPublicaNoMuestraPlatosDeOtraSucursalDelMismoRestaurante() throws Exception {
        Escenario e = prepararRestauranteConCarta();
        // Segunda sucursal del MISMO tenant, con su propia carta.
        Long otraSucursalId = crearSucursal(e.tenantId);
        Long itemDeOtraSucursal = enContexto(e.tenantId, otraSucursalId, () -> {
            var categoria = menuService.crearCategoria("Solo en la otra sede", 0);
            return menuService.crearItem(categoria.getId(), "Plato de la otra sede", null, new BigDecimal("1000"), null).getId();
        });

        mockMvc.perform(get("/api/publico/mesas/{token}/carta", e.token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias.length()").value(1))
                .andExpect(jsonPath("$.categorias[0].nombre").value("Platos fuertes"));

        mockMvc.perform(post("/api/publico/mesas/{token}/pedidos", e.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"claveIdempotencia":"%s","items":[{"itemMenuId":%d,"cantidad":1}]}
                                """.formatted(UUID.randomUUID(), itemDeOtraSucursal)))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------

    private record Escenario(Long tenantId, String token, Long itemId) {}

    private Escenario prepararRestauranteConCarta() {
        Tenant tenant = new Tenant();
        tenant.setNombre("Restaurante " + UUID.randomUUID());
        tenant.setPlan("STARTER");
        tenant.setMoneda("COP");
        tenant.setZonaHoraria("America/Bogota");
        Long tenantId = tenantRepository.save(tenant).getId();
        Long sucursalId = crearSucursal(tenantId);

        return enContexto(tenantId, sucursalId, () -> {
            var categoria = menuService.crearCategoria("Platos fuertes", 0);
            Long itemId = menuService.crearItem(categoria.getId(), "Bandeja paisa", null, new BigDecimal("38000"), null).getId();
            Mesa mesa = new Mesa();
            mesa.setTenantId(tenantId);
            mesa.setSucursalId(sucursalId);
            mesa.setCodigo("M-" + UUID.randomUUID());
            mesa = mesaRepository.save(mesa);
            String token = mesaService.generarSesion(mesa.getId()).getToken();
            return new Escenario(tenantId, token, itemId);
        });
    }

    private Long crearSucursal(Long tenantId) {
        Sucursal sucursal = new Sucursal();
        sucursal.setTenantId(tenantId);
        sucursal.setNombre("Sucursal " + UUID.randomUUID());
        sucursal.setActiva(true);
        return sucursalRepository.save(sucursal).getId();
    }

    /** Arma datos como lo haria el admin de esa sucursal, y deja el hilo limpio despues. */
    private <T> T enContexto(Long tenantId, Long sucursalId, java.util.function.Supplier<T> accion) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin.test", null, List.of()));
        TenantContext.establecerTenantId(tenantId);
        SucursalContext.establecerSucursalId(sucursalId);
        try {
            return accion.get();
        } finally {
            limpiar();
        }
    }
}
