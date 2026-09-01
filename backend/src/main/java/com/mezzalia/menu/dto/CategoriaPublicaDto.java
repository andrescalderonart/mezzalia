package com.mezzalia.menu.dto;

import com.mezzalia.menu.CategoriaMenu;

import java.util.List;

public record CategoriaPublicaDto(Long id, String nombre, int orden, List<ItemPublicoDto> items) {
    public static CategoriaPublicaDto desde(CategoriaMenu categoria, List<ItemPublicoDto> items) {
        return new CategoriaPublicaDto(categoria.getId(), categoria.getNombre(), categoria.getOrden(), items);
    }
}
