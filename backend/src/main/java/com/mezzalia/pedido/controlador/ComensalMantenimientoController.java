package com.mezzalia.pedido.controlador;

import com.mezzalia.pedido.dto.ReencriptarComensalesRespuesta;
import com.mezzalia.pedido.servicio.ComensalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mantenimiento de la rotacion de COMENSAL_ENCRYPTION_KEY (§10) -- ver el
 * javadoc del procedimiento completo en
 * pedido.seguridad.AesGcmComensalCipher. Bajo /api/superadmin/** (ver
 * SecurityConfig: ya restringido a SUPER_ADMIN a nivel de ruta) con
 * @PreAuthorize explicito ademas, mismo patron de doble defensa que
 * TenantAdminController -- esto rota la clave de TODOS los tenants a la
 * vez, no es una operacion de un ADMIN_RESTAURANTE individual.
 */
@RestController
@RequestMapping("/api/superadmin/comensales")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ComensalMantenimientoController {

    private final ComensalService comensalService;

    /**
     * Se llama UNA vez, manualmente, como parte del procedimiento de
     * rotacion (paso (e) del javadoc de AesGcmComensalCipher) -- no es un
     * endpoint que el frontend de app-admin necesite consumir nunca; es una
     * herramienta operativa para quien administra la infraestructura.
     */
    @PostMapping("/reencriptar")
    public ResponseEntity<ReencriptarComensalesRespuesta> reencriptar() {
        int total = comensalService.reencriptarTodosLosComensales();
        return ResponseEntity.ok(new ReencriptarComensalesRespuesta(total));
    }
}
