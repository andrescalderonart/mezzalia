package com.mezzalia.pedido.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A proposito SIN campo de precio (RF-05): el precio de un pedido nunca
 * sale del cliente, siempre de ItemMenu.precio en el momento de crear el
 * pedido (ver PedidoService.crearPedido / MenuService.obtenerItemDisponibleParaPedido).
 * Esto no es "no confiar en el precio del cliente" como politica -- es que
 * estructuralmente no existe ningun precio del cliente que ignorar.
 */
public record ItemSolicitadoRequest(
        @NotNull Long itemMenuId,
        @Min(1) @Max(50) int cantidad,
        // "Sin cebolla" (2026-11-26): ids de ingredientes QUITABLES de este
        // plato. Opcional; null o vacio = el plato tal cual. Ver
        // MenuService.nombresDeIngredientesQuitables.
        @Size(max = 20) java.util.List<Long> sinIngredientes
) {
    /** El plato tal cual, sin quitar nada (la forma que ya usaba todo el codigo). */
    public ItemSolicitadoRequest(Long itemMenuId, int cantidad) {
        this(itemMenuId, cantidad, null);
    }
}
