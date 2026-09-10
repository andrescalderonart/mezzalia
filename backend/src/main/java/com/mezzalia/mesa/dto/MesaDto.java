package com.mezzalia.mesa.dto;

import com.mezzalia.mesa.Mesa;

public record MesaDto(Long id, Long sucursalId, String codigo, boolean activa, SesionMesaDto sesionActiva) {
    public static MesaDto desde(Mesa mesa) {
        return desde(mesa, null);
    }

    public static MesaDto desde(Mesa mesa, SesionMesaDto sesionActiva) {
        return new MesaDto(mesa.getId(), mesa.getSucursalId(), mesa.getCodigo(), mesa.isActiva(), sesionActiva);
    }
}
