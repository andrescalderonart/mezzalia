package com.mezzalia.menu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ActualizarIngredienteRequest(@NotBlank @Size(max = 80) String nombre, boolean removible) {}
