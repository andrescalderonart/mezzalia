package com.mezzalia.common.salud;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auditoria 2026-09-08 §2.2 / roadmap §11: health check para el hosting.
 *
 * Lo que importa probar no es que Actuator funcione (eso es del framework),
 * sino los dos limites que decidimos nosotros: (1) el health responde SIN
 * login, porque el balanceador no tiene token, y (2) NADA MAS de Actuator
 * queda publicado -- /actuator/env filtraria configuracion y secretos. Si
 * alguien amplia "management.endpoints.web.exposure.include" a "*" por
 * comodidad, este test tiene que romperse.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class HealthCheckTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthRespondeUpSinAutenticacion() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                // show-details/show-components: never -> no se listan
                // componentes internos (db, diskSpace...) a un anonimo.
                .andExpect(content().string(not(containsString("components"))));
    }

    @Test
    void probesDeLivenessYReadinessRespondenSinAutenticacion() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void otrosEndpointsDeActuatorNoQuedanPublicos() throws Exception {
        for (String ruta : new String[] {"/actuator/env", "/actuator/beans", "/actuator/mappings", "/actuator"}) {
            mockMvc.perform(get(ruta))
                    .andExpect(result -> {
                        int codigo = result.getResponse().getStatus();
                        if (codigo == 200) {
                            throw new AssertionError(ruta + " quedo publico (200) y no deberia");
                        }
                    });
        }
    }
}
