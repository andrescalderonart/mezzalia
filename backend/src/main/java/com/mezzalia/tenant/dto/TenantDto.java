package com.mezzalia.tenant.dto;

import com.mezzalia.tenant.Tenant;

public record TenantDto(Long id, String nombre, String plan, String moneda, String zonaHoraria, String estado) {
    public static TenantDto desde(Tenant tenant) {
        return new TenantDto(
                tenant.getId(),
                tenant.getNombre(),
                tenant.getPlan(),
                tenant.getMoneda(),
                tenant.getZonaHoraria(),
                tenant.getEstado()
        );
    }
}
