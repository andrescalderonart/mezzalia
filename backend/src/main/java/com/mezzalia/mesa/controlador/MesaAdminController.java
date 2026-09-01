package com.mezzalia.mesa.controlador;

import com.mezzalia.mesa.Mesa;
import com.mezzalia.mesa.SesionMesa;
import com.mezzalia.mesa.dto.CrearMesaRequest;
import com.mezzalia.mesa.dto.MesaDto;
import com.mezzalia.mesa.dto.SesionMesaDto;
import com.mezzalia.mesa.servicio.MesaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RF-09: mismo criterio que MenuAdminController -- ver la lista de mesas
 * también la necesita un MESERO (para saber a qué mesa atender), así que los
 * GET no llevan @PreAuthorize adicional, solo la autenticación que ya exige
 * SecurityConfig para /api/admin/**. Crear/desactivar mesas y regenerar el
 * QR sí quedan resguardados a ADMIN_RESTAURANTE: son decisiones operativas
 * del dueño/encargado, no del mesero de turno.
 */
@RestController
@RequestMapping("/api/admin/mesas")
@RequiredArgsConstructor
public class MesaAdminController {

    private final MesaService mesaService;

    @GetMapping
    public ResponseEntity<List<MesaDto>> listarMesas() {
        List<MesaDto> mesas = mesaService.listarMesas().stream().map(MesaDto::desde).toList();
        return ResponseEntity.ok(mesas);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN_RESTAURANTE')")
    public ResponseEntity<MesaDto> crearMesa(@Valid @RequestBody CrearMesaRequest request) {
        Mesa mesa = mesaService.crearMesa(request.codigo());
        return ResponseEntity.status(HttpStatus.CREATED).body(MesaDto.desde(mesa));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_RESTAURANTE')")
    public ResponseEntity<Void> desactivarMesa(@PathVariable Long id) {
        mesaService.desactivarMesa(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Genera (o regenera, invalidando la anterior) el token de sesión de la
     * mesa -- el frontend construye el QR a partir de `token`
     * (ver MesasPage.jsx en app-admin).
     */
    @PostMapping("/{id}/sesion")
    @PreAuthorize("hasRole('ADMIN_RESTAURANTE')")
    public ResponseEntity<SesionMesaDto> generarSesion(@PathVariable Long id) {
        SesionMesa sesion = mesaService.generarSesion(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(SesionMesaDto.desde(sesion));
    }
}
