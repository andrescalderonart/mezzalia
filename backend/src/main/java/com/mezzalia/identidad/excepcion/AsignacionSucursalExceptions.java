package com.mezzalia.identidad.excepcion;

import com.mezzalia.identidad.RolUsuario;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * RF-10 PR4: ver identidad.servicio.AsignacionSucursalService. "Ya
 * asignado"/"no estaba asignado" NO tienen excepcion propia a proposito --
 * asignar()/desasignar() son idempotentes (mismo criterio que
 * UsuarioInternoExceptions.NoPuedeDesactivarseASiMismoException y vecinas:
 * ver el javadoc de esos metodos), asi que esos dos casos nunca llegan a
 * lanzar nada.
 */
public final class AsignacionSucursalExceptions {

    private AsignacionSucursalExceptions() {}

    /**
     * Un ADMIN_RESTAURANTE nunca necesita una fila en usuario_sucursales:
     * ya ve todas las sucursales de su tenant sin restriccion (ver
     * AuthService.misSucursalesDisponibles) -- asignarle una seria dato
     * muerto que ningun camino de la aplicacion lee, y silenciosamente
     * aceptarlo solo generaria confusion el dia que alguien audite esta
     * tabla preguntandose por que un admin aparece "atado" a una sucursal
     * cuando en realidad nunca lo estuvo.
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class RolNoPermitidoParaAsignacionException extends RuntimeException {
        public RolNoPermitidoParaAsignacionException(RolUsuario rol) {
            super("Solo se pueden asignar sucursales a usuarios con rol MESERO (rol recibido: " + rol + ")");
        }
    }
}
