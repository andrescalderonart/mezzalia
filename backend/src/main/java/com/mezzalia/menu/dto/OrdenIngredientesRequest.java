package com.mezzalia.menu.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Los ids de los ingredientes del plato en el orden nuevo (de arriba hacia abajo). */
public record OrdenIngredientesRequest(@NotNull List<Long> ids) {}
