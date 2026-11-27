package com.mezzalia.menu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mezzalia.identidad.RolUsuario;
import com.mezzalia.identidad.UsuarioInterno;
import com.mezzalia.identidad.UsuarioInternoRepository;
import com.mezzalia.identidad.servicio.JwtService;
import com.mezzalia.mesa.Mesa;
import com.mezzalia.mesa.MesaRepository;
import com.mezzalia.mesa.servicio.MesaService;
import com.mezzalia.sucursal.Sucursal;
import com.mezzalia.sucursal.SucursalContext;
import com.mezzalia.sucursal.SucursalRepository;
import com.mezzalia.tenant.Tenant;
import com.mezzalia.tenant.TenantContext;
import com.mezzalia.tenant.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ingredientes quitables ("sin cebolla"), de punta a punta por HTTP con JWT
 * reales: el admin arma la hamburguesa por capas, el comensal anonimo la ve
 * en la carta y pide sin cebolla, y el panel recibe "SIN cebolla".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class IngredientesEndToEndTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JwtService jwtService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private SucursalRepository sucursalRepository;
    @Autowired private MesaRepository mesaRepository;
    @Autowired private MesaService mesaService;
    @Autowired private UsuarioInternoRepository usuarioRepository;

    private Long tenantId;
    private Long sucursalId;
    private String admin;
    private String qr;
    /** Mesa del QR principal (para confirmarla o abrirle un QR nuevo). */
    private Long mesaId;
    private long hamburguesa;
    private long limonada;
    /** Ids en orden: pan arriba, lechuga, cebolla, carne, pan abajo. */
    private final List<Long> capas = new ArrayList<>();

    @BeforeEach
    void preparar() throws Exception {
        tenantId = tenant();
        sucursalId = sucursal(tenantId);
        admin = token(tenantId, RolUsuario.ADMIN_RESTAURANTE, sucursalId);
        qr = qr(tenantId, sucursalId);

        long categoria = idDe(conAdmin(post("/api/admin/menu/categorias"), "{\"nombre\":\"Hamburguesas\",\"orden\":0}"));
        hamburguesa = idDe(conAdmin(post("/api/admin/menu/items"),
                "{\"categoriaId\":" + categoria + ",\"nombre\":\"Hamburguesa clásica\",\"precio\":28000}"));
        limonada = idDe(conAdmin(post("/api/admin/menu/items"),
                "{\"categoriaId\":" + categoria + ",\"nombre\":\"Limonada\",\"precio\":9000}"));
        for (String[] capa : new String[][] {{"Pan", "false"}, {"Lechuga", "true"}, {"Cebolla", "true"}, {"Carne", "false"}, {"Pan de abajo", "false"}}) {
            capas.add(idDe(conAdmin(post("/api/admin/menu/items/{id}/ingredientes", hamburguesa),
                    "{\"nombre\":\"" + capa[0] + "\",\"removible\":" + capa[1] + "}")
                    .andExpect(status().isCreated())));
        }
    }

    @AfterEach
    void limpiar() {
        TenantContext.limpiar();
        SucursalContext.limpiar();
        SecurityContextHolder.clearContext();
    }

    @Test
    void laCartaPublicaTraeLosIngredientesEnOrdenYLaPresentacion() throws Exception {
        mvc.perform(get("/api/publico/mesas/{t}/carta", qr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias[0].items[0].presentacionIngredientes").value("APILADO"))
                .andExpect(jsonPath("$.categorias[0].items[0].ingredientes", hasSize(5)))
                .andExpect(jsonPath("$.categorias[0].items[0].ingredientes[0].nombre").value("Pan"))
                .andExpect(jsonPath("$.categorias[0].items[0].ingredientes[0].removible").value(false))
                .andExpect(jsonPath("$.categorias[0].items[0].ingredientes[2].nombre").value("Cebolla"))
                .andExpect(jsonPath("$.categorias[0].items[1].ingredientes", hasSize(0)));
    }

    @Test
    void elComensalPideSinCebollaYElPanelLoVe() throws Exception {
        long cebolla = capas.get(2);
        pedir("[{\"itemMenuId\":" + hamburguesa + ",\"cantidad\":1,\"sinIngredientes\":[" + cebolla + "]},"
                + "{\"itemMenuId\":" + hamburguesa + ",\"cantidad\":1}]")
                .andExpect(status().isCreated())
                // El precio no cambia por quitar algo.
                .andExpect(jsonPath("$.total").value(56000))
                .andExpect(jsonPath("$.items[0].sinIngredientes[0]").value("Cebolla"))
                .andExpect(jsonPath("$.items[1].sinIngredientes", hasSize(0)));

        mvc.perform(get("/api/admin/pedidos").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$[0].items[0].sinIngredientes[0]").value("Cebolla"));
    }

    @Test
    void elPedidoGuardaUnaCopiaDelNombreAunqueDespuesSeRenombreOBorreElIngrediente() throws Exception {
        long cebolla = capas.get(2);
        pedir("[{\"itemMenuId\":" + hamburguesa + ",\"cantidad\":1,\"sinIngredientes\":[" + cebolla + "]}]");
        conAdmin(put("/api/admin/menu/ingredientes/{id}", cebolla), "{\"nombre\":\"Cebolla caramelizada\",\"removible\":true}");
        conAdmin(delete("/api/admin/menu/ingredientes/{id}", cebolla), null).andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/pedidos").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$[0].items[0].sinIngredientes[0]").value("Cebolla"));
    }

    @Test
    void noSePuedeQuitarAlgoQueNoEsQuitableNiDeOtroPlato() throws Exception {
        long pan = capas.get(0);
        pedir("[{\"itemMenuId\":" + hamburguesa + ",\"cantidad\":1,\"sinIngredientes\":[" + pan + "]}]")
                .andExpect(status().isBadRequest());
        // La cebolla es de la hamburguesa, no de la limonada.
        pedir("[{\"itemMenuId\":" + limonada + ",\"cantidad\":1,\"sinIngredientes\":[" + capas.get(2) + "]}]")
                .andExpect(status().isBadRequest());
        pedir("[{\"itemMenuId\":" + hamburguesa + ",\"cantidad\":1,\"sinIngredientes\":[999999]}]")
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/admin/pedidos").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void elAdminReordenaCambiaLaPresentacionYSubeLaFotoDeUnaCapa() throws Exception {
        List<Long> invertido = new ArrayList<>(capas);
        java.util.Collections.reverse(invertido);
        conAdmin(put("/api/admin/menu/items/{id}/ingredientes/orden", hamburguesa), "{\"ids\":" + invertido + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Pan de abajo"));

        conAdmin(patch("/api/admin/menu/items/{id}/presentacion", hamburguesa), "{\"presentacion\":\"ESPARCIDO\"}")
                .andExpect(jsonPath("$.presentacionIngredientes").value("ESPARCIDO"));

        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
        mvc.perform(multipart("/api/admin/menu/ingredientes/{id}/imagen", capas.get(2))
                        .file(new MockMultipartFile("archivo", "cebolla.png", "image/png", png))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imagenUrl").value(org.hamcrest.Matchers.startsWith("/uploads/")));

        // ASVS V5.2.2: un HTML disfrazado de PNG (Content-Type falso) se rechaza.
        mvc.perform(multipart("/api/admin/menu/ingredientes/{id}/imagen", capas.get(2))
                        .file(new MockMultipartFile("archivo", "foto.png", "image/png",
                                "<html><script>alert(1)</script></html>".getBytes()))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());

        // Reordenar con un id de mas, de menos o ajeno se rechaza.
        conAdmin(put("/api/admin/menu/items/{id}/ingredientes/orden", hamburguesa), "{\"ids\":" + capas.subList(0, 3) + "}")
                .andExpect(status().isNotFound());
    }

    @Test
    void unMeseroNoPuedeEditarIngredientes() throws Exception {
        String mesero = token(tenantId, RolUsuario.MESERO, sucursalId);
        mvc.perform(post("/api/admin/menu/items/{id}/ingredientes", hamburguesa)
                        .header("Authorization", "Bearer " + mesero)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Pepinillos\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unAdminDeOtroRestauranteNoVeNiTocaLosIngredientes() throws Exception {
        Long otroTenant = tenant();
        String otroAdmin = token(otroTenant, RolUsuario.ADMIN_RESTAURANTE, sucursal(otroTenant));

        mvc.perform(get("/api/admin/menu/items/{id}/ingredientes", hamburguesa).header("Authorization", "Bearer " + otroAdmin))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/menu/ingredientes/{id}", capas.get(2)).header("Authorization", "Bearer " + otroAdmin))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/menu/items/{id}/ingredientes", hamburguesa).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    void duplicarLaCartaAOtraSucursalCopiaLosIngredientes() throws Exception {
        Long otraSucursal = sucursal(tenantId);
        String adminOtra = token(tenantId, RolUsuario.ADMIN_RESTAURANTE, otraSucursal);
        mvc.perform(post("/api/admin/menu/duplicar").param("sucursalOrigenId", sucursalId.toString())
                        .header("Authorization", "Bearer " + adminOtra))
                .andExpect(status().is2xxSuccessful());

        String qrOtra = qr(tenantId, otraSucursal);
        mvc.perform(get("/api/publico/mesas/{t}/carta", qrOtra))
                .andExpect(jsonPath("$.categorias[0].items[0].ingredientes", hasSize(5)))
                .andExpect(jsonPath("$.categorias[0].items[0].ingredientes[2].nombre").value("Cebolla"));
    }

    // --- reporte "que quitan tus clientes" ---------------------------------

    @Test
    void elReporteCuentaUnidadesQuitadasPorPlatoYExcluyeCancelados() throws Exception {
        long cebolla = capas.get(2), lechuga = capas.get(1);
        // 3 hamburguesas sin cebolla en una linea, 1 sin cebolla ni lechuga,
        // 4 normales y 2 limonadas.
        pedir("[{\"itemMenuId\":" + hamburguesa + ",\"cantidad\":3,\"sinIngredientes\":[" + cebolla + "]},"
                + "{\"itemMenuId\":" + hamburguesa + ",\"cantidad\":1,\"sinIngredientes\":[" + cebolla + "," + lechuga + "]},"
                + "{\"itemMenuId\":" + hamburguesa + ",\"cantidad\":4},"
                + "{\"itemMenuId\":" + limonada + ",\"cantidad\":2}]").andExpect(status().isCreated());
        // Un pedido cancelado no cuenta.
        long cancelado = idDe(pedir("[{\"itemMenuId\":" + hamburguesa + ",\"cantidad\":5,\"sinIngredientes\":[" + lechuga + "]}]"));
        conAdmin(patch("/api/admin/pedidos/{id}/estado", cancelado), "{\"estado\":\"CANCELADO\"}").andExpect(status().isOk());
        conAdmin(patch("/api/admin/pedidos/mesa/{id}/confirmar", mesaId), null).andExpect(status().isOk());

        mvc.perform(get("/api/admin/reportes/ingredientes-quitados").param("dias", "30").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unidadesVendidas").value(10))
                .andExpect(jsonPath("$.unidadesPersonalizadas").value(4))
                .andExpect(jsonPath("$.porcentajePersonalizado").value(40.0))
                // La limonada nadie la personaliza: no aparece.
                .andExpect(jsonPath("$.platos", hasSize(1)))
                .andExpect(jsonPath("$.platos[0].nombre").value("Hamburguesa clásica"))
                .andExpect(jsonPath("$.platos[0].unidadesVendidas").value(8))
                .andExpect(jsonPath("$.platos[0].porcentajePersonalizado").value(50.0))
                .andExpect(jsonPath("$.platos[0].ingredientes[0].nombre").value("Cebolla"))
                .andExpect(jsonPath("$.platos[0].ingredientes[0].unidades").value(4))
                .andExpect(jsonPath("$.platos[0].ingredientes[0].porcentaje").value(50.0))
                .andExpect(jsonPath("$.platos[0].ingredientes[1].nombre").value("Lechuga"))
                .andExpect(jsonPath("$.platos[0].ingredientes[1].porcentaje").value(12.5))
                .andExpect(jsonPath("$.masQuitados[0].nombre").value("Cebolla"))
                .andExpect(jsonPath("$.masQuitados[0].plato").value("Hamburguesa clásica"));
    }

    @Test
    void elReporteNoCuentaLoQueElMeseroTodaviaNoConfirma() throws Exception {
        pedir("[{\"itemMenuId\":" + hamburguesa + ",\"cantidad\":2,\"sinIngredientes\":[" + capas.get(2) + "]}]");
        mvc.perform(get("/api/admin/reportes/ingredientes-quitados").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.unidadesVendidas").value(0));
    }

    @Test
    void elReporteEsSoloDelAdminYDeSuRestaurante() throws Exception {
        long cebolla = capas.get(2);
        pedir("[{\"itemMenuId\":" + hamburguesa + ",\"cantidad\":2,\"sinIngredientes\":[" + cebolla + "]}]");

        String mesero = token(tenantId, RolUsuario.MESERO, sucursalId);
        mvc.perform(get("/api/admin/reportes/ingredientes-quitados").header("Authorization", "Bearer " + mesero))
                .andExpect(status().isForbidden());

        Long otroTenant = tenant();
        String otroAdmin = token(otroTenant, RolUsuario.ADMIN_RESTAURANTE, sucursal(otroTenant));
        mvc.perform(get("/api/admin/reportes/ingredientes-quitados").header("Authorization", "Bearer " + otroAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unidadesVendidas").value(0))
                .andExpect(jsonPath("$.platos", hasSize(0)));
    }

    @Test
    void unRangoDeDiasFueraDeLimitesSeAjustaEnVezDeFallar() throws Exception {
        mvc.perform(get("/api/admin/reportes/ingredientes-quitados").param("dias", "5000").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias").value(365));
    }

    // --- pedido de la mesa (2026-11-27) -----------------------------------

    @Test
    void todoPedidoNaceSinConfirmarYLaCocinaNoLoVeHastaQueElMeseroConfirma() throws Exception {
        pedirComo("Andrés", "[{\"itemMenuId\":" + hamburguesa + ",\"cantidad\":1,\"sinIngredientes\":[" + capas.get(2) + "]}]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("POR_CONFIRMAR"))
                .andExpect(jsonPath("$.apodo").value("Andrés"));
        pedirComo(null, "[{\"itemMenuId\":" + limonada + ",\"cantidad\":2}]");

        // Lo que mira la cocina (RECIBIDO) esta vacio...
        mvc.perform(get("/api/admin/pedidos").param("estado", "RECIBIDO").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/admin/pedidos").param("estado", "POR_CONFIRMAR").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].apodo").value("Andrés"));

        // ...hasta que el mesero confirma la mesa: ahi pasan los dos juntos.
        conAdmin(patch("/api/admin/pedidos/mesa/{id}/confirmar", mesaId), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].estado").value("RECIBIDO"));
        mvc.perform(get("/api/admin/pedidos").param("estado", "RECIBIDO").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$", hasSize(2)));
        // Confirmar de nuevo no hace nada.
        conAdmin(patch("/api/admin/pedidos/mesa/{id}/confirmar", mesaId), null).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void elPedidoDeLaMesaMuestraLoDeTodosLosCelularesSinDatosPersonales() throws Exception {
        pedirComo("Andrés", "[{\"itemMenuId\":" + hamburguesa + ",\"cantidad\":1,\"sinIngredientes\":[" + capas.get(2) + "]}]");
        // Otro celular, sin apodo y CON datos de contacto (con consentimiento).
        mvc.perform(post("/api/publico/mesas/{t}/pedidos", qr).contentType(MediaType.APPLICATION_JSON)
                .content("{\"claveIdempotencia\":\"" + UUID.randomUUID() + "\",\"items\":[{\"itemMenuId\":" + limonada
                        + ",\"cantidad\":1}],\"comensal\":{\"nombre\":\"Laura\",\"telefono\":\"3001234567\",\"consentimiento\":true}}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/publico/mesas/{t}/pedidos", qr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].apodo").value("Andrés"))
                .andExpect(jsonPath("$[0].estado").value("POR_CONFIRMAR"))
                .andExpect(jsonPath("$[0].items[0].sinIngredientes[0]").value("Cebolla"))
                .andExpect(jsonPath("$[1].apodo").doesNotExist())
                // El telefono y el nombre del comensal nunca salen por aca.
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("300"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Laura"))));
    }

    @Test
    void unQrNuevoParaLaMesaEsUnaCuentaNueva() throws Exception {
        pedirComo("Andrés", "[{\"itemMenuId\":" + limonada + ",\"cantidad\":1}]");
        Thread.sleep(5); // el QR nuevo debe abrirse DESPUES del pedido anterior
        String qrNuevo = nuevaSesion(mesaId);
        mvc.perform(get("/api/publico/mesas/{t}/pedidos", qrNuevo)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void unMeseroDeOtraSucursalNoPuedeConfirmarLaMesa() throws Exception {
        pedirComo(null, "[{\"itemMenuId\":" + limonada + ",\"cantidad\":1}]");
        String meseroOtra = token(tenantId, RolUsuario.MESERO, sucursal(tenantId));
        mvc.perform(patch("/api/admin/pedidos/mesa/{id}/confirmar", mesaId).header("Authorization", "Bearer " + meseroOtra))
                .andExpect(status().isNotFound());
        String meseroPropio = token(tenantId, RolUsuario.MESERO, sucursalId);
        mvc.perform(patch("/api/admin/pedidos/mesa/{id}/confirmar", mesaId).header("Authorization", "Bearer " + meseroPropio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void unApodoDeMasDe40CaracteresSeRechaza() throws Exception {
        pedirComo("x".repeat(41), "[{\"itemMenuId\":" + limonada + ",\"cantidad\":1}]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("apodo")));
        // Mismo arreglo para cualquier validacion del cuerpo (antes: 500).
        pedir("[{\"itemMenuId\":" + limonada + ",\"cantidad\":0}]").andExpect(status().isBadRequest());
    }

    // --- ayudantes --------------------------------------------------------

    private ResultActions pedirComo(String apodo, String items) throws Exception {
        return mvc.perform(post("/api/publico/mesas/{t}/pedidos", qr)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"claveIdempotencia\":\"" + UUID.randomUUID() + "\",\"items\":" + items
                        + (apodo == null ? "" : ",\"apodo\":\"" + apodo + "\"") + "}"));
    }

    private String nuevaSesion(Long mesa) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin.test", null, List.of()));
        TenantContext.establecerTenantId(tenantId);
        try {
            return mesaService.generarSesion(mesa).getToken();
        } finally {
            limpiar();
        }
    }

    private ResultActions conAdmin(MockHttpServletRequestBuilder req, String cuerpo) throws Exception {
        req.header("Authorization", "Bearer " + admin);
        if (cuerpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mvc.perform(req);
    }

    private ResultActions pedir(String items) throws Exception {
        return mvc.perform(post("/api/publico/mesas/{t}/pedidos", qr)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"claveIdempotencia\":\"" + UUID.randomUUID() + "\",\"items\":" + items + "}"));
    }

    private long idDe(ResultActions r) throws Exception {
        JsonNode nodo = json.readTree(r.andReturn().getResponse().getContentAsString());
        assertThat(nodo.has("id")).as("respuesta sin id: %s", nodo).isTrue();
        return nodo.get("id").asLong();
    }

    private Long tenant() {
        Tenant t = new Tenant();
        t.setNombre("Bar " + UUID.randomUUID());
        t.setPlan("STARTER");
        t.setMoneda("COP");
        t.setZonaHoraria("America/Bogota");
        return tenantRepository.save(t).getId();
    }

    private Long sucursal(Long tenant) {
        Sucursal s = new Sucursal();
        s.setTenantId(tenant);
        s.setNombre("Sede " + UUID.randomUUID());
        s.setActiva(true);
        return sucursalRepository.save(s).getId();
    }

    private String qr(Long tenant, Long sucursal) {
        Mesa m = new Mesa();
        m.setTenantId(tenant);
        m.setSucursalId(sucursal);
        m.setCodigo("M-" + UUID.randomUUID());
        m = mesaRepository.save(m);
        if (tenant.equals(tenantId) && sucursal.equals(sucursalId) && mesaId == null) {
            mesaId = m.getId();
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin.test", null, List.of()));
        TenantContext.establecerTenantId(tenant);
        try {
            return mesaService.generarSesion(m.getId()).getToken();
        } finally {
            limpiar();
        }
    }

    private String token(Long tenant, RolUsuario rol, Long sucursal) {
        UsuarioInterno u = new UsuarioInterno();
        u.setTenantId(tenant);
        u.setUsername("u-" + UUID.randomUUID());
        u.setPasswordHash("no-relevante");
        u.setRol(rol);
        u.setActivo(true);
        u = usuarioRepository.save(u);
        // La sucursal viaja en el JWT; para estos tests no hace falta una
        // AsignacionSucursal (eso lo cubre AuthServiceTest).
        return jwtService.generarAccessToken(u, sucursal);
    }
}
