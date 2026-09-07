package com.mezzalia.turno.dto;

import java.time.LocalDate;

/**
 * Una fila del reporte de TurnoService.horasPorDiaDe: `horas` es la suma
 * de todos los turnos de ese usuario cuyo INICIO cae en `fecha` (un turno
 * que cruza medianoche se atribuye ENTERO al dia en que empezo, nunca se
 * parte -- ver el javadoc de clase de TurnoService). `turnoAbiertoIncluido`
 * es true cuando ese total incluye un turno todavia en curso (fin == null)
 * -- el numero de ese dia sigue subiendo mientras el turno siga abierto, a
 * diferencia de los demas dias, que ya son definitivos; el frontend lo usa
 * para mostrar "(en curso)" en vez de dar a entender que ya es un total
 * cerrado.
 */
public record HorasPorDiaDto(LocalDate fecha, double horas, boolean turnoAbiertoIncluido) {
}
