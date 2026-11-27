package com.mezzalia.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Con el contexto completo arriba, la JVM tiene que estar en hora de
 * Colombia aunque la maquina (o el runner de CI, que corre en UTC) no.
 */
@SpringBootTest
class ZonaHorariaConfigTest {

    @Test
    void laAplicacionCorreEnHoraDeColombia() {
        assertThat(TimeZone.getDefault().getID()).isEqualTo("America/Bogota");
    }
}
