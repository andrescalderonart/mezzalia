package com.mezzalia.turno.excepcion;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public final class TurnoExceptions {

    private TurnoExceptions() {}

    /**
     * Se modela 409 (conflicto con el estado actual), no 400: la peticion
     * en si es valida, lo que pasa es que ya existe un turno abierto para
     * este usuario -- mismo criterio que SucursalNoSeleccionadaException
     * en el modulo sucursal.
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    public static class TurnoYaAbiertoException extends RuntimeException {
        public TurnoYaAbiertoException() {
            super("Ya hay un turno abierto para este usuario -- marca salida antes de poder marcar entrada de nuevo");
        }
    }

    /**
     * Se lanza tanto si el usuario nunca marco entrada como si su turno ya
     * fue cerrado solo por TurnoService.cerrarSiVencido (paso de mas de
     * TOPE_HORAS_TURNO horas sin marcar salida) -- el mensaje avisa
     * explicitamente de la segunda posibilidad para que no parezca un bug
     * si alguien intenta marcar salida de un turno que el sistema ya dio
     * por cerrado.
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    public static class TurnoNoAbiertoException extends RuntimeException {
        public TurnoNoAbiertoException() {
            super("No hay ningun turno abierto para marcar la salida (puede que ya se haya cerrado solo por pasar el tope de horas seguidas)");
        }
    }
}
