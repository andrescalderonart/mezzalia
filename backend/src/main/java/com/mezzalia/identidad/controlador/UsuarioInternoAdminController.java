package com.mezzalia.identidad.controlador;

import com.mezzalia.identidad.UsuarioInterno;
import com.mezzalia.identidad.dto.CrearUsuarioInternoRequest;
import com.mezzalia.identidad.dto.ResetearPasswordRequest;
import com.mezzalia.identidad.dto.UsuarioInternoDto;
import com.mezzalia.identidad.servicio.AsignacionSucursalService;
import com.mezzalia.identidad.servicio.UsuarioInternoAdminService;
import com.mezzalia.sucursal.dto.SucursalDto;
import com.mezzalia.turno.dto.HorasPorDiaDto;
import com.mezzalia.turno.servicio.TurnoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RF-09/documentacion-tecnica.md §2: CRUD de usuarios internos del propio
 * tenant -- exclusivo de ADMIN_RESTAURANTE (@PreAuthorize a nivel de clase,
 * mismo patrón que TenantAdminController para SUPER_ADMIN): un MESERO
 * puede operar pedidos y ver la carta, pero no dar de alta ni desactivar
 * otras cuentas del restaurante donde trabaja.
 */
@RestController
@RequestMapping("/api/admin/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_RESTAURANTE')")
public class UsuarioInternoAdminController {

    private final UsuarioInternoAdminService usuarioInternoAdminService;
    private final AsignacionSucursalService asignacionSucursalService;
    private final TurnoService turnoService;

    @GetMapping
    public ResponseEntity<List<UsuarioInternoDto>> listarUsuarios() {
        List<UsuarioInternoDto> usuarios = usuarioInternoAdminService.listarUsuarios().stream()
                .map(UsuarioInternoDto::desde)
                .toList();
        return ResponseEntity.ok(usuarios);
    }

    @PostMapping
    public ResponseEntity<UsuarioInternoDto> crearUsuario(@Valid @RequestBody CrearUsuarioInternoRequest request) {
        UsuarioInterno usuario = usuarioInternoAdminService.crearUsuario(
                request.username(), request.password(), request.rol());
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioInternoDto.desde(usuario));
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<Void> desactivarUsuario(@PathVariable Long id) {
        usuarioInternoAdminService.desactivarUsuario(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reactivar")
    public ResponseEntity<Void> reactivarUsuario(@PathVariable Long id) {
        usuarioInternoAdminService.reactivarUsuario(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> resetearPassword(@PathVariable Long id, @Valid @RequestBody ResetearPasswordRequest request) {
        usuarioInternoAdminService.resetearPassword(id, request.passwordNueva());
        return ResponseEntity.noContent().build();
    }

    /**
     * RF-10 PR4: sucursales asignadas a un MESERO -- ver
     * AsignacionSucursalService. Estos 3 endpoints heredan el mismo
     * @PreAuthorize de clase que el resto de este controlador (exclusivo
     * de ADMIN_RESTAURANTE): un MESERO no gestiona sus propias
     * asignaciones, las gestiona el encargado del restaurante (ver
     * SesionController para el endpoint que SI usa el propio MESERO para
     * ver/elegir entre las sucursales que ya tiene asignadas).
     */
    @GetMapping("/{id}/sucursales")
    public ResponseEntity<List<SucursalDto>> listarSucursalesAsignadas(@PathVariable Long id) {
        List<SucursalDto> sucursales = asignacionSucursalService.listarSucursalesDe(id).stream()
                .map(SucursalDto::desde)
                .toList();
        return ResponseEntity.ok(sucursales);
    }

    @PutMapping("/{id}/sucursales/{sucursalId}")
    public ResponseEntity<Void> asignarSucursal(@PathVariable Long id, @PathVariable Long sucursalId) {
        asignacionSucursalService.asignar(id, sucursalId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/sucursales/{sucursalId}")
    public ResponseEntity<Void> desasignarSucursal(@PathVariable Long id, @PathVariable Long sucursalId) {
        asignacionSucursalService.desasignar(id, sucursalId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Pedido explicito de Andres (2026-09-07): ver cuanto tiempo trabajado
     * por dia lleva cada usuario. Ver turno.servicio.TurnoService para la
     * discusion completa de por que esto depende de que la propia persona
     * marque entrada/salida (SesionController) en vez de derivarse de
     * algun dato que ya existiera. `dias` es una ventana simple (default
     * 14) en vez de un rango desde/hasta explicito -- no hubo pedido de
     * poder elegir un rango arbitrario, y una ventana de "los ultimos N
     * dias" es mas facil de entender para el dueño del restaurante que
     * tener que elegir dos fechas.
     */
    @GetMapping("/{id}/turnos")
    public ResponseEntity<List<HorasPorDiaDto>> horasPorDia(
            @PathVariable Long id,
            @RequestParam(defaultValue = "14") int dias) {
        return ResponseEntity.ok(turnoService.horasPorDiaDe(id, dias));
    }
}
