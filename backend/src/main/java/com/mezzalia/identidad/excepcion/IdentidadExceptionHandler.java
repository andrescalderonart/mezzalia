package com.mezzalia.identidad.excepcion;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.core.annotation.Order;

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
 *
 * @Order(10) a proposito: debe resolverse ANTES que el catch-all
 * Exception.class de common.excepcion.GlobalExceptionHandler. Sin un orden
 * explicito, Spring no compara especificidad entre distintos beans
 * @RestControllerAdvice -- toma el primero que descubre con algun match --
 * y eso hacia que CredencialesInvalidasException, RefreshTokenInvalidoException
 * y DemasiadosIntentosException terminaran devolviendo 500 generico en vez
 * de 401/429. Ese fue el bug real detras del 500 en refresh y en rate limiting.
 */
@Order(10)
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

    @ExceptionHandler(AuthExceptions.UsuarioAutenticadoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> handleUsuarioAutenticadoNoEncontrado(AuthExceptions.UsuarioAutenticadoNoEncontradoException ex) {
        return respuesta(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    // ── CRUD de usuarios internos (RF-09/§2, ver UsuarioInternoAdminService) ──

    @ExceptionHandler(UsuarioInternoExceptions.UsuarioYaExisteException.class)
    public ResponseEntity<Map<String, Object>> handleUsuarioYaExiste(UsuarioInternoExceptions.UsuarioYaExisteException ex) {
        return respuesta(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(UsuarioInternoExceptions.UsuarioNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> handleUsuarioNoEncontrado(UsuarioInternoExceptions.UsuarioNoEncontradoException ex) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(UsuarioInternoExceptions.RolNoPermitidoException.class)
    public ResponseEntity<Map<String, Object>> handleRolNoPermitido(UsuarioInternoExceptions.RolNoPermitidoException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(UsuarioInternoExceptions.SinTenantEnContextoException.class)
    public ResponseEntity<Map<String, Object>> handleSinTenantUsuarios(UsuarioInternoExceptions.SinTenantEnContextoException ex) {
        return respuesta(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(UsuarioInternoExceptions.NoPuedeDesactivarseASiMismoException.class)
    public ResponseEntity<Map<String, Object>> handleNoPuedeDesactivarseASiMismo(UsuarioInternoExceptions.NoPuedeDesactivarseASiMismoException ex) {
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
