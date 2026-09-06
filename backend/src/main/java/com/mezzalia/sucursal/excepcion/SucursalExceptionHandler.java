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

    /**
     * A diferencia de las demas excepciones de este handler, esta SI trae
     * un campo "codigo" ademas del "mensaje" para humanos: es la UNICA
     * excepcion de todo el proyecto pensada para ser resuelta por el
     * propio cliente sin intervencion humana (ver el javadoc de
     * AuthService.misSucursalesDisponibles/seleccionarSucursal -- "el
     * cliente puede resolverlo el mismo llamando a POST
     * /api/admin/sesion/sucursal"). El frontend (ver useApiJson en
     * AuthContext.jsx) la detecta por este "codigo", nunca por el texto de
     * "mensaje" -- ese es solo para mostrar en pantalla si hace falta, y
     * matchear control de flujo contra un string pensado para humanos se
     * rompe en silencio el dia que alguien lo reescriba o lo traduzca.
     */
    @ExceptionHandler(SucursalExceptions.SucursalNoSeleccionadaException.class)
    public ResponseEntity<Map<String, Object>> handleSucursalNoSeleccionada(SucursalExceptions.SucursalNoSeleccionadaException ex) {
        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", HttpStatus.CONFLICT.value(),
                "error", HttpStatus.CONFLICT.getReasonPhrase(),
                "mensaje", ex.getMessage(),
                "codigo", "SUCURSAL_NO_SELECCIONADA"
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
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
