package com.mezzalia.common.seguridad;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** OWASP ASVS 5.0 V6.2 (ver PoliticaPassword). */
class PoliticaPasswordTest {

    @Test
    void aceptaUnaFraseLargaSinReglasDeComposicion() {
        assertThat(PoliticaPassword.problema("arepa con queso y cafe", "mesero.uno")).isNull();
        assertThat(PoliticaPassword.problema("Password1234", "mesero.uno")).isNull();
    }

    @Test
    void rechazaCortasComunesRepetidasOConElUsuario() {
        assertThat(PoliticaPassword.problema("corta", "x")).contains("al menos 8");
        assertThat(PoliticaPassword.problema("12345678", "x")).contains("muy común");
        assertThat(PoliticaPassword.problema("Mezzalia123", "x")).contains("muy común");
        assertThat(PoliticaPassword.problema("aaaaaaaaaa", "x")).contains("muy común");
        assertThat(PoliticaPassword.problema("ababababab", "x")).contains("muy común");
        assertThat(PoliticaPassword.problema("hola.mesero.uno!", "mesero.uno")).contains("nombre de usuario");
    }

    @Test
    void permite64CaracteresPeroNoMasDe72BytesPorqueBcryptLosIgnora() {
        assertThat(PoliticaPassword.problema("a1b2c3d4".repeat(8), "x")).isNull(); // 64
        assertThat(PoliticaPassword.problema("a1b2c3d4".repeat(9) + "z", "x")).contains("demasiado larga"); // 73
        // 30 eñes = 60 bytes en UTF-8: el límite es en bytes, no en caracteres.
        assertThat(PoliticaPassword.problema("ñ".repeat(37) + "x", "x")).contains("demasiado larga");
    }

    @Test
    void elLogNoAceptaSaltosDeLineaNiTextoInfinito() {
        assertThat(EventosSeguridad.limpiar("admin\nevento=login_exitoso usuario=root"))
                .doesNotContain("\n").startsWith("admin_evento");
        assertThat(EventosSeguridad.limpiar("x".repeat(500))).hasSizeLessThan(110);
        assertThat(EventosSeguridad.limpiar(null)).isEqualTo("-");
    }
}
