package com.mezzalia.reporte.dto;

import java.util.List;

public record PlatoQuitadosDto(
        Long itemMenuId,
        String nombre,
        int unidadesVendidas,
        int unidadesPersonalizadas,
        double porcentajePersonalizado,
        List<IngredienteQuitadoDto> ingredientes) {}
