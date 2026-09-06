package com.mezzalia.sucursal.excepcion;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Handler separado de GlobalExceptionHandler (common), mismo motivo que
 * MenuExceptionHandler/MesaExceptionHandler: common no debe depender de
 * sucursal (documentacion-tecnica.md §12). @Order(10) obligatorio -- ver el
 * comentario largo en MenuExceptionHandler sobre el bug real de 500
 * genérico que este @Order explícito evita.
 */
@Order(10)
@RestControllerAdvice
public class SucursalExceptionHandler {

    @ExceptionHandler(SucursalExceptions.SucursalNoEncontradaException.class)
    public ResponseEntity<Map<String, Object>> handleSucursalNoEncontrada(SucursalExceptions.SucursalNoEncontradaException ex) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(SucursalExceptions.SinTenantEnContextoException.class)
    public ResponseEntity<Map<String, Object>> handleSinTenant(SucursalExceptions.SinTenantEnContextoException ex) {
        return respuesta(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(SucursalExceptions.UltimaSucursalActivaException.class)
    public ResponseEntity<Map<String, Object>> handleUltimaSucursalActiva(SucursalExceptions.UltimaSucursalActivaException ex) {
        return respuesta(HttpStatus.CONFLICT, ex.getMessage());
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
