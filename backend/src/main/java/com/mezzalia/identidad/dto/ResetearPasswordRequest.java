package com.mezzalia.identidad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetearPasswordRequest(
        @NotBlank @Size(min = 8, max = 100) String passwordNueva
) {}
