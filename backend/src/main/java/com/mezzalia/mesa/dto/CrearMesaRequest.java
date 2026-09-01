package com.mezzalia.mesa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearMesaRequest(
        @NotBlank @Size(max = 50) String codigo
) {}
