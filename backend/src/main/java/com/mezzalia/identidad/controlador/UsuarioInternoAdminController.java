package com.mezzalia.identidad.controlador;

import com.mezzalia.identidad.UsuarioInterno;
import com.mezzalia.identidad.dto.CrearUsuarioInternoRequest;
import com.mezzalia.identidad.dto.ResetearPasswordRequest;
import com.mezzalia.identidad.dto.UsuarioInternoDto;
import com.mezzalia.identidad.servicio.UsuarioInternoAdminService;
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
}
