package com.mezzalia.menu.dto;

import com.mezzalia.menu.ItemMenu;

import java.math.BigDecimal;

/**
 * DTO separado de ItemMenuDto (el de /api/admin) a propósito: son contratos
 * distintos aunque hoy tengan casi los mismos campos. Si mañana ItemMenuDto
 * crece con algo interno (costo, margen, notas del proveedor), este no lo
 * hereda por accidente -- un endpoint público nunca debería depender
 * silenciosamente de la forma de un DTO pensado para el panel autenticado.
 */
public record ItemPublicoDto(
        Long id,
        String nombre,
        String descripcion,
        BigDecimal precio,
        String imagenUrl,
        boolean disponible
) {
    public static ItemPublicoDto desde(ItemMenu item) {
        return new ItemPublicoDto(
                item.getId(), item.getNombre(), item.getDescripcion(),
                item.getPrecio(), item.getImagenUrl(), item.isDisponible());
    }
}
