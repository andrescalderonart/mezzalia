package com.mezzalia.pedido.dto;

import com.mezzalia.pedido.PedidoItem;

import java.math.BigDecimal;

public record PedidoItemDto(
        Long itemMenuId,
        String nombreItem,
        BigDecimal precioUnitario,
        int cantidad,
        BigDecimal subtotal,
        java.util.List<String> sinIngredientes
) {
    public static PedidoItemDto desde(PedidoItem item) {
        return new PedidoItemDto(
                item.getItemMenuId(), item.getNombreItem(), item.getPrecioUnitario(),
                item.getCantidad(), item.getSubtotal(),
                item.getSinIngredientes() == null || item.getSinIngredientes().isBlank()
                        ? java.util.List.of()
                        : java.util.List.of(item.getSinIngredientes().split("\n")));
    }
}
