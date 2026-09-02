package com.mezzalia.pedido.excepcion;

import com.mezzalia.pedido.EstadoPedido;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public final class PedidoExceptions {

    private PedidoExceptions() {}

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class PedidoNoEncontradoException extends RuntimeException {
        public PedidoNoEncontradoException(Long id) {
            super("Pedido no encontrado con id: " + id);
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class PedidoSinItemsException extends RuntimeException {
        public PedidoSinItemsException() {
            super("El pedido debe tener al menos un item");
        }
    }

    /**
     * Ver el comentario largo en PedidoService.crearPedido: esto pasa solo
     * en la carrera real de dos requests casi simultaneos con la misma
     * clave de idempotencia -- el mensaje le dice al cliente exactamente
     * que hacer (reintentar el mismo POST), no es un error generico.
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    public static class PedidoDuplicadoException extends RuntimeException {
        public PedidoDuplicadoException() {
            super("Ya se esta procesando un pedido con esta clave de idempotencia. Intenta de nuevo.");
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class TransicionEstadoInvalidaException extends RuntimeException {
        public TransicionEstadoInvalidaException(EstadoPedido actual, EstadoPedido solicitado) {
            super("No se puede pasar un pedido de " + actual + " a " + solicitado);
        }
    }

    /**
     * Backstop de abuso del endpoint publico (sin auth) -- ver
     * PedidoService.crearPedido/LIMITE_PEDIDOS_POR_VENTANA. No es un error
     * del comensal en el sentido de RF-04/RF-05 (no confunde con
     * PedidoDuplicadoException, que es sobre la MISMA clave de
     * idempotencia); esto es sobre demasiados pedidos DISTINTOS en poco
     * tiempo desde la misma mesa.
     */
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public static class DemasiadosPedidosException extends RuntimeException {
        public DemasiadosPedidosException() {
            super("Se alcanzo el limite de pedidos para esta mesa en poco tiempo. Espera un momento e intenta de nuevo.");
        }
    }

    /** Mismo patron que menu/mesa: SUPER_ADMIN no tiene tenant_id propio. */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class SinTenantEnContextoException extends RuntimeException {
        public SinTenantEnContextoException() {
            super("Esta operacion requiere un usuario de restaurante autenticado (no SUPER_ADMIN)");
        }
    }
}
