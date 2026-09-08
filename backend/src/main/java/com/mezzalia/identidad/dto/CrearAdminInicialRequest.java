package com.mezzalia.identidad.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta del primer usuario ADMIN_RESTAURANTE de un tenant recien creado
 * (SUPER_ADMIN, onboarding de un restaurante). El tenantId NO viaja aca --
 * viene del path (POST /api/admin/tenants/{tenantId}/admin-inicial), mismo
 * criterio que el resto de TenantAdminController (branding/suspender/
 * reactivar): nunca confiar en un tenant que el cliente podria manipular en
 * el body cuando el path ya lo deja sin ambiguedad. Ver el javadoc de
 * UsuarioInternoAdminService.crearAdminInicial para el porque de este
 * endpoint (antes de que existiera, no habia NINGUN camino de API para dar
 * de alta el primer usuario de un tenant nuevo).
 */
public record CrearAdminInicialRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(min = 8, max = 100) String password
) {}
