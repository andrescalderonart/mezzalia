package com.mezzalia.menu.dto;

/**
 * Sin @NotNull en los campos a propósito: null+null es una solicitud
 * válida (quitarle el modelo 3D al item). La regla real -- las dos URLs o
 * ninguna, nunca una sola -- la valida MenuService.actualizarModelo3d(),
 * no este DTO, para no duplicar la lógica de negocio en dos lugares que se
 * puedan desincronizar.
 */
public record ActualizarModelo3dRequest(String modeloGlbUrl, String modeloUsdzUrl) {}
