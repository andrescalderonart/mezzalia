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

    /**
     * Uso exclusivo de caminos PÚBLICOS/de cliente final (ej. branding vía QR,
     * carta). Un tenant suspendido (RF-11) no debe seguir siendo servido acá:
     * lanza TenantSuspendidoException en vez de devolver sus datos.
     *
     * NO usar este método para pantallas de administración de tenants (ej. un
     * futuro "ver/reactivar tenant" del super-admin) — para eso, usar
     * {@link #obtenerParaAdministracion(Long)}, que sí necesita poder leer un
     * tenant suspendido para poder reactivarlo.
     */
    @Transactional(readOnly = true)
    public Tenant obtenerPorId(Long tenantId) {
        Tenant tenant = obtenerParaAdministracion(tenantId);
        if (tenant.estaSuspendido()) {
            throw new DomainExceptions.TenantSuspendidoException(tenantId);
        }
        return tenant;
    }

    /**
     * Lectura sin chequeo de suspensión, para uso interno/administrativo
     * (super-admin) donde precisamente hace falta poder ver un tenant
     * suspendido para gestionarlo (ej. reactivarlo).
     */
    @Transactional(readOnly = true)
    public Tenant obtenerParaAdministracion(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new DomainExceptions.TenantNoEncontradoException(tenantId));
    }

    @Transactional
    public void actualizarBranding(Long tenantId, String logoUrl, String colorPrimario, String colorSecundario) {
        Tenant tenant = obtenerParaAdministracion(tenantId);

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
