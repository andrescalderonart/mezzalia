package com.mezzalia.sucursal.dto;

import com.mezzalia.sucursal.Sucursal;

public record SucursalDto(Long id, String nombre, String direccion, boolean activa) {
    public static SucursalDto desde(Sucursal sucursal) {
        return new SucursalDto(sucursal.getId(), sucursal.getNombre(), sucursal.getDireccion(), sucursal.isActiva());
    }
}
