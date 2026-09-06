package com.mezzalia.identidad.dto;

/**
 * RF-10 PR4: respuesta de POST /api/admin/sesion/sucursal -- a propósito
 * NO reutiliza TokensRespuesta (que trae refreshToken): elegir/cambiar de
 * sucursal de trabajo re-emite solo el access token, nunca toca el refresh
 * token que el cliente ya tiene (ver AuthService.seleccionarSucursal para
 * el porqué -- reutilizar la rotación de refresh acá invalidaría, sin
 * ningún beneficio real de seguridad, un token que el cliente todavía
 * necesita y no pidió renovar).
 */
public record AccessTokenRespuesta(String accessToken, long expiraEnSegundos) {}
