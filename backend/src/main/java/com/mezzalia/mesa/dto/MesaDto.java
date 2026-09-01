package com.mezzalia.mesa.dto;

import com.mezzalia.mesa.Mesa;

public record MesaDto(Long id, String codigo, boolean activa) {
    public static MesaDto desde(Mesa mesa) {
        return new MesaDto(mesa.getId(), mesa.getCodigo(), mesa.isActiva());
    }
}
