package com.mezzalia.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** removible null = true (lo normal es que se pueda quitar). */
public record CrearIngredienteRequest(@NotBlank @Size(max = 80) String nombre, Boolean removible) {}
