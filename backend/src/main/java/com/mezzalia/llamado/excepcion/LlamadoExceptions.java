package com.mezzalia.llamado.excepcion;

public final class LlamadoExceptions {

    private LlamadoExceptions() {}

    public static class LlamadoNoEncontradoException extends RuntimeException {
        public LlamadoNoEncontradoException(Long id) {
            super("Llamado no encontrado con id: " + id);
        }
    }

    /** Backstop de abuso del endpoint publico sin auth. */
    public static class DemasiadosLlamadosException extends RuntimeException {
        public DemasiadosLlamadosException() {
            super("Demasiados llamados desde esta mesa. Espera un momento, el personal ya fue avisado.");
        }
    }

    public static class SinTenantEnContextoException extends RuntimeException {
        public SinTenantEnContextoException() {
            super("Esta operacion requiere un usuario de restaurante");
        }
    }
}
