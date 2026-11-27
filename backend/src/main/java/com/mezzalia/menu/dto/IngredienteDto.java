package com.mezzalia.menu.dto;

import com.mezzalia.menu.IngredienteItem;

public record IngredienteDto(Long id, String nombre, String imagenUrl, int orden, boolean removible) {
    public static IngredienteDto desde(IngredienteItem i) {
        return new IngredienteDto(i.getId(), i.getNombre(), i.getImagenUrl(), i.getOrden(), i.isRemovible());
    }
}
