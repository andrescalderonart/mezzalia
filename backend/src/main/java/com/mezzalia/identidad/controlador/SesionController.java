package com.mezzalia.identidad.controlador;

import com.mezzalia.identidad.dto.AccessTokenRespuesta;
import com.mezzalia.identidad.dto.SeleccionarSucursalRequest;
import com.mezzalia.identidad.servicio.AuthService;
import com.mezzalia.sucursal.dto.SucursalDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RF-10 PR4 (docs/propuesta-multisucursal-rf10.md §3): "sesión" acá se
 * refiere a la sucursal en la que el usuario autenticado está trabajando
 * en este momento -- NO a la sesión HTTP (este sistema es 100% stateless
 * vía JWT, ver JwtAuthenticationFilter) ni a mesa.SesionMesa (la sesión de
 * UNA MESA para el QR del comensal, un concepto completamente distinto).
 *
 * A propósito bajo /api/admin/** y NO bajo /api/auth/**, aunque
 * conceptualmente está más cerca de login/refresh: SecurityConfig marca
 * /api/auth/** como permitAll() (login/refresh/logout no requieren token
 * previo, por diseño -- ver el comentario de esa clase). Estos dos
 * endpoints SÍ necesitan la identidad de un usuario YA autenticado (vía el
 * JWT del header), así que deben vivir en un prefijo que SecurityConfig
 * exija autenticado a nivel de FILTRO -- no confiar esa garantía solo a un
 * @PreAuthorize de método, más frágil de verificar sin poder correr la
 * suite real en este entorno (sin Maven/JDK disponibles).
 */
@RestController
@RequestMapping("/api/admin/sesion")
@RequiredArgsConstructor
public class SesionController {

    private final AuthService authService;

    @GetMapping("/sucursales-disponibles")
    public ResponseEntity<List<SucursalDto>> sucursalesDisponibles() {
        List<SucursalDto> sucursales = authService.misSucursalesDisponibles().stream()
                .map(SucursalDto::desde)
                .toList();
        return ResponseEntity.ok(sucursales);
    }

    @PostMapping("/sucursal")
    public ResponseEntity<AccessTokenRespuesta> seleccionarSucursal(@Valid @RequestBody SeleccionarSucursalRequest request) {
        return ResponseEntity.ok(authService.seleccionarSucursal(request.sucursalId()));
    }
}
