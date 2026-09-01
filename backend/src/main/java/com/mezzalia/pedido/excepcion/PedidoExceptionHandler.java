package com.mezzalia.pedido.excepcion;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Handler separado de GlobalExceptionHandler (common), mismo motivo ya
 * documentado en menu/mesa: common no debe depender de un modulo hijo (§12).
 *
 * @Order(10) NO es decorativo -- ver el bug real de 500 en login/refresh que
 * motivo esta convencion (common.excepcion.GlobalExceptionHandler). Todo
 * @RestControllerAdvice nuevo de un modulo debe declarar un @Order explicito
 * menor que Ordered.LOWEST_PRECEDENCE.
 */
@Order(10)
@RestControllerAdvice
public class PedidoExceptionHandler {

    @ExceptionHandler(PedidoExceptions.PedidoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> handlePedidoNoEncontrado(PedidoExceptions.PedidoNoEncontradoException ex) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PedidoExceptions.PedidoSinItemsException.class)
    public ResponseEntity<Map<String, Object>> handlePedidoSinItems(PedidoExceptions.PedidoSinItemsException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(PedidoExceptions.PedidoDuplicadoException.class)
    public ResponseEntity<Map<String, Object>> handlePedidoDuplicado(PedidoExceptions.PedidoDuplicadoException ex) {
        return respuesta(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(PedidoExceptions.TransicionEstadoInvalidaException.class)
    public ResponseEntity<Map<String, Object>> handleTransicionInvalida(PedidoExceptions.TransicionEstadoInvalidaException ex) {
        return respuesta(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(PedidoExceptions.SinTenantEnContextoException.class)
    public ResponseEntity<Map<String, Object>> handleSinTenant(PedidoExceptions.SinTenantEnContextoException ex) {
        return respuesta(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> respuesta(HttpStatus status, String mensaje) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "mensaje", mensaje
        ));
    }
}
