package com.mezzalia.common.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/** OWASP ASVS 5.0 V3.4: cabeceras de seguridad en TODAS las respuestas. */
@SpringBootTest
@AutoConfigureMockMvc
class CabecerasSeguridadTest {

    @Autowired
    MockMvc mvc;

    @Test
    void lasRespuestasLlevanLasCabecerasDeSeguridad() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")));
    }

    @Test
    void sobreHttpsSeExigeHsts() throws Exception {
        mvc.perform(get("/actuator/health").secure(true))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")));
    }
}
