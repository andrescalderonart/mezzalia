package com.mezzalia.tenant.servicio;

import com.mezzalia.common.excepcion.DomainExceptions;
import com.mezzalia.tenant.Tenant;
import com.mezzalia.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class TenantService {

    private static final Pattern PATRON_COLOR_HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public Tenant obtenerPorId(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new DomainExceptions.TenantNoEncontradoException(tenantId));
    }

    @Transactional
    public void actualizarBranding(Long tenantId, String logoUrl, String colorPrimario, String colorSecundario) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new DomainExceptions.TenantNoEncontradoException(tenantId));

        validarColorHex(colorPrimario);
        validarColorHex(colorSecundario);

        tenant.setLogoUrl(logoUrl);
        tenant.setColorPrimario(colorPrimario);
        tenant.setColorSecundario(colorSecundario);

        tenantRepository.save(tenant);
    }

    private void validarColorHex(String color) {
        if (color != null && !PATRON_COLOR_HEX.matcher(color).matches()) {
            throw new DomainExceptions.ColorHexInvalidoException(color);
        }
    }
}