package com.mezzalia.pedido.dto;

import com.mezzalia.pedido.Comensal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * documentacion-tecnica.md §10: "un mesero no necesita ver el telefono
 * completo, un admin_restaurante si". Estos tests cubren solo la logica de
 * enmascarado -- el "quien es ADMIN_RESTAURANTE" vive en
 * PedidoAdminController y se prueba aparte en PedidoAdminControllerTest.
 */
class ComensalDtoTest {

    private Comensal comensalCon(String telefono) {
        Comensal comensal = new Comensal();
        comensal.setId(42L);
        comensal.setNombre("Juan Perez");
        comensal.setTelefono(telefono);
        return comensal;
    }

    @Test
    void telefonoCompletoDevuelveElNumeroSinEnmascarar() {
        ComensalDto dto = ComensalDto.desde(comensalCon("3001234567"), true);

        assertThat(dto.telefono()).isEqualTo("3001234567");
        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.nombre()).isEqualTo("Juan Perez");
    }

    @Test
    void telefonoNoCompletoDejaSoloLosUltimosCuatroDigitosVisibles() {
        ComensalDto dto = ComensalDto.desde(comensalCon("3001234567"), false);

        assertThat(dto.telefono()).isEqualTo("***4567");
    }

    @Test
    void enmascaradoNoDelataLaLongitudRealDelNumero() {
        ComensalDto corto = ComensalDto.desde(comensalCon("1234567"), false);
        ComensalDto largo = ComensalDto.desde(comensalCon("+57 300 123 4567"), false);

        assertThat(corto.telefono()).hasSameSizeAs(largo.telefono());
    }

    @Test
    void telefonoDeCuatroCaracteresOMenosQuedaCompletoDetrasDeLaMascara() {
        ComensalDto dto = ComensalDto.desde(comensalCon("123"), false);

        assertThat(dto.telefono()).isEqualTo("***123");
    }

    @Test
    void telefonoNuloNoSeEnmascara() {
        ComensalDto dto = ComensalDto.desde(comensalCon(null), false);

        assertThat(dto.telefono()).isNull();
    }

    @Test
    void telefonoEnBlancoNoSeEnmascara() {
        ComensalDto dto = ComensalDto.desde(comensalCon("   "), false);

        assertThat(dto.telefono()).isEqualTo("   ");
    }
}
