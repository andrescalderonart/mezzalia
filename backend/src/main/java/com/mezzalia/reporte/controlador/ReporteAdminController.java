package com.mezzalia.reporte.controlador;

import com.mezzalia.reporte.dto.ReporteQuitadosDto;
import com.mezzalia.reporte.servicio.ReporteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reportes del restaurante. Solo ADMIN_RESTAURANTE (es informacion de negocio). */
@RestController
@RequestMapping("/api/admin/reportes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_RESTAURANTE')")
public class ReporteAdminController {

    private final ReporteService reporteService;

    @GetMapping("/ingredientes-quitados")
    public ResponseEntity<ReporteQuitadosDto> ingredientesQuitados(
            @RequestParam(defaultValue = "30") int dias) {
        // Se ajusta al rango en vez de rechazar: es un filtro de pantalla, no
        // vale la pena un error por pedir 400 dias.
        return ResponseEntity.ok(reporteService.ingredientesQuitados(Math.max(1, Math.min(365, dias))));
    }
}
