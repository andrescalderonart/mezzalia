package com.mezzalia.menu.dto;

import com.mezzalia.menu.PresentacionIngredientes;
import jakarta.validation.constraints.NotNull;

public record PresentacionRequest(@NotNull PresentacionIngredientes presentacion) {}
