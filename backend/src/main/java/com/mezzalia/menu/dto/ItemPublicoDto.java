package com.mezzalia.menu.dto;

import com.mezzalia.menu.ItemMenu;

import java.math.BigDecimal;

/**
 * DTO separado de ItemMenuDto (el de /api/admin) a propósito: son contratos
 * distintos aunque hoy tengan casi los mismos campos. Si mañana ItemMenuDto
 * crece con algo interno (costo, margen, notas del proveedor), este no lo
 * hereda por accidente -- un endpoint público nunca debería depender
 * silenciosamente de la forma de un DTO pensado para el panel autenticado.
 *
 * modeloGlbUrl/modeloUsdzUrl (2026-09-11): se exponen igual que imagenUrl
 * -- son URLs de un archivo público, no hay dato sensible que filtrar acá
 * (a diferencia de, por ejemplo, un costo interno del plato).
 */
public record ItemPublicoDto(
        Long id,
        String nombre,
        String descripcion,
        BigDecimal precio,
        String imagenUrl,
        String modeloGlbUrl,
        String modeloUsdzUrl,
        boolean disponible,
        com.mezzalia.menu.PresentacionIngredientes presentacionIngredientes,
        java.util.List<IngredientePublicoDto> ingredientes
) {
    public static ItemPublicoDto desde(ItemMenu item) {
        return desde(item, java.util.List.of());
    }

    public static ItemPublicoDto desde(ItemMenu item, java.util.List<IngredientePublicoDto> ingredientes) {
        return new ItemPublicoDto(
                item.getId(), item.getNombre(), item.getDescripcion(),
                item.getPrecio(), item.getImagenUrl(), item.getModeloGlbUrl(), item.getModeloUsdzUrl(),
                item.isDisponible(), item.getPresentacionIngredientes(), ingredientes);
    }
}
