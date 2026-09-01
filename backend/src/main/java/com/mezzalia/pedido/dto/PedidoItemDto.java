package com.mezzalia.pedido.dto;

import com.mezzalia.pedido.PedidoItem;

import java.math.BigDecimal;

public record PedidoItemDto(
        Long itemMenuId,
        String nombreItem,
        BigDecimal precioUnitario,
        int cantidad,
        BigDecimal subtotal
) {
    public static PedidoItemDto desde(PedidoItem item) {
        return new PedidoItemDto(
                item.getItemMenuId(), item.getNombreItem(), item.getPrecioUnitario(),
                item.getCantidad(), item.getSubtotal());
    }
}
