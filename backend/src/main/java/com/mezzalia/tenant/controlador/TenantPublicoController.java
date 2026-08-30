package com.mezzalia.tenant.controlador;

import com.mezzalia.tenant.Tenant;
import com.mezzalia.tenant.dto.BrandingDto;
import com.mezzalia.tenant.servicio.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/publico/tenants")
@RequiredArgsConstructor
public class TenantPublicoController {

    private final TenantService tenantService;

    /**
     * Consultado por app-cliente al escanear el QR de la mesa, antes de cargar el menú.
     * No requiere autenticación: el tenantId llega de la sesión de mesa resuelta por el QR.
     */
    @GetMapping("/{tenantId}/branding")
    public ResponseEntity<BrandingDto> obtenerBranding(@PathVariable Long tenantId) {
        Tenant tenant = tenantService.obtenerPorId(tenantId);
        return ResponseEntity.ok(new BrandingDto(
                tenant.getLogoUrl(),
                tenant.getColorPrimario(),
                tenant.getColorSecundario()
        ));
    }
}