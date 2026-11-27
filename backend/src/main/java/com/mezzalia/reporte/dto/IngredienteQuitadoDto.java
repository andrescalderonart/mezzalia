package com.mezzalia.reporte.dto;

/**
 * Un ingrediente y cuantas unidades del plato lo pidieron sin el.
 * `porcentaje` = unidades sin el / unidades vendidas del plato (0-100, 1 decimal).
 * `plato` solo viene en el ranking global (en el detalle por plato es obvio).
 */
public record IngredienteQuitadoDto(String nombre, String plato, int unidades, double porcentaje) {}
