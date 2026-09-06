package com.mezzalia.identidad.dto;

import com.mezzalia.identidad.RolUsuario;
import com.mezzalia.identidad.UsuarioInterno;

/** Nunca incluye passwordHash -- ni cifrado ni de ninguna otra forma: el
 * panel no tiene ninguna razón legítima para recibirlo, ver
 * UsuarioInternoAdminService.resetearPassword para el único camino de
 * cambiarlo. */
public record UsuarioInternoDto(Long id, String username, RolUsuario rol, boolean activo) {
    public static UsuarioInternoDto desde(UsuarioInterno usuario) {
        return new UsuarioInternoDto(usuario.getId(), usuario.getUsername(), usuario.getRol(), usuario.isActivo());
    }
}
