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
import java.util.Map;

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

    /**
     * Incluye la sesión de QR vigente de cada mesa (si tiene) en el mismo
     * viaje -- ver el javadoc de MesaService.obtenerSesionesActivas: sin
     * esto, MesasPage.jsx solo sabía de una sesión generada en la MISMA
     * pestaña del navegador, y un simple refresh de página hacía que todas
     * las mesas se vieran como "sin QR", tentando a regenerar (lo que
     * invalida el QR ya impreso y pegado en la mesa física).
     */
    @GetMapping
    public ResponseEntity<List<MesaDto>> listarMesas() {
        List<Mesa> mesas = mesaService.listarMesas();
        Map<Long, SesionMesa> sesionesActivas = mesaService.obtenerSesionesActivas(
                mesas.stream().map(Mesa::getId).toList());
        List<MesaDto> resultado = mesas.stream()
                .map(mesa -> {
                    SesionMesa sesion = sesionesActivas.get(mesa.getId());
                    SesionMesaDto sesionDto = sesion == null ? null : SesionMesaDto.desde(sesion);
                    return MesaDto.desde(mesa, sesionDto);
                })
                .toList();
        return ResponseEntity.ok(resultado);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN_RESTAURANTE')")
    public ResponseEntity<MesaDto> crearMesa(@Valid @RequestBody CrearMesaRequest request) {
        Mesa mesa = mesaService.crearMesa(request.codigo(), request.sucursalId());
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
