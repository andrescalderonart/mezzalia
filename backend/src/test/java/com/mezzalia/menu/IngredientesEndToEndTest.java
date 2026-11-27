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

    // --- ayudantes --------------------------------------------------------

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
