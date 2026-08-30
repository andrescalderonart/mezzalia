package com.mezzalia.tenant.controlador;

import com.mezzalia.tenant.dto.BrandingDto;
import com.mezzalia.tenant.servicio.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TenantAdminController {

    private final TenantService tenantService;

    @PatchMapping("/{id}/branding")
    public ResponseEntity<Void> actualizarBranding(
            @PathVariable Long id,
            @RequestBody BrandingDto branding) {

        tenantService.actualizarBranding(
                id,
                branding.logoUrl(),
                branding.colorPrimario(),
                branding.colorSecundario()
        );
        return ResponseEntity.noContent().build();
    }
}