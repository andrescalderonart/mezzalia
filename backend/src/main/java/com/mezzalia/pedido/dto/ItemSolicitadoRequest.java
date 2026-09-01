package com.mezzalia.pedido.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * A proposito SIN campo de precio (RF-05): el precio de un pedido nunca
 * sale del cliente, siempre de ItemMenu.precio en el momento de crear el
 * pedido (ver PedidoService.crearPedido / MenuService.obtenerItemDisponibleParaPedido).
 * Esto no es "no confiar en el precio del cliente" como politica -- es que
 * estructuralmente no existe ningun precio del cliente que ignorar.
 */
public record ItemSolicitadoRequest(
        @NotNull Long itemMenuId,
        @Min(1) @Max(50) int cantidad
) {}
