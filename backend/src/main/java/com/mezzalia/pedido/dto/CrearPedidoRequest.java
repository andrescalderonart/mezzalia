package com.mezzalia.pedido.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CrearPedidoRequest(
        @NotBlank @Size(max = 36) String claveIdempotencia,
        @NotEmpty @Valid List<ItemSolicitadoRequest> items,
        @Size(max = 500) String notas,
        @Valid ComensalRequest comensal
) {}
