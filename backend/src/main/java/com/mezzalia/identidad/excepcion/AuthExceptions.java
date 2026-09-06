package com.mezzalia.identidad.excepcion;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public final class AuthExceptions {

    private AuthExceptions() {}

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class CredencialesInvalidasException extends RuntimeException {
        public CredencialesInvalidasException() {
            super("Usuario o contraseña incorrectos");
        }
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class CuentaInactivaException extends RuntimeException {
        public CuentaInactivaException() {
            super("La cuenta está inactiva");
        }
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class TokenInvalidoException extends RuntimeException {
        public TokenInvalidoException(String motivo) {
            super("Token inválido: " + motivo);
        }
    }

    /**
     * Se lanza tanto por expiración/no-existencia normal del refresh token
     * como por REUSO de un refresh token ya rotado (indicio de robo) — en
     * ambos casos la respuesta al cliente es la misma (401 genérico) para no
     * darle a un atacante información de cuál es el caso real.
     */
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class RefreshTokenInvalidoException extends RuntimeException {
        public RefreshTokenInvalidoException() {
            super("Refresh token inválido o expirado");
        }
    }

    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public static class DemasiadosIntentosException extends RuntimeException {
        public DemasiadosIntentosException(long segundosParaReintentar) {
            super("Demasiados intentos fallidos. Reintente en " + segundosParaReintentar + " segundos");
        }
    }

    /**
     * RF-10 PR4: defensa-en-profundidad de AuthService.usuarioAutenticadoActual
     * -- no debería poder pasar nunca en la práctica (SecurityConfig ya
     * exige autenticado a nivel de filtro para /api/admin/**, ver
     * SesionController), pero un JWT válido para un username que ya no
     * resuelve a ningún UsuarioInterno (nunca debería ocurrir, este
     * proyecto no hace hard-delete de usuarios) es un caso distinto de
     * "credenciales incorrectas" y merece su propio mensaje, no uno que
     * sugiera que el usuario escribió mal su contraseña.
     */
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class UsuarioAutenticadoNoEncontradoException extends RuntimeException {
        public UsuarioAutenticadoNoEncontradoException() {
            super("El usuario autenticado ya no existe");
        }
    }
}
