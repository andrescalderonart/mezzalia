package com.mezzalia.identidad.servicio;

import com.mezzalia.identidad.excepcion.AuthExceptions;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Auditoria 2026-09-08 §2.1: LoginAttemptService (rate limiting del login,
 * "no negociable" segun documentacion-tecnica.md §10) no tenia ningun test.
 */
class LoginAttemptServiceTest {

    /** Reloj que el test puede adelantar a mano. */
    private static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-11-26T03:00:00Z");
        void avanzar(Duration d) { ahora = ahora.plus(d); }
        @Override public Instant instant() { return ahora; }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    private final RelojManual reloj = new RelojManual();
    private final LoginAttemptService servicio = new LoginAttemptService(reloj);

    private void fallar(String usuario, int veces) {
        for (int i = 0; i < veces; i++) servicio.registrarFallo(usuario);
    }

    @Test
    void cuatroFallosNoBloquean() {
        fallar("mesero.ana", 4);
        assertThatCode(() -> servicio.verificarNoBloqueado("mesero.ana")).doesNotThrowAnyException();
    }

    @Test
    void elQuintoFalloBloqueaQuinceMinutosConElTiempoRestanteEnLaExcepcion() {
        fallar("mesero.ana", 5);
        assertThatThrownBy(() -> servicio.verificarNoBloqueado("mesero.ana"))
                .isInstanceOf(AuthExceptions.DemasiadosIntentosException.class);

        reloj.avanzar(Duration.ofMinutes(14).plusSeconds(59));
        assertThatThrownBy(() -> servicio.verificarNoBloqueado("mesero.ana"))
                .isInstanceOf(AuthExceptions.DemasiadosIntentosException.class);

        reloj.avanzar(Duration.ofSeconds(1));
        assertThatCode(() -> servicio.verificarNoBloqueado("mesero.ana")).doesNotThrowAnyException();
    }

    @Test
    void cambiarMayusculasOEspaciosNoEsquivaElBloqueo() {
        servicio.registrarFallo("Mesero.Ana");
        servicio.registrarFallo("MESERO.ANA");
        servicio.registrarFallo(" mesero.ana ");
        servicio.registrarFallo("mesero.ANA");
        servicio.registrarFallo("mesero.ana");
        assertThatThrownBy(() -> servicio.verificarNoBloqueado("mEsErO.aNa"))
                .isInstanceOf(AuthExceptions.DemasiadosIntentosException.class);
    }

    @Test
    void elBloqueoEsPorUsuarioNoGlobal() {
        fallar("mesero.ana", 5);
        assertThatCode(() -> servicio.verificarNoBloqueado("mesero.beto")).doesNotThrowAnyException();
    }

    @Test
    void unLoginExitosoReiniciaElContador() {
        fallar("mesero.ana", 4);
        servicio.registrarExito("mesero.ana");
        fallar("mesero.ana", 4);
        assertThatCode(() -> servicio.verificarNoBloqueado("mesero.ana")).doesNotThrowAnyException();
    }

    @Test
    void cuandoVenceElBloqueoElUsuarioRecuperaSusCincoIntentos() {
        // Regresion: antes, el contador seguia en 5 tras vencer el bloqueo y
        // un solo error de tipeo volvia a bloquear la cuenta 15 minutos mas.
        fallar("mesero.ana", 5);
        reloj.avanzar(Duration.ofMinutes(15));

        fallar("mesero.ana", 1);
        assertThatCode(() -> servicio.verificarNoBloqueado("mesero.ana")).doesNotThrowAnyException();

        fallar("mesero.ana", 3);
        assertThatCode(() -> servicio.verificarNoBloqueado("mesero.ana")).doesNotThrowAnyException();
        fallar("mesero.ana", 1);
        assertThatThrownBy(() -> servicio.verificarNoBloqueado("mesero.ana"))
                .isInstanceOf(AuthExceptions.DemasiadosIntentosException.class);
    }

    @Test
    void usernameNuloNoRevienta() {
        assertThatCode(() -> {
            servicio.registrarFallo(null);
            servicio.verificarNoBloqueado(null);
        }).doesNotThrowAnyException();
    }

    @Test
    void laPurgaBorraBloqueosVencidosYFallosViejosPeroNoLosVigentes() {
        fallar("bloqueo.viejo", 5);            // bloqueado hasta T+15
        fallar("fallo.viejo", 2);              // sin bloqueo, ultimo fallo en T
        reloj.avanzar(Duration.ofMinutes(10));
        fallar("bloqueo.vigente", 5);          // bloqueado hasta T+25
        fallar("fallo.reciente", 1);           // ultimo fallo en T+10
        reloj.avanzar(Duration.ofMinutes(5));  // ahora T+15

        servicio.purgarVencidas();

        assertThat(servicio.entradasEnMemoria()).isEqualTo(2);
        assertThatThrownBy(() -> servicio.verificarNoBloqueado("bloqueo.vigente"))
                .isInstanceOf(AuthExceptions.DemasiadosIntentosException.class);
        // El fallo reciente sigue contando: 4 mas lo bloquean.
        fallar("fallo.reciente", 4);
        assertThatThrownBy(() -> servicio.verificarNoBloqueado("fallo.reciente"))
                .isInstanceOf(AuthExceptions.DemasiadosIntentosException.class);
    }

    @Test
    void elMapaNoCreceSinLimiteConUsernamesInventados() {
        // Un bot probando usernames distintos: con la purga automatica cada
        // 64 fallos, las entradas viejas no se acumulan.
        for (int i = 0; i < 1000; i++) {
            servicio.registrarFallo("bot." + i);
            reloj.avanzar(Duration.ofMinutes(1));
        }
        // Solo sobreviven los de los ultimos 15 min (+ a lo sumo 64 desde la
        // ultima purga).
        assertThat(servicio.entradasEnMemoria()).isLessThanOrEqualTo(15 + 64);
    }

    @Test
    void fallosDeHaceMasDeQuinceMinutosNoSumanAunqueLaPurgaNoHayaPasado() {
        fallar("mesero.ana", 4);
        reloj.avanzar(Duration.ofMinutes(15));
        fallar("mesero.ana", 4);
        assertThatCode(() -> servicio.verificarNoBloqueado("mesero.ana")).doesNotThrowAnyException();
    }
}
