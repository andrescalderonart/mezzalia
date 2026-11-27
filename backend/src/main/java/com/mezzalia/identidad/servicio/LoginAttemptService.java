package com.mezzalia.identidad.servicio;

import com.mezzalia.identidad.excepcion.AuthExceptions;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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
    // Cada cuantos fallos registrados se barre el mapa buscando entradas
    // vencidas. Barrer en cada fallo seria O(n) por intento; cada 64 lo
    // amortiza y aun asi acota el mapa a "lo que fallo en los ultimos 15
    // min + a lo sumo 64 entradas viejas".
    private static final int PURGAR_CADA_N_FALLOS = 64;

    private final ConcurrentHashMap<String, EstadoIntentos> intentosPorUsuario = new ConcurrentHashMap<>();
    private final Clock reloj;
    private final AtomicLong fallosRegistrados = new AtomicLong();

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
            // Lo mismo si lleva 15 min sin fallos: esos intentos viejos ya no
            // cuentan (y la purga de abajo los borraria de todos modos; asi
            // el resultado no depende de si la purga ya paso o no).
            EstadoIntentos estado = (estadoActual == null || estadoActual.vencido(ahora))
                    ? new EstadoIntentos() : estadoActual;
            estado.fallos++;
            estado.ultimoFallo = ahora;
            if (estado.fallos >= MAX_INTENTOS_FALLIDOS) {
                estado.bloqueadoHasta = ahora.plus(VENTANA_BLOQUEO);
            }
            return estado;
        });
        if (fallosRegistrados.incrementAndGet() % PURGAR_CADA_N_FALLOS == 0) {
            purgarVencidas();
        }
    }

    /**
     * Borra las entradas cuyo bloqueo ya vencio o que llevan 15 min sin
     * fallos. Sin esto el mapa crecia sin limite: cada username probado
     * (incluidos los inventados por un bot) quedaba guardado para siempre.
     */
    void purgarVencidas() {
        Instant ahora = reloj.instant();
        // removeIf sobre la vista de entradas de ConcurrentHashMap es seguro
        // con escrituras concurrentes; una entrada que se actualiza justo
        // ahora puede sobrevivir hasta la proxima purga, lo cual no importa.
        intentosPorUsuario.entrySet().removeIf(e -> e.getValue().vencido(ahora));
    }

    // Solo para tests.
    int entradasEnMemoria() {
        return intentosPorUsuario.size();
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
        private volatile Instant ultimoFallo;
        private volatile Instant bloqueadoHasta;

        /** Bloqueo vencido, o sin bloqueo y 15 min sin fallos. */
        boolean vencido(Instant ahora) {
            if (bloqueadoHasta != null) {
                return !bloqueadoHasta.isAfter(ahora);
            }
            return ultimoFallo != null && !ultimoFallo.plus(VENTANA_BLOQUEO).isAfter(ahora);
        }
    }
}
