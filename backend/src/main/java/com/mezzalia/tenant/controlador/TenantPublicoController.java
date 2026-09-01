package com.mezzalia.tenant.controlador;

import com.mezzalia.common.excepcion.DomainExceptions;
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
     * Consultado por app-cliente al escanear el QR de la mesa, antes de
     * cargar el menú -- diseño original. En la práctica, desde que
     * MenuPublicoController#obtenerCarta existe, esa respuesta YA incluye el
     * branding embebido (ver menu.dto.CartaPublicaDto) en una sola llamada,
     * así que app-cliente hoy no necesita golpear este endpoint por
     * separado. Se deja funcionando (con el fix de tenant suspendido de
     * abajo, que faltaba) para no romper a quien ya lo esté usando, pero no
     * es el camino recomendado para carta nueva -- ese pasa por
     * tenant_id crudo y secuencial en la URL, enumerable por cualquiera,
     * algo que el diseño de mesa/sesión (RF-08) evita a propósito.
     */
    @GetMapping("/{tenantId}/branding")
    public ResponseEntity<BrandingDto> obtenerBranding(@PathVariable Long tenantId) {
        Tenant tenant = tenantService.obtenerPorId(tenantId);
        // Tenant.estaSuspendido() ya documenta este contrato ("deja de ser
        // servido por los endpoints públicos: carta, branding") pero este
        // endpoint no lo respetaba -- lo único que faltaba para cumplirlo.
        if (tenant.estaSuspendido()) {
            throw new DomainExceptions.TenantSuspendidoException(tenantId);
        }
        return ResponseEntity.ok(new BrandingDto(
                tenant.getLogoUrl(),
                tenant.getColorPrimario(),
                tenant.getColorSecundario()
        ));
    }
}
