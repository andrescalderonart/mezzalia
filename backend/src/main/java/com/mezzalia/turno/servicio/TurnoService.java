package com.mezzalia.turno.servicio;

import com.mezzalia.identidad.RolUsuario;
import com.mezzalia.identidad.UsuarioInterno;
import com.mezzalia.identidad.UsuarioInternoRepository;
import com.mezzalia.identidad.excepcion.AuthExceptions;
import com.mezzalia.identidad.excepcion.UsuarioInternoExceptions;
import com.mezzalia.sucursal.SucursalContext;
import com.mezzalia.tenant.TenantContext;
import com.mezzalia.turno.Turno;
import com.mezzalia.turno.TurnoRepository;
import com.mezzalia.turno.dto.EstadoTurnoDto;
import com.mezzalia.turno.dto.HorasPorDiaDto;
import com.mezzalia.turno.excepcion.TurnoExceptions;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pedido explicito de Andres (2026-09-07): poder ver cuanto tiempo
 * trabajado por dia lleva cada usuario ("jornada laboral"). Antes de
 * construir esto se le planteo -- y confirmo -- que ningun dato que ya
 * existiera en el sistema serviria para calcularlo de forma confiable:
 *
 *  - identidad.AsignacionSucursal (la asignacion mesero<->sucursal) es
 *    EXPLICITAMENTE una tabla sin historial, hard-delete (ver su propio
 *    javadoc) -- no queda ningun rastro de cuando alguien dejo de estar
 *    asignado a una sucursal.
 *  - El ciclo de vida del JWT (login/refresh) tampoco sirve de proxy: el
 *    refresco silencioso del frontend (ver client.js/
 *    intentarReseleccionAutomatica) mantiene una sesion "viva" aunque la
 *    persona haya cerrado la pestana y se haya ido a su casa.
 *
 * La unica forma honesta de tener un numero real es que la propia persona
 * marque entrada/salida -- eso es lo que este servicio implementa, vía
 * SesionController (el mismo lugar conceptual que "en que sucursal estoy
 * trabajando", ver SesionController.seleccionarSucursal).
 *
 * TOPE_HORAS_TURNO: si alguien se olvida de marcar salida, un turno que
 * queda abierto para siempre romperia tanto marcarEntrada (no podria
 * empezar uno nuevo) como el reporte de horas (sumaria indefinidamente).
 * Se cierra solo despues de esa cantidad de horas seguidas -- a proposito
 * NO es un corte a medianoche: un restaurante que cierra pasada la
 * medianoche tiene turnos reales que cruzan de un dia calendario a otro
 * (decision de negocio confirmada explicitamente con Andres, que senalo
 * el caso). El cierre es LAZY (cerrarSiVencido se llama antes de
 * cualquier operacion que dependa de "hay o no un turno abierto") porque
 * este proyecto no tiene ningun scheduler/cron corriendo -- agregar uno
 * solo para esto no se justificaba.
 */
@Service
@RequiredArgsConstructor
public class TurnoService {

    private static final long TOPE_HORAS_TURNO = 12;

    private final TurnoRepository turnoRepository;
    private final UsuarioInternoRepository usuarioInternoRepository;

    @Transactional
    public EstadoTurnoDto marcarEntrada() {
        UsuarioInterno usuario = usuarioAutenticadoActual();
        cerrarSiVencido(usuario.getId());
        if (turnoRepository.findFirstByUsuarioInternoIdAndFinIsNull(usuario.getId()).isPresent()) {
            throw new TurnoExceptions.TurnoYaAbiertoException();
        }
        Turno turno = new Turno();
        turno.setTenantId(tenantActual());
        turno.setUsuarioInternoId(usuario.getId());
        turno.setSucursalId(SucursalContext.obtenerSucursalId());
        turno.setInicio(LocalDateTime.now());
        turnoRepository.save(turno);
        return new EstadoTurnoDto(true, turno.getInicio(), turno.getSucursalId());
    }

    @Transactional
    public EstadoTurnoDto marcarSalida() {
        UsuarioInterno usuario = usuarioAutenticadoActual();
        cerrarSiVencido(usuario.getId());
        Turno turno = turnoRepository.findFirstByUsuarioInternoIdAndFinIsNull(usuario.getId())
                .orElseThrow(TurnoExceptions.TurnoNoAbiertoException::new);
        turno.setFin(LocalDateTime.now());
        turnoRepository.save(turno);
        return new EstadoTurnoDto(false, null, null);
    }

    @Transactional
    public EstadoTurnoDto estadoActual() {
        UsuarioInterno usuario = usuarioAutenticadoActual();
        cerrarSiVencido(usuario.getId());
        return turnoRepository.findFirstByUsuarioInternoIdAndFinIsNull(usuario.getId())
                .map(t -> new EstadoTurnoDto(true, t.getInicio(), t.getSucursalId()))
                .orElseGet(() -> new EstadoTurnoDto(false, null, null));
    }

    /**
     * Vista de ADMIN_RESTAURANTE (ver UsuarioInternoAdminController):
     * horas trabajadas por dia de un usuario, en los ultimos `dias` dias
     * calendario (incluyendo hoy). Aplica cerrarSiVencido antes de leer
     * para que un turno vencido no infle el ultimo dia del reporte con
     * horas que en realidad son un olvido de marcar salida.
     */
    @Transactional(readOnly = true)
    public List<HorasPorDiaDto> horasPorDiaDe(Long usuarioId, int dias) {
        UsuarioInterno usuario = obtenerUsuarioDelTenant(usuarioId);
        cerrarSiVencido(usuario.getId());

        LocalDateTime desde = LocalDate.now().minusDays(Math.max(dias, 1) - 1L).atStartOfDay();
        List<Turno> turnos = turnoRepository
                .findByUsuarioInternoIdAndInicioGreaterThanEqualOrderByInicioAsc(usuario.getId(), desde);

        LocalDateTime ahora = LocalDateTime.now();
        Map<LocalDate, Double> horasPorDia = new LinkedHashMap<>();
        Map<LocalDate, Boolean> abiertoPorDia = new LinkedHashMap<>();
        for (Turno turno : turnos) {
            LocalDate dia = turno.getInicio().toLocalDate();
            LocalDateTime fin = turno.getFin() != null ? turno.getFin() : ahora;
            double horas = Duration.between(turno.getInicio(), fin).toMinutes() / 60.0;
            horasPorDia.merge(dia, horas, Double::sum);
            if (turno.getFin() == null) {
                abiertoPorDia.put(dia, true);
            }
        }

        return horasPorDia.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new HorasPorDiaDto(
                        e.getKey(),
                        Math.round(e.getValue() * 100) / 100.0,
                        abiertoPorDia.getOrDefault(e.getKey(), false)))
                .toList();
    }

    /**
     * Cierra, en el momento de la lectura, cualquier turno abierto de este
     * usuario que ya exceda TOPE_HORAS_TURNO desde su inicio -- ver el
     * javadoc de clase para por que es lazy y por que el tope no es un
     * corte a medianoche. El fin queda fijado en inicio+TOPE (no en el
     * momento en que se detecta): la duracion de un turno cerrado
     * automaticamente siempre es exactamente el tope, sin importar cuanto
     * tiempo despues se note.
     */
    private void cerrarSiVencido(Long usuarioId) {
        turnoRepository.findFirstByUsuarioInternoIdAndFinIsNull(usuarioId).ifPresent(turno -> {
            if (Duration.between(turno.getInicio(), LocalDateTime.now()).toHours() >= TOPE_HORAS_TURNO) {
                turno.setFin(turno.getInicio().plusHours(TOPE_HORAS_TURNO));
                turno.setCierreAutomatico(true);
                turnoRepository.save(turno);
            }
        });
    }

    /**
     * Mismo criterio de "no encontrado" generico (nunca 403) que
     * AsignacionSucursalService.obtenerUsuarioDelTenant: no confiar solo en
     * el filtro de Hibernate, y no confirmarle a un ADMIN_RESTAURANTE que
     * un id de usuario existe en otro tenant.
     */
    private UsuarioInterno obtenerUsuarioDelTenant(Long id) {
        UsuarioInterno usuario = usuarioInternoRepository.findById(id)
                .orElseThrow(() -> new UsuarioInternoExceptions.UsuarioNoEncontradoException(id));
        if (!tenantActual().equals(usuario.getTenantId())) {
            throw new UsuarioInternoExceptions.UsuarioNoEncontradoException(id);
        }
        return usuario;
    }

    /**
     * Mismo patron que AuthService.usuarioAutenticadoActual: defensa en
     * profundidad, no deberia poder pasar nunca en la practica ya que
     * SesionController vive bajo /api/admin/** (autenticado a nivel de
     * filtro, ver SecurityConfig).
     *
     * SUPER_ADMIN (equipo Mezzalia, sin tenant_id propio) queda excluido
     * explicitamente aca, mismo criterio que
     * AuthService.misSucursalesDisponibles/seleccionarSucursal: no es
     * personal de un restaurante, no tiene turnos que marcar.
     */
    private UsuarioInterno usuarioAutenticadoActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : null;
        UsuarioInterno usuario = usuarioInternoRepository.findByUsername(username)
                .orElseThrow(AuthExceptions.UsuarioAutenticadoNoEncontradoException::new);
        if (usuario.getRol() == RolUsuario.SUPER_ADMIN) {
            throw new UsuarioInternoExceptions.SinTenantEnContextoException();
        }
        return usuario;
    }

    private Long tenantActual() {
        Long tenantId = TenantContext.obtenerTenantId();
        if (tenantId == null) {
            throw new UsuarioInternoExceptions.SinTenantEnContextoException();
        }
        return tenantId;
    }
}
