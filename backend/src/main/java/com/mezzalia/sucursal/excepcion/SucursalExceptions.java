package com.mezzalia.sucursal.excepcion;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public final class SucursalExceptions {

    private SucursalExceptions() {}

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class SucursalNoEncontradaException extends RuntimeException {
        public SucursalNoEncontradaException(Long id) {
            super("Sucursal no encontrada con id: " + id);
        }
    }

    /**
     * SUPER_ADMIN no tiene tenant_id propio -- mismo criterio que el resto
     * del sistema (MenuExceptions/MesaExceptions/PedidoExceptions/
     * UsuarioInternoExceptions.SinTenantEnContextoException): 403
     * explícito, no un 500 genérico.
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class SinTenantEnContextoException extends RuntimeException {
        public SinTenantEnContextoException() {
            super("Esta operación requiere un usuario de restaurante autenticado (no SUPER_ADMIN)");
        }
    }

    /**
     * Sin este guardia, un ADMIN_RESTAURANTE que desactiva por error (o a
     * propósito, sin darse cuenta de la consecuencia) su única sucursal
     * activa deja al tenant sin forma de crear mesas nuevas -- MesaService
     * exige un sucursalId activo (ver V16__mesas_sucursal_id.sql y
     * MesaService.crearMesa) y no hay ningún otro camino en la UI para
     * salir de ese estado salvo reactivarla a mano vía la propia API. Se
     * modela como 409: es un conflicto con el estado actual, no un dato
     * inválido en la petición.
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    public static class UltimaSucursalActivaException extends RuntimeException {
        public UltimaSucursalActivaException() {
            super("No se puede desactivar la última sucursal activa del restaurante: no quedaría ninguna sucursal disponible para crear mesas nuevas");
        }
    }

    /**
     * RF-10 PR4 (docs/propuesta-multisucursal-rf10.md §4): un MESERO SIEMPRE
     * debe tener una sucursal seleccionada antes de listar u operar mesas/
     * pedidos (ver sucursal.SucursalContext.requiereSeleccion() y el
     * "sucursalActual()" de MesaService/PedidoService) -- esto se lanza
     * cuando su JWT no trae el claim sucursalId todavia (nunca llamo a
     * SesionController.seleccionarSucursal tras el login, o su token viene
     * de un /api/auth/refresh reciente, que por diseño lo pierde -- ver
     * AuthService.emitirPar). Se modela 409, no 403: no es un problema de
     * permisos, es que la operación necesita un dato de contexto (que
     * sucursal) que todavía no fue provisto -- el cliente puede resolverlo
     * el mismo llamando a POST /api/admin/sesion/sucursal, no necesita
     * intervención de otro usuario.
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    public static class SucursalNoSeleccionadaException extends RuntimeException {
        public SucursalNoSeleccionadaException() {
            super("Este usuario debe seleccionar una sucursal de trabajo antes de continuar (ver POST /api/admin/sesion/sucursal)");
        }
    }
}
