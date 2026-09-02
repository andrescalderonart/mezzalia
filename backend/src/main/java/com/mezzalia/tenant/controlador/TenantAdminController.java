package com.mezzalia.tenant.controlador;

import com.mezzalia.tenant.Tenant;
import com.mezzalia.tenant.dto.BrandingDto;
import com.mezzalia.tenant.dto.CrearTenantRequest;
import com.mezzalia.tenant.dto.TenantDto;
import com.mezzalia.tenant.servicio.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TenantAdminController {

    private final TenantService tenantService;

    /**
     * Alta de un tenant nuevo. 201 + el tenant creado (TenantDto), a
     * diferencia de branding/suspender/reactivar (204) porque aca el
     * llamador necesita el id generado -- no lo tiene todavia, no lo puede
     * pasar en la URL como en esos otros casos.
     */
    @PostMapping
    public ResponseEntity<TenantDto> crearTenant(@Valid @RequestBody CrearTenantRequest request) {
        Tenant tenant = tenantService.crearTenant(
                request.nombre(),
                request.plan(),
                request.moneda(),
                request.zonaHoraria()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(TenantDto.desde(tenant));
    }

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

    /**
     * Suspender/reactivar son idempotentes (ver el javadoc de
     * TenantService.suspenderTenant): PATCH sin cuerpo, 204 siempre que el
     * tenant exista, sin importar el estado en el que ya estuviera.
     */
    @PatchMapping("/{id}/suspender")
    public ResponseEntity<Void> suspenderTenant(@PathVariable Long id) {
        tenantService.suspenderTenant(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/reactivar")
    public ResponseEntity<Void> reactivarTenant(@PathVariable Long id) {
        tenantService.reactivarTenant(id);
        return ResponseEntity.noContent().build();
    }
}