package com.mezzalia.menu.excepcion;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.math.BigDecimal;

public final class MenuExceptions {

    private MenuExceptions() {}

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class CategoriaNoEncontradaException extends RuntimeException {
        public CategoriaNoEncontradaException(Long id) {
            super("Categoría no encontrada con id: " + id);
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class ItemMenuNoEncontradoException extends RuntimeException {
        public ItemMenuNoEncontradoException(Long id) {
            super("Ítem de carta no encontrado con id: " + id);
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class PrecioInvalidoException extends RuntimeException {
        public PrecioInvalidoException(BigDecimal precio) {
            super("El precio debe ser mayor o igual a cero: " + precio);
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class ArchivoInvalidoException extends RuntimeException {
        public ArchivoInvalidoException(String motivo) {
            super(motivo);
        }
    }

    /**
     * SUPER_ADMIN (equipo Mezzalia) no tiene tenant_id propio -- no gestiona
     * la carta de un restaurante específico. Si de alguna forma llega hasta
     * acá (no debería, ver RolUsuario), es un 403 explícito, no un 500
     * genérico ni un NullPointerException silencioso.
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class SinTenantEnContextoException extends RuntimeException {
        public SinTenantEnContextoException() {
            super("Esta operación requiere un usuario de restaurante autenticado (no SUPER_ADMIN)");
        }
    }
}
