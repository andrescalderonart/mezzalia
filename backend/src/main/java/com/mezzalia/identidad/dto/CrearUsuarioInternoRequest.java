package com.mezzalia.identidad.dto;

import com.mezzalia.identidad.RolUsuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * `tenantId` a propósito NO viaja acá -- se resuelve siempre desde
 * TenantContext (ver UsuarioInternoAdminService.tenantActual()), mismo
 * criterio que crearMesa/crearCategoria/crearItem en el resto del sistema:
 * nunca confiar en un tenant que el cliente podría manipular en el body.
 * `rol` sí es parte del request (a diferencia de esos otros casos) porque
 * ADMIN_RESTAURANTE necesita poder decidir si el usuario nuevo es otro
 * ADMIN_RESTAURANTE o un MESERO -- el servicio valida que nunca sea
 * SUPER_ADMIN (ver UsuarioInternoExceptions.RolNoPermitidoException).
 */
public record CrearUsuarioInternoRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotNull RolUsuario rol
) {}
