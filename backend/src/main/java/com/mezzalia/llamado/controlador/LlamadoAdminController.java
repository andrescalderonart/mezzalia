package com.mezzalia.llamado.controlador;

import com.mezzalia.llamado.dto.LlamadoDto;
import com.mezzalia.llamado.servicio.LlamadoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Llamados de las mesas para el panel. Como PedidoAdminController, sin
 * @PreAuthorize: atender mesas es trabajo diario de ADMIN_RESTAURANTE y
 * MESERO por igual. El aislamiento por sucursal lo hace LlamadoService.
 */
@RestController
@RequestMapping("/api/admin/llamados")
@RequiredArgsConstructor
public class LlamadoAdminController {

    private final LlamadoService llamadoService;

    @GetMapping
    public ResponseEntity<List<LlamadoDto>> pendientes() {
        return ResponseEntity.ok(llamadoService.pendientes().stream().map(LlamadoDto::desde).toList());
    }

    @PatchMapping("/{id}/atender")
    public ResponseEntity<LlamadoDto> atender(@PathVariable Long id) {
        return ResponseEntity.ok(LlamadoDto.desde(llamadoService.atender(id)));
    }
}
