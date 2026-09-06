package com.mezzalia.identidad.dto;

import jakarta.validation.constraints.NotNull;

public record SeleccionarSucursalRequest(@NotNull Long sucursalId) {}
