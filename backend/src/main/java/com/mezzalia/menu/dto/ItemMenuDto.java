package com.mezzalia.menu.dto;

import com.mezzalia.menu.ItemMenu;

import java.math.BigDecimal;

public record ItemMenuDto(
        Long id,
        Long categoriaId,
        String nombre,
        String descripcion,
        BigDecimal precio,
        String imagenUrl,
        String modeloGlbUrl,
        String modeloUsdzUrl,
        boolean disponible,
        com.mezzalia.menu.PresentacionIngredientes presentacionIngredientes
) {
    public static ItemMenuDto desde(ItemMenu item) {
        return new ItemMenuDto(
                item.getId(), item.getCategoriaId(), item.getNombre(), item.getDescripcion(),
                item.getPrecio(), item.getImagenUrl(), item.getModeloGlbUrl(), item.getModeloUsdzUrl(),
                item.isDisponible(), item.getPresentacionIngredientes());
    }
}
