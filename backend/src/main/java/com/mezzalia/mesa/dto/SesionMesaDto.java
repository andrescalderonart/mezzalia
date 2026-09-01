package com.mezzalia.mesa.dto;

import com.mezzalia.mesa.SesionMesa;

import java.time.LocalDateTime;

public record SesionMesaDto(String token, LocalDateTime expiraEn) {
    public static SesionMesaDto desde(SesionMesa sesion) {
        return new SesionMesaDto(sesion.getToken(), sesion.getExpiraEn());
    }
}
