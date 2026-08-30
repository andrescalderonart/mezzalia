package com.mezzalia.common.excepcion;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public final class DomainExceptions {

    private DomainExceptions() {}

    // ── Tenant ──────────────────────────────────────────────────────────────

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class TenantNoEncontradoException extends RuntimeException {
        public TenantNoEncontradoException(Long id) {
            super("Tenant no encontrado con id: " + id);
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class TenantYaExisteException extends RuntimeException {
        public TenantYaExisteException(String nombre) {
            super("Ya existe un tenant con el nombre: " + nombre);
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class TenantSuspendidoException extends RuntimeException {
        public TenantSuspendidoException(Long id) {
            super("El tenant con id " + id + " está suspendido");
        }
    }

    // ── Validación de dominio ────────────────────────────────────────────────

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class ColorHexInvalidoException extends RuntimeException {
        public ColorHexInvalidoException(String valor) {
            super("El color '" + valor + "' no es un valor hexadecimal válido (#RRGGBB)");
        }
    }
}