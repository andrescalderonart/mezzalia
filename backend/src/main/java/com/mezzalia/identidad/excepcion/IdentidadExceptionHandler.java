package com.mezzalia.identidad.excepcion;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Handler separado del GlobalExceptionHandler de `common` a propósito:
 * `common` es el módulo del que todos los demás dependen (§12 de
 * documentacion-tecnica.md), nunca al revés. Si estas excepciones se
 * manejaran en common.excepcion.GlobalExceptionHandler, common pasaría a
 * depender de identidad, invirtiendo la capa y abriendo la puerta a
 * dependencias circulares el día que otro módulo necesite lo mismo (§11,
 * nota sobre ArchUnit). El pequeño duplicado del helper `respuesta(...)` es
 * el costo aceptado de mantener esa regla.
 */
@RestControllerAdvice
public class IdentidadExceptionHandler {

    @ExceptionHandler(AuthExceptions.CredencialesInvalidasException.class)
    public ResponseEntity<Map<String, Object>> handleCredencialesInvalidas(AuthExceptions.CredencialesInvalidasException ex) {
        return respuesta(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AuthExceptions.CuentaInactivaException.class)
    public ResponseEntity<Map<String, Object>> handleCuentaInactiva(AuthExceptions.CuentaInactivaException ex) {
        return respuesta(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(AuthExceptions.TokenInvalidoException.class)
    public ResponseEntity<Map<String, Object>> handleTokenInvalido(AuthExceptions.TokenInvalidoException ex) {
        return respuesta(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AuthExceptions.RefreshTokenInvalidoException.class)
    public ResponseEntity<Map<String, Object>> handleRefreshTokenInvalido(AuthExceptions.RefreshTokenInvalidoException ex) {
        return respuesta(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AuthExceptions.DemasiadosIntentosException.class)
    public ResponseEntity<Map<String, Object>> handleDemasiadosIntentos(AuthExceptions.DemasiadosIntentosException ex) {
        return respuesta(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
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
