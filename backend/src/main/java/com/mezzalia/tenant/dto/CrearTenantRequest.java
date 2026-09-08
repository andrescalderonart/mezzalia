package com.mezzalia.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de un tenant nuevo (SUPER_ADMIN). A proposito NO incluye branding
 * (logo/colores) -- eso queda para PATCH /branding. Este endpoint resuelve
 * unicamente lo que TenantYaExisteException ya modelaba sin tener quien la
 * lanzara: que no se puedan crear dos tenants con el mismo nombre.
 *
 * Tampoco incluye la creacion de un usuario ADMIN_RESTAURANTE inicial --
 * pero a diferencia de lo que este comentario decia antes, eso YA NO es
 * porque ese CRUD no existiera: existe (UsuarioInternoAdminController) desde
 * la implementacion de RF-09, pero esta detras de
 * @PreAuthorize("hasRole('ADMIN_RESTAURANTE')") y siempre resuelve el
 * tenant desde el JWT del que llama, asi que SUPER_ADMIN no puede usarlo
 * para el primer usuario de un tenant que acaba de crear. Ese es un
 * segundo paso deliberadamente separado: ver
 * TenantAdminInicialController/UsuarioInternoAdminService.crearAdminInicial
 * (POST /api/admin/tenants/{tenantId}/admin-inicial) para el endpoint que
 * cierra ese hueco, y su javadoc para el porque no vive aca mismo (modulo
 * `tenant` no puede depender de `identidad`).
 */
public record CrearTenantRequest(
        @NotBlank @Size(max = 255) String nombre,
        @NotBlank @Size(max = 100) String plan,
        @NotBlank @Size(max = 10) String moneda,
        @NotBlank @Size(max = 100) String zonaHoraria
) {}
