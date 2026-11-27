package com.mezzalia.turno;

import com.mezzalia.identidad.RolUsuario;
import com.mezzalia.identidad.UsuarioInterno;
import com.mezzalia.identidad.UsuarioInternoRepository;
import com.mezzalia.identidad.excepcion.UsuarioInternoExceptions;
import com.mezzalia.sucursal.SucursalContext;
import com.mezzalia.tenant.TenantContext;
import com.mezzalia.turno.dto.EstadoTurnoDto;
import com.mezzalia.turno.dto.HorasPorDiaDto;
import com.mezzalia.turno.excepcion.TurnoExceptions;
import com.mezzalia.turno.servicio.TurnoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Auditoria 2026-09-08 §2.1: TurnoService (el feature mas nuevo, marcar
 * entrada/salida y horas trabajadas) no tenia ningun test. Lo delicado es el
 * cierre automatico a las 12 h: es lo que evita que un "me olvide de marcar
 * salida" se convierta en 40 horas pagadas.
 */
@DataJpaTest
class TurnoServiceTest {

    @Autowired private TestEntityManager em;
    @Autowired private TurnoRepository turnoRepository;
    @Autowired private UsuarioInternoRepository usuarioRepository;

    private TurnoService turnos;
    private UsuarioInterno mesero;

    @BeforeEach
    void preparar() {
        turnos = new TurnoService(turnoRepository, usuarioRepository);
        mesero = usuario(1L, "mesero.uno", RolUsuario.MESERO);
        TenantContext.establecerTenantId(1L);
        SucursalContext.establecerSucursalId(5L);
        autenticarComo("mesero.uno");
    }

    @AfterEach
    void limpiar() {
        TenantContext.limpiar();
        SucursalContext.limpiar();
        SecurityContextHolder.clearContext();
    }

    @Test
    void marcarEntradaAbreUnTurnoEnLaSucursalActual() {
        EstadoTurnoDto estado = turnos.marcarEntrada();

        assertThat(estado.enTurno()).isTrue();
        assertThat(estado.sucursalId()).isEqualTo(5L);
        assertThat(turnos.estadoActual().enTurno()).isTrue();
    }

    @Test
    void noSePuedeMarcarEntradaDosVeces() {
        turnos.marcarEntrada();
        assertThatThrownBy(() -> turnos.marcarEntrada())
                .isInstanceOf(TurnoExceptions.TurnoYaAbiertoException.class);
    }

    @Test
    void marcarSalidaSinTurnoAbiertoFalla() {
        assertThatThrownBy(() -> turnos.marcarSalida())
                .isInstanceOf(TurnoExceptions.TurnoNoAbiertoException.class);
    }

    @Test
    void marcarSalidaCierraElTurno() {
        turnos.marcarEntrada();
        assertThat(turnos.marcarSalida().enTurno()).isFalse();
        assertThat(turnos.estadoActual().enTurno()).isFalse();
        assertThat(turnoRepository.findAll()).singleElement()
                .satisfies(t -> {
                    assertThat(t.getFin()).isNotNull();
                    assertThat(t.isCierreAutomatico()).isFalse();
                });
    }

    @Test
    void unTurnoOlvidadoSeCierraSoloALas12HorasYNoCuentaMas() {
        Turno olvidado = turnoAbiertoHace(30);

        assertThat(turnos.estadoActual().enTurno()).isFalse();
        em.flush();
        em.clear();
        Turno cerrado = turnoRepository.findById(olvidado.getId()).orElseThrow();
        assertThat(cerrado.isCierreAutomatico()).isTrue();
        assertThat(cerrado.getFin()).isEqualTo(cerrado.getInicio().plusHours(12));

        // ...y al dia siguiente puede volver a marcar entrada normalmente.
        assertThat(turnos.marcarEntrada().enTurno()).isTrue();
    }

    @Test
    void unTurnoDe11HorasSigueAbierto() {
        turnoAbiertoHace(11);
        assertThat(turnos.estadoActual().enTurno()).isTrue();
    }

    @Test
    void lasHorasDeUnTurnoOlvidadoSeTopanEn12() {
        turnoAbiertoHace(30);

        List<HorasPorDiaDto> horas = turnos.horasPorDiaDe(mesero.getId(), 7);

        assertThat(horas).singleElement().satisfies(h -> {
            assertThat(h.horas()).isEqualTo(12.0);
            assertThat(h.turnoAbiertoIncluido()).isFalse();
        });
    }

