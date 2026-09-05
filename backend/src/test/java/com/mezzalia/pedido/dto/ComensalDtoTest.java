package com.mezzalia.pedido.dto;

import com.mezzalia.pedido.Comensal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF-09/documentacion-tecnica.md §10: "un mesero no necesita ver el
 * telefono completo, un admin_restaurante si". Test unitario puro (sin
 * contexto de Spring, sin BD) -- mismo criterio que
 * GlobalExceptionHandlerTest: ComensalDto.desde() es una funcion pura de
 * sus argumentos, no necesita mas que eso para probarse.
 */
class ComensalDtoTest {

    @Test
    void adminRestauranteVeElTelefonoCompleto() {
        Comensal comensal = nuevoComensal("Juan", "3001234567");

        ComensalDto dto = ComensalDto.desde(comensal, true);

        assertThat(dto.telefono()).isEqualTo("3001234567");
    }

    @Test
    void meseroVeElTelefonoEnmascaradoSalvoLosUltimosDosDigitos() {
        Comensal comensal = nuevoComensal("Juan", "3001234567");

        ComensalDto dto = ComensalDto.desde(comensal, false);

        assertThat(dto.telefono()).isEqualTo("••••••••67");
        assertThat(dto.telefono()).hasSize("3001234567".length());
    }

    @Test
    void meseroNuncaVeElNombreEnmascarado() {
        Comensal comensal = nuevoComensal("Juan", "3001234567");

        ComensalDto dto = ComensalDto.desde(comensal, false);

        assertThat(dto.nombre()).isEqualTo("Juan");
    }

    @Test
    void unTelefonoNuloNoRompeElEnmascaradoParaMesero() {
        Comensal comensal = nuevoComensal("Sin Telefono", null);

        ComensalDto dto = ComensalDto.desde(comensal, false);

        assertThat(dto.telefono()).isNull();
    }

    @Test
    void unTelefonoMasCortoQueLosDigitosVisiblesSeEnmascaraPorCompleto() {
        Comensal comensal = nuevoComensal("Corto", "5");

        ComensalDto dto = ComensalDto.desde(comensal, false);

        assertThat(dto.telefono()).isEqualTo("•");
    }

    private Comensal nuevoComensal(String nombre, String telefono) {
        Comensal comensal = new Comensal();
        comensal.setNombre(nombre);
        comensal.setTelefono(telefono);
        return comensal;
    }
}
