package com.mezzalia.sucursal.controlador;

import com.mezzalia.identidad.dto.UsuarioInternoDto;
import com.mezzalia.identidad.servicio.AsignacionSucursalService;
import com.mezzalia.sucursal.Sucursal;
import com.mezzalia.sucursal.dto.CrearSucursalRequest;
import com.mezzalia.sucursal.dto.SucursalDto;
import com.mezzalia.sucursal.servicio.SucursalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RF-10/§9: mismo criterio que MenuAdminController/MesaAdminController --
 * ver la lista de sucursales la puede necesitar un MESERO en el futuro (ej.
 * para elegir en qué sucursal está trabajando), así que el GET no lleva
 * @PreAuthorize adicional. Crear/desactivar sucursales sí queda restringido
 * a ADMIN_RESTAURANTE: es una decisión operativa del dueño/encargado.
 */
@RestController
@RequestMapping("/api/admin/sucursales")
@RequiredArgsConstructor
public class SucursalAdminController {

    private final SucursalService sucursalService;
    private final AsignacionSucursalService asignacionSucursalService;

    @GetMapping
    public ResponseEntity<List<SucursalDto>> listarSucursales() {
        List<SucursalDto> sucursales = sucursalService.listarSucursales().stream()
                .map(SucursalDto::desde)
                .toList();
        return ResponseEntity.ok(sucursales);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN_RESTAURANTE')")
    public ResponseEntity<SucursalDto> crearSucursal(@Valid @RequestBody CrearSucursalRequest request) {
        Sucursal sucursal = sucursalService.crearSucursal(request.nombre(), request.direccion());
        return ResponseEntity.status(HttpStatus.CREATED).body(SucursalDto.desde(sucursal));
    }

    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMIN_RESTAURANTE')")
    public ResponseEntity<Void> desactivarSucursal(@PathVariable Long id) {
        sucursalService.desactivarSucursal(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reactivar")
    @PreAuthorize("hasRole('ADMIN_RESTAURANTE')")
    public ResponseEntity<Void> reactivarSucursal(@PathVariable Long id) {
        sucursalService.reactivarSucursal(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Vista pedida por el dueño del restaurante: qué meseros tiene
     * asignados esta sucursal, para poder quitarlos sin tener que ir uno
     * por uno a UsuariosPage.jsx. Restringido a ADMIN_RESTAURANTE a nivel
     * de método (a diferencia del GET de arriba) -- gestionar el staff de
     * una sucursal es una decisión operativa del encargado, no algo que un
     * MESERO necesite consultar. El quitar en sí reusa el DELETE que ya
     * existe en UsuarioInternoAdminController
     * (/api/admin/usuarios/{id}/sucursales/{sucursalId}) -- no hace falta
     * duplicar esa lógica acá, este controlador solo agrega la vista de
     * lectura que faltaba en el sentido "sucursal -> meseros".
     */
    @GetMapping("/{id}/usuarios")
    @PreAuthorize("hasRole('ADMIN_RESTAURANTE')")
    public ResponseEntity<List<UsuarioInternoDto>> listarUsuariosAsignados(@PathVariable Long id) {
        List<UsuarioInternoDto> usuarios = asignacionSucursalService.listarUsuariosDe(id).stream()
                .map(UsuarioInternoDto::desde)
                .toList();
        return ResponseEntity.ok(usuarios);
    }
}
