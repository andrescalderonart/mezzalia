package com.mezzalia.llamado;

import com.mezzalia.identidad.AsignacionSucursal;
import com.mezzalia.identidad.AsignacionSucursalRepository;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Llamar al mesero" / "pedir la cuenta" (enfoque en bares), de punta a
 * punta por HTTP: el comensal anonimo llama desde el QR y el personal lo ve
 * y lo atiende con un JWT real (pasa por JwtAuthenticationFilter, que es
 * quien puebla tenant y sucursal). Va por HTTP a proposito: la regresion de
 * la carta publica (CartaPublicaEndToEndTest) vino justo de probar los
 * servicios con el contexto ya poblado a mano.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class LlamadosEndToEndTest {

    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwtService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private SucursalRepository sucursalRepository;
    @Autowired private MesaRepository mesaRepository;
    @Autowired private MesaService mesaService;
    @Autowired private UsuarioInternoRepository usuarioRepository;
    @Autowired private AsignacionSucursalRepository asignacionRepository;

    private Long tenantId;
    private Long sucursalA;
    private Long sucursalB;
    private Long mesaA;
    private String qrMesaA;
    private String qrMesaB;
    private String meseroA;
    private String meseroB;
    private String admin;

    @BeforeEach
    void preparar() {
        Tenant t = new Tenant();
        t.setNombre("Bar " + UUID.randomUUID());
        t.setPlan("STARTER");
        t.setMoneda("COP");
        t.setZonaHoraria("America/Bogota");
        tenantId = tenantRepository.save(t).getId();
        sucursalA = sucursal();
        sucursalB = sucursal();

        Mesa a = mesa(sucursalA);
        mesaA = a.getId();
        Mesa b = mesa(sucursalB);
        qrMesaA = qr(a);
        qrMesaB = qr(b);

        meseroA = tokenDe(usuario(RolUsuario.MESERO, sucursalA), sucursalA);
        meseroB = tokenDe(usuario(RolUsuario.MESERO, sucursalB), sucursalB);
        admin = tokenDe(usuario(RolUsuario.ADMIN_RESTAURANTE, null), null);
    }

    @AfterEach
    void limpiar() {
        TenantContext.limpiar();
        SucursalContext.limpiar();
        SecurityContextHolder.clearContext();
    }

    @Test
    void elComensalAnonimoLlamaYElMeseroDeEsaSucursalLoVeYLoAtiende() throws Exception {
        long id = idDe(llamar(qrMesaA, "MESERO").andExpect(status().isOk())
                .andExpect(jsonPath("$.mesaId").value(mesaA))
                .andExpect(jsonPath("$.tipo").value("MESERO"))
                .andExpect(jsonPath("$.pendiente").value(true)));

        mvc.perform(get("/api/admin/llamados").header("Authorization", "Bearer " + meseroA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id));

        mvc.perform(patch("/api/admin/llamados/{id}/atender", id).header("Authorization", "Bearer " + meseroA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendiente").value(false));

        mvc.perform(get("/api/admin/llamados").header("Authorization", "Bearer " + meseroA))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void tocarElBotonVariasVecesNoDuplicaElAviso() throws Exception {
        long primero = idDe(llamar(qrMesaA, "CUENTA"));
        long segundo = idDe(llamar(qrMesaA, "CUENTA"));
        long otroTipo = idDe(llamar(qrMesaA, "MESERO"));

        org.assertj.core.api.Assertions.assertThat(segundo).isEqualTo(primero);
        org.assertj.core.api.Assertions.assertThat(otroTipo).isNotEqualTo(primero);
        mvc.perform(get("/api/admin/llamados").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void unMeseroNoVeNiAtiendeLlamadosDeOtraSucursalPeroElAdminVeTodos() throws Exception {
        long deA = idDe(llamar(qrMesaA, "MESERO"));
        llamar(qrMesaB, "CUENTA");

        mvc.perform(get("/api/admin/llamados").header("Authorization", "Bearer " + meseroB))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].tipo").value("CUENTA"));
        mvc.perform(patch("/api/admin/llamados/{id}/atender", deA).header("Authorization", "Bearer " + meseroB))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/llamados").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void sinLoginNoSePuedenVerLosLlamados() throws Exception {
        mvc.perform(get("/api/admin/llamados")).andExpect(status().isUnauthorized());
    }

    @Test
    void unQrInventadoNoPuedeLlamar() throws Exception {
        llamar("no-existe-" + UUID.randomUUID(), "MESERO").andExpect(status().is4xxClientError());
    }

    @Test
    void unTipoInvalidoSeRechaza() throws Exception {
        llamar(qrMesaA, "TRAIGAN_CERVEZA").andExpect(status().isBadRequest());
    }

    @Test
    void hayUnTopeDeLlamadosPorMesaContraElAbuso() throws Exception {
        // Llamar y atender en bucle: cada uno es un llamado nuevo.
        for (int i = 0; i < 10; i++) {
            long id = idDe(llamar(qrMesaA, "MESERO"));
            mvc.perform(patch("/api/admin/llamados/{id}/atender", id).header("Authorization", "Bearer " + admin));
        }
        llamar(qrMesaA, "MESERO").andExpect(status().isTooManyRequests());
    }

    // --- ayudantes --------------------------------------------------------

    private ResultActions llamar(String qr, String tipo) throws Exception {
        return mvc.perform(post("/api/publico/mesas/{token}/llamados", qr)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"" + tipo + "\"}"));
    }

    private long idDe(ResultActions r) throws Exception {
        String json = r.andReturn().getResponse().getContentAsString();
        return Long.parseLong(json.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private Long sucursal() {
        Sucursal s = new Sucursal();
        s.setTenantId(tenantId);
        s.setNombre("Sede " + UUID.randomUUID());
        s.setActiva(true);
        return sucursalRepository.save(s).getId();
    }

    private Mesa mesa(Long sucursalId) {
        Mesa m = new Mesa();
        m.setTenantId(tenantId);
        m.setSucursalId(sucursalId);
        m.setCodigo("M-" + UUID.randomUUID());
        return mesaRepository.save(m);
    }

    private String qr(Mesa mesa) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin.test", null, List.of()));
        TenantContext.establecerTenantId(tenantId);
        try {
            return mesaService.generarSesion(mesa.getId()).getToken();
        } finally {
            limpiar();
        }
    }

    private UsuarioInterno usuario(RolUsuario rol, Long sucursalAsignada) {
        UsuarioInterno u = new UsuarioInterno();
        u.setTenantId(tenantId);
        u.setUsername("u-" + UUID.randomUUID());
        u.setPasswordHash("no-relevante");
        u.setRol(rol);
        u.setActivo(true);
        u = usuarioRepository.save(u);
        if (sucursalAsignada != null) {
            AsignacionSucursal a = new AsignacionSucursal();
            a.setTenantId(tenantId);
            a.setUsuarioInternoId(u.getId());
            a.setSucursalId(sucursalAsignada);
            asignacionRepository.save(a);
        }
        return u;
    }

    private String tokenDe(UsuarioInterno u, Long sucursalId) {
        return jwtService.generarAccessToken(u, sucursalId);
    }
}
