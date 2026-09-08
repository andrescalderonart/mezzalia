package com.mezzalia.identidad.controlador;

import com.mezzalia.identidad.UsuarioInterno;
import com.mezzalia.identidad.dto.CrearAdminInicialRequest;
import com.mezzalia.identidad.dto.UsuarioInternoDto;
import com.mezzalia.identidad.servicio.UsuarioInternoAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hallazgo posterior al cierre de la auditoria del 2026-09-08 (surge al
 * revisar con Andres si el modelo multi-tenant ya soportaba onboardear
 * restaurantes distintos): unico camino de API para dar de alta el primer
 * ADMIN_RESTAURANTE de un tenant nuevo. Ver el javadoc completo de
 * UsuarioInternoAdminService.crearAdminInicial para el detalle del hallazgo
 * y del porque de cada decision de diseno.
 *
 * Por que este controller vive en el modulo `identidad` y NO en
 * `tenant` (donde vive TenantAdminController, el resto de
 * /api/admin/tenants/**): crea un UsuarioInterno, una entidad de
 * `identidad`, y `tenant` no puede depender de `identidad` sin invertir la
 * direccion de dependencia que ya tiene el resto del sistema (identidad,
 * menu, mesa, sucursal, pedido y turno importan `tenant`; `tenant` no
 * importa a ninguno de ellos). Comparte el mismo prefijo de ruta
 * (/api/admin/tenants) que TenantAdminController a proposito -- desde
 * afuera es un solo recurso ("administracion de tenants"), aunque
 * internamente la implementacion este partida en dos controllers por la
 * regla de dependencia entre modulos. Spring no exige que un
 * @RequestMapping este atado a un unico controller por prefijo, y el
 * requestMatcher de SecurityConfig para "/api/admin/tenants/**" ya cubre
 * esta ruta nueva igual que las demas (doble defensa, mismo criterio que
 * el resto de rutas SUPER_ADMIN).
 *
 * No fusionado dentro de UsuarioInternoAdminController: ese controller
 * tiene @PreAuthorize("hasRole('ADMIN_RESTAURANTE')") a nivel de CLASE y
 * resuelve el tenant siempre via TenantContext -- mezclar un endpoint
 * SUPER_ADMIN ahi obligaria a pisar esa anotacion de clase a nivel de
 * metodo (valido en Spring Security 6, pero innecesariamente fragil y
 * dificil de auditar a simple vista en un area que esta sesion acaba de
 * terminar de endurecer con tests especificos, ver AutorizacionAdminTest).
 * Un controller nuevo, separado y con su propio @PreAuthorize de clase es
 * mas dificil de mezclar por error.
 */
@RestController
@RequestMapping("/api/admin/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class TenantAdminInicialController {

    private final UsuarioInternoAdminService usuarioInternoAdminService;

    @PostMapping("/{tenantId}/admin-inicial")
    public ResponseEntity<UsuarioInternoDto> crearAdminInicial(
            @PathVariable Long tenantId,
            @Valid @RequestBody CrearAdminInicialRequest request) {
        UsuarioInterno usuario = usuarioInternoAdminService.crearAdminInicial(
                tenantId, request.username(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioInternoDto.desde(usuario));
    }
}
