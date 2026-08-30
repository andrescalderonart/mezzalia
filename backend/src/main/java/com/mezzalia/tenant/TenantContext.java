package com.mezzalia.tenant;

/**
 * Almacena el tenant_id del request en curso usando un ThreadLocal.
 * El módulo identidad es responsable de poblar este contexto al validar el JWT
 * (JwtAuthenticationFilter) y de limpiarlo al finalizar el request.
 * Los servicios de dominio llaman a obtenerTenantId() para aplicar el filtro de
 * aislamiento sin recibir el tenant como parámetro en cada método.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> tenantIdHolder = new ThreadLocal<>();

    private TenantContext() {}

    public static void establecerTenantId(Long tenantId) {
        tenantIdHolder.set(tenantId);
    }

    public static Long obtenerTenantId() {
        return tenantIdHolder.get();
    }

    public static void limpiar() {
        tenantIdHolder.remove();
    }
}
