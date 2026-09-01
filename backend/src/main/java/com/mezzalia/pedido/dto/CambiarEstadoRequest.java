package com.mezzalia.pedido.dto;

import com.mezzalia.pedido.EstadoPedido;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoRequest(@NotNull EstadoPedido estado) {}
