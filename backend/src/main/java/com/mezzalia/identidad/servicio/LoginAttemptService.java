package com.mezzalia.identidad.servicio;

import com.mezzalia.identidad.excepcion.AuthExceptions;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting del login (documentacion-tecnica.md §10: "no negociable").
 *
 * LIMITACIÓN CONOCIDA, dejada explícita a propósito: esto es en memoria, por
 * instancia. Con una sola instancia de backend (que es el plan hasta Fase 3,
 * §13) frena fuerza bruta igual. El día que haya más de una instancia detrás
 * de un balanceador, esto deja de servir (cada instancia cuenta intentos por
 * separado) y hay que migrar a Redis (que el propio doc ya prevé para
 * cache/sesiones, §5.2) — no lo hice ahora porque Redis ni siquiera está
 * cableado como dependencia en pom.xml todavía.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_INTENTOS_FALLIDOS = 5;
    private static final Duration VENTANA_BLOQUEO = Duration.ofMinutes(15);

    private final ConcurrentHashMap<String, EstadoIntentos> intentosPorUsuario = new ConcurrentHashMap<>();
    private final Clock reloj;

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    // Solo para tests (LoginAttemptServiceTest): permite avanzar el tiempo
    // para probar el vencimiento del bloqueo sin dormir 15 minutos.
    LoginAttemptService(Clock reloj) {
        this.reloj = reloj;
    }

    public void verificarNoBloqueado(String username) {
        EstadoIntentos estado = intentosPorUsuario.get(normalizar(username));
        if (estado == null || estado.bloqueadoHasta == null) {
            return;
        }
        Instant ahora = reloj.instant();
        if (estado.bloqueadoHasta.isAfter(ahora)) {
            long segundosRestantes = Duration.between(ahora, estado.bloqueadoHasta).getSeconds();
            throw new AuthExceptions.DemasiadosIntentosException(Math.max(segundosRestantes, 1));
        }
    }

    public void registrarFallo(String username) {
        intentosPorUsuario.compute(normalizar(username), (clave, estadoActual) -> {
            Instant ahora = reloj.instant();
            // Un bloqueo ya vencido es borrón y cuenta nueva: sin esto, el
            // contador seguia en 5+ y el PRIMER error de tipeo despues de
            // esperar los 15 minutos volvia a bloquear la cuenta otros 15
            // (en la practica, el usuario nunca recuperaba los 5 intentos).
            boolean bloqueoVencido = estadoActual != null && estadoActual.bloqueadoHasta != null
                    && !estadoActual.bloqueadoHasta.isAfter(ahora);
            EstadoIntentos estado = (estadoActual == null || bloqueoVencido) ? new EstadoIntentos() : estadoActual;
            estado.fallos++;
            if (estado.fallos >= MAX_INTENTOS_FALLIDOS) {
                estado.bloqueadoHasta = ahora.plus(VENTANA_BLOQUEO);
            }
            return estado;
        });
    }

    public void registrarExito(String username) {
        intentosPorUsuario.remove(normalizar(username));
    }

    private String normalizar(String username) {
        // Alineado con la collation utf8mb4_unicode_ci de la columna username
        // (case-insensitive): sin esto, alguien podría esquivar el bloqueo
        // alternando mayúsculas/minúsculas en cada intento.
        return username == null ? "" : username.trim().toLowerCase();
    }

    private static final class EstadoIntentos {
        private int fallos = 0;
        private volatile Instant bloqueadoHasta;
    }
}
