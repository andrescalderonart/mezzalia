package com.mezzalia.menu.dto;

import com.mezzalia.menu.CategoriaMenu;

public record CategoriaMenuDto(Long id, String nombre, int orden) {
    public static CategoriaMenuDto desde(CategoriaMenu categoria) {
        return new CategoriaMenuDto(categoria.getId(), categoria.getNombre(), categoria.getOrden());
    }
}
