package com.mezzalia.turno.dto;

import java.time.LocalDateTime;

/**
 * `desde`/`sucursalId` solo tienen sentido cuando `enTurno` es true -- ver
 * TurnoService. Se devuelve null en ambos en vez de omitir el campo para
 * que el frontend no tenga que distinguir "ausente" de "null", mismo
 * criterio que el resto de los DTOs de este proyecto (ver AccessTokenRespuesta).
 */
public record EstadoTurnoDto(boolean enTurno, LocalDateTime desde, Long sucursalId) {
}
