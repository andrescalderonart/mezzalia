package com.mezzalia.identidad.dto;

/**
 * requiereSeleccionSucursal (RF-10 PR4): true cuando el usuario que acaba
 * de loguearse/refrescar es MESERO -- señal para el frontend de que debe
 * llamar a GET /api/admin/sesion/sucursales-disponibles y luego a
 * POST /api/admin/sesion/sucursal ANTES de dejarlo entrar a Mesas/Pedidos
 * (esos endpoints le van a devolver 409 SucursalNoSeleccionadaException si
 * no lo hace, ver sucursal.excepcion.SucursalExceptions). Siempre true de
 * nuevo tras CADA /api/auth/refresh para un MESERO, incluso si ya había
 * elegido sucursal antes de que el access token expirara -- ver la
 * limitación documentada en AuthService.emitirPar: el frontend debe volver
 * a seleccionar (puede hacerlo con el mismo id que ya tenía guardado, sin
 * volver a preguntarle al usuario) inmediatamente después de cada refresh.
 */
public record TokensRespuesta(
        String accessToken,
        String refreshToken,
        long expiraEnSegundos,
        boolean requiereSeleccionSucursal
) {}
