package com.mezzalia.menu.dto;

import jakarta.validation.constraints.NotBlank;

public record CrearCategoriaRequest(@NotBlank String nombre, int orden) {}
