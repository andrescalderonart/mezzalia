package com.mezzalia.common.excepcion;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.time.Instant;
import java.util.Map;

// @Order explicito y al final a proposito: este es el handler generico de
// ultimo recurso (catch-all Exception.class). Sin esto, Spring puede elegir
// este bean antes que el @RestControllerAdvice especifico de otro modulo
// (p.ej. identidad.excepcion.IdentidadExceptionHandler) simplemente por el
// orden en que los descubre el classpath scanning -- no por especificidad --
// y entonces excepciones de negocio bien tipadas terminan devolviendo 500
// generico en vez de su status real. Ver docs/documentacion-tecnica.md §12:
// cualquier @RestControllerAdvice nuevo de un modulo debe declarar un
// @Order explicito MENOR que Ordered.LOWEST_PRECEDENCE (ej. @Order(10)) para
// garantizar que gane sobre este handler generico.
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainExceptions.TenantNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> handleTenantNoEncontrado(DomainExceptions.TenantNoEncontradoException ex) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DomainExceptions.TenantYaExisteException.class)
    public ResponseEntity<Map<String, Object>> handleTenantYaExiste(DomainExceptions.TenantYaExisteException ex) {
        return respuesta(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DomainExceptions.TenantSuspendidoException.class)
    public ResponseEntity<Map<String, Object>> handleTenantSuspendido(DomainExceptions.TenantSuspendidoException ex) {
        return respuesta(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(DomainExceptions.ColorHexInvalidoException.class)
    public ResponseEntity<Map<String, Object>> handleColorHexInvalido(DomainExceptions.ColorHexInvalidoException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenerico(Exception ex) {
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
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