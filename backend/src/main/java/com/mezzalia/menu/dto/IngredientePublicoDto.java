package com.mezzalia.menu.dto;

import com.mezzalia.menu.IngredienteItem;

public record IngredientePublicoDto(Long id, String nombre, String imagenUrl, boolean removible) {
    public static IngredientePublicoDto desde(IngredienteItem i) {
        return new IngredientePublicoDto(i.getId(), i.getNombre(), i.getImagenUrl(), i.isRemovible());
    }
}
