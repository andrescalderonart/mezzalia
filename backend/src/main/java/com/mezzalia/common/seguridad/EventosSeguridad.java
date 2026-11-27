package com.mezzalia.common.seguridad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registro de eventos de seguridad (OWASP ASVS 5.0, capítulo V16 "Security
 * Logging and Error Handling"): quién intentó qué y cuándo, para poder
 * investigar un incidente. Un logger propio ("SEGURIDAD") permite mandar
 * estas líneas a otro destino o alertar sobre ellas sin mezclarlas con el
 * log de la aplicación.
 *
 * Reglas (ASVS V16.2 / V16.4):
 * - NUNCA se registran contraseñas, tokens ni teléfonos.
 * - Todo dato que viene del usuario pasa por {@link #limpiar(String)}: sin
 *   saltos de línea no puede inventar líneas falsas en el log (inyección de
 *   logs) y se corta a un largo razonable.
 */
public final class EventosSeguridad {

    private static final Logger log = LoggerFactory.getLogger("SEGURIDAD");
    private static final int LARGO_MAXIMO = 100;

    private EventosSeguridad() {
    }

    public static void loginExitoso(String username) {
        log.info("evento=login_exitoso usuario={}", limpiar(username));
    }

    public static void loginFallido(String username) {
        log.warn("evento=login_fallido usuario={}", limpiar(username));
    }

    public static void cuentaBloqueada(String username) {
        log.warn("evento=cuenta_bloqueada_por_intentos usuario={}", limpiar(username));
    }

    public static void loginCuentaInactiva(String username) {
        log.warn("evento=login_cuenta_inactiva usuario={}", limpiar(username));
    }

    public static void refreshInvalido() {
        log.warn("evento=refresh_token_invalido");
    }

    public static void accesoDenegado(String metodo, String uri) {
        log.warn("evento=acceso_denegado metodo={} uri={}", limpiar(metodo), limpiar(uri));
    }

    public static void archivoRechazado(String motivo) {
        log.warn("evento=archivo_rechazado motivo={}", limpiar(motivo));
    }

    public static void passwordCambiada(Long usuarioId) {
        log.info("evento=password_cambiada usuarioId={}", usuarioId);
    }

    static String limpiar(String valor) {
        if (valor == null) {
            return "-";
        }
        String sinControl = valor.replaceAll("[\\p{Cntrl}]", "_");
        return sinControl.length() > LARGO_MAXIMO ? sinControl.substring(0, LARGO_MAXIMO) + "…" : sinControl;
    }
}