    @Test
    void lasHorasSeSumanPorDiaEnOrdenYMarcanElDiaConTurnoEnCurso() {
        LocalDate hoy = LocalDate.now();
        turnoCerrado(hoy.minusDays(2).atTime(9, 0), hoy.minusDays(2).atTime(13, 30)); // 4.5 h
        turnoCerrado(hoy.minusDays(2).atTime(18, 0), hoy.minusDays(2).atTime(21, 0)); // 3 h
        turnoCerrado(hoy.minusDays(1).atTime(10, 0), hoy.minusDays(1).atTime(12, 0)); // 2 h
        // Turno en curso que empezo hace 1 minuto: su dia es el de su inicio,
        // sea cual sea la hora a la que corra este test.
        LocalDate diaEnCurso = turnoAbiertoDesde(LocalDateTime.now().minusMinutes(1)).getInicio().toLocalDate();

        List<HorasPorDiaDto> horas = turnos.horasPorDiaDe(mesero.getId(), 7);

        assertThat(horas).extracting(HorasPorDiaDto::fecha).isSorted().contains(hoy.minusDays(2), hoy.minusDays(1), diaEnCurso);
        assertThat(horas).filteredOn(h -> h.fecha().equals(hoy.minusDays(2))).singleElement()
                .satisfies(h -> assertThat(h.horas()).isEqualTo(7.5));
        assertThat(horas).filteredOn(HorasPorDiaDto::turnoAbiertoIncluido).extracting(HorasPorDiaDto::fecha)
                .containsExactly(diaEnCurso);
    }

    @Test
    void soloCuentaLosUltimosNDias() {
        LocalDate hoy = LocalDate.now();
        turnoCerrado(hoy.minusDays(10).atTime(9, 0), hoy.minusDays(10).atTime(17, 0));
        turnoCerrado(hoy.minusDays(1).atTime(9, 0), hoy.minusDays(1).atTime(10, 0));

        assertThat(turnos.horasPorDiaDe(mesero.getId(), 7)).extracting(HorasPorDiaDto::fecha)
                .containsExactly(hoy.minusDays(1));
    }

    @Test
    void elAdminNoPuedeVerHorasDeUnUsuarioDeOtroRestaurante() {
        UsuarioInterno ajeno = usuario(2L, "mesero.ajeno", RolUsuario.MESERO);
        assertThatThrownBy(() -> turnos.horasPorDiaDe(ajeno.getId(), 7))
                .isInstanceOf(UsuarioInternoExceptions.UsuarioNoEncontradoException.class);
    }

    @Test
    void superAdminNoMarcaTurnos() {
        usuario(null, "super", RolUsuario.SUPER_ADMIN);
        autenticarComo("super");
        assertThatThrownBy(() -> turnos.marcarEntrada())
                .isInstanceOf(UsuarioInternoExceptions.SinTenantEnContextoException.class);
    }

    // --- ayudantes --------------------------------------------------------

    private Turno turnoAbiertoHace(int horas) {
        return turnoAbiertoDesde(LocalDateTime.now().minusHours(horas));
    }

    private Turno turnoAbiertoDesde(LocalDateTime inicio) {
        Turno t = new Turno();
        t.setTenantId(1L);
        t.setUsuarioInternoId(mesero.getId());
        t.setSucursalId(5L);
        t.setInicio(inicio);
        return em.persistAndFlush(t);
    }

    private void turnoCerrado(LocalDateTime inicio, LocalDateTime fin) {
        Turno t = new Turno();
        t.setTenantId(1L);
        t.setUsuarioInternoId(mesero.getId());
        t.setSucursalId(5L);
        t.setInicio(inicio);
        t.setFin(fin);
        em.persistAndFlush(t);
    }

    private UsuarioInterno usuario(Long tenantId, String username, RolUsuario rol) {
        UsuarioInterno u = new UsuarioInterno();
        u.setTenantId(tenantId);
        u.setUsername(username);
        u.setPasswordHash("no-relevante");
        u.setRol(rol);
        u.setActivo(true);
        return em.persistAndFlush(u);
    }

    private void autenticarComo(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }
}
