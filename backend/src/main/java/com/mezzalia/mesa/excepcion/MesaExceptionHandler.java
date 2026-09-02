package com.mezzalia.mesa.excepcion;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Handler separado de GlobalExceptionHandler (common), mismo motivo ya
 * documentado en menu.excepcion.MenuExceptionHandler / identidad's
 * IdentidadExceptionHandler: common no debe depender de un módulo hijo (§12).
 *
 * @Order(10) NO es decorativo -- ver el bug real de 500 en login/refresh que
 * motivó esta convención (common.excepcion.GlobalExceptionHandler). Todo
 * @RestControllerAdvice nuevo de un módulo debe declarar un @Order explícito
 * menor que Ordered.LOWEST_PRECEDENCE.
 */
@Order(10)
@RestControllerAdvice
public class MesaExceptionHandler {

    @ExceptionHandler(MesaExceptions.MesaNoEncontradaException.class)
    public ResponseEntity<Map<String, Object>> handleMesaNoEncontrada(MesaExceptions.MesaNoEncontradaException ex) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MesaExceptions.SesionMesaInvalidaException.class)
    public ResponseEntity<Map<String, Object>> handleSesionInvalida(MesaExceptions.SesionMesaInvalidaException ex) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MesaExceptions.RestauranteNoDisponibleException.class)
    public ResponseEntity<Map<String, Object>> handleRestauranteNoDisponible(MesaExceptions.RestauranteNoDisponibleException ex) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MesaExceptions.SinTenantEnContextoException.class)
    public ResponseEntity<Map<String, Object>> handleSinTenant(MesaExceptions.SinTenantEnContextoException ex) {
        return respuesta(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(MesaExceptions.CodigoMesaDuplicadoException.class)
    public ResponseEntity<Map<String, Object>> handleCodigoDuplicado(MesaExceptions.CodigoMesaDuplicadoException ex) {
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
