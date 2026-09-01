package com.mezzalia.menu.excepcion;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.Map;

/**
 * Handler separado de GlobalExceptionHandler (common), misma razón que
 * IdentidadExceptionHandler: common no debe depender de menu (§12).
 *
 * @Order(10) NO es decorativo: ver el commit que arregló el 500 en
 * login/refresh (bug real detectado en producción de este mismo proyecto).
 * Sin un @Order explícito menor que el de GlobalExceptionHandler
 * (Ordered.LOWEST_PRECEDENCE), su catch-all Exception.class intercepta estas
 * excepciones ANTES que este handler, y todas terminan devolviendo 500
 * genérico en vez de su status real. Cualquier @RestControllerAdvice nuevo
 * de un módulo debe repetir este mismo @Order.
 */
@Order(10)
@RestControllerAdvice
public class MenuExceptionHandler {

    @ExceptionHandler(MenuExceptions.CategoriaNoEncontradaException.class)
    public ResponseEntity<Map<String, Object>> handleCategoriaNoEncontrada(MenuExceptions.CategoriaNoEncontradaException ex) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MenuExceptions.ItemMenuNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> handleItemNoEncontrado(MenuExceptions.ItemMenuNoEncontradoException ex) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MenuExceptions.PrecioInvalidoException.class)
    public ResponseEntity<Map<String, Object>> handlePrecioInvalido(MenuExceptions.PrecioInvalidoException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MenuExceptions.ItemNoDisponibleException.class)
    public ResponseEntity<Map<String, Object>> handleItemNoDisponible(MenuExceptions.ItemNoDisponibleException ex) {
        return respuesta(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MenuExceptions.ArchivoInvalidoException.class)
    public ResponseEntity<Map<String, Object>> handleArchivoInvalido(MenuExceptions.ArchivoInvalidoException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MenuExceptions.SinTenantEnContextoException.class)
    public ResponseEntity<Map<String, Object>> handleSinTenant(MenuExceptions.SinTenantEnContextoException ex) {
        return respuesta(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleArchivoDemasiadoGrande(MaxUploadSizeExceededException ex) {
        return respuesta(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo supera el tamaño máximo permitido");
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
