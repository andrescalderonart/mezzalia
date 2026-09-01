package com.mezzalia.mesa.excepcion;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public final class MesaExceptions {

    private MesaExceptions() {}

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class MesaNoEncontradaException extends RuntimeException {
        public MesaNoEncontradaException(Long id) {
            super("Mesa no encontrada con id: " + id);
        }
    }

    /**
     * Mismo mensaje genérico para "el token nunca existió" y "el token
     * expiró" a propósito -- ver MenuService.obtenerCategoria/obtenerItem
     * para el mismo criterio ya aplicado en `menu`: no darle a quien
     * manipule la URL información de si el token alguna vez fue válido.
     */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class SesionMesaInvalidaException extends RuntimeException {
        public SesionMesaInvalidaException() {
            super("El enlace de esta mesa ya no es válido. Pide al mesero un código QR nuevo.");
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class RestauranteNoDisponibleException extends RuntimeException {
        public RestauranteNoDisponibleException() {
            super("Este restaurante no está disponible en este momento.");
        }
    }

    /** Mismo patrón que menu.excepcion.MenuExceptions.SinTenantEnContextoException. */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class SinTenantEnContextoException extends RuntimeException {
        public SinTenantEnContextoException() {
            super("Esta operación requiere un usuario de restaurante autenticado (no SUPER_ADMIN)");
        }
    }
}
