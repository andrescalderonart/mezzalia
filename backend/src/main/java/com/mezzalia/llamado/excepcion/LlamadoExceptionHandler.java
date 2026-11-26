package com.mezzalia.llamado.excepcion;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/** Mismo formato y mismo @Order explicito que PedidoExceptionHandler (ver su javadoc). */
@Order(10)
@RestControllerAdvice
public class LlamadoExceptionHandler {

    @ExceptionHandler(LlamadoExceptions.LlamadoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> handleNoEncontrado(LlamadoExceptions.LlamadoNoEncontradoException ex) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(LlamadoExceptions.DemasiadosLlamadosException.class)
    public ResponseEntity<Map<String, Object>> handleDemasiados(LlamadoExceptions.DemasiadosLlamadosException ex) {
        return respuesta(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(LlamadoExceptions.SinTenantEnContextoException.class)
    public ResponseEntity<Map<String, Object>> handleSinTenant(LlamadoExceptions.SinTenantEnContextoException ex) {
        return respuesta(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> respuesta(HttpStatus status, String mensaje) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "mensaje", mensaje));
    }
}
