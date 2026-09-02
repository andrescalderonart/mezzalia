package com.mezzalia.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de un tenant nuevo (SUPER_ADMIN). A proposito NO incluye branding
 * (logo/colores) ni la creacion de un usuario ADMIN_RESTAURANTE inicial --
 * eso queda para PATCH /branding y para el CRUD de usuarios, que todavia no
 * existe (ver el comentario de SeedSuperAdminConfig sobre por que ese CRUD
 * esta fuera de alcance por ahora). Este endpoint resuelve unicamente lo que
 * TenantYaExisteException ya modelaba sin tener quien la lanzara: que no se
 * puedan crear dos tenants con el mismo nombre.
 */
public record CrearTenantRequest(
        @NotBlank @Size(max = 255) String nombre,
        @NotBlank @Size(max = 100) String plan,
        @NotBlank @Size(max = 10) String moneda,
        @NotBlank @Size(max = 100) String zonaHoraria
) {}
