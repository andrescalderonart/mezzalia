package com.mezzalia.reporte.dto;

import java.util.List;

/**
 * "Que quitan tus clientes" (2026-11-27). Unidades, no lineas: un pedido de
 * 3 hamburguesas sin cebolla cuenta 3.
 */
public record ReporteQuitadosDto(
        int dias,
        int unidadesVendidas,
        int unidadesPersonalizadas,
        double porcentajePersonalizado,
        List<IngredienteQuitadoDto> masQuitados,
        List<PlatoQuitadosDto> platos) {}
