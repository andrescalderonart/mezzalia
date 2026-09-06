package com.mezzalia.sucursal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearSucursalRequest(
        @NotBlank @Size(max = 150) String nombre,
        @Size(max = 255) String direccion
) {}
