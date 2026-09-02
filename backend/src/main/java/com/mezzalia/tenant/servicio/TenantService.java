package com.mezzalia.tenant.servicio;

import com.mezzalia.common.excepcion.DomainExceptions;
import com.mezzalia.tenant.Tenant;
import com.mezzalia.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * Alta de un tenant nuevo (SUPER_ADMIN, onboarding de un restaurante).
     * `estado` no se setea aca a proposito: Tenant.antesDeGuardar() ya lo
     * default-ea a ACTIVO cuando es null, asi que fijarlo aca otra vez
     * duplicaria esa decision en dos lugares.
     *
     * Chequeo de aplicacion (leer-antes-de-escribir) para la unicidad de
     * `nombre`, con un catch de DataIntegrityViolationException como
     * backstop real de la restriccion UNIQUE ya existente en la columna
     * (Tenant.nombre, ver el @Column(unique=true)) -- mismo patron de dos
     * capas que Pedido.crearPedido usa para clave_idempotencia: el chequeo
     * de aplicacion cubre el caso comun con un mensaje de negocio claro
     * (TenantYaExisteException, que existia declarada desde antes pero
     * nunca se lanzaba en ningun lado), y la restriccion de BD cierra la
     * carrera real entre dos altas simultaneas con el mismo nombre -- algo
     * mucho mas plausible aca que en Mesa.codigo, porque el super-admin no
     * es la unica fuente posible de un alta futura (ej. un formulario de
     * self-service todavia no construido).
     */
    @Transactional
    public Tenant crearTenant(String nombre, String plan, String moneda, String zonaHoraria) {
        if (tenantRepository.existsByNombre(nombre)) {
            throw new DomainExceptions.TenantYaExisteException(nombre);
        }
        Tenant tenant = new Tenant();
        tenant.setNombre(nombre);
        tenant.setPlan(plan);
        tenant.setMoneda(moneda);
        tenant.setZonaHoraria(zonaHoraria);
        try {
            return tenantRepository.save(tenant);
        } catch (DataIntegrityViolationException ex) {
            throw new DomainExceptions.TenantYaExisteException(nombre);
        }
    }

    /**
     * Suspender/reactivar son idempotentes a proposito (suspender un tenant
     * ya suspendido, o reactivar uno ya activo, es un no-op exitoso, no un
     * error) -- coherente con la semantica de PATCH: el llamador declara el
     * estado final que quiere, no una transicion que pueda fallar por
     * "ya estaba asi". Usan obtenerParaAdministracion (no obtenerPorId) por
     * el mismo motivo documentado ahi: hace falta poder operar sobre un
     * tenant que ya esta suspendido.
     */
    @Transactional
    public void suspenderTenant(Long tenantId) {
        Tenant tenant = obtenerParaAdministracion(tenantId);
        tenant.setEstado(Tenant.ESTADO_SUSPENDIDO);
        tenantRepository.save(tenant);
    }

    @Transactional
    public void reactivarTenant(Long tenantId) {
        Tenant tenant = obtenerParaAdministracion(tenantId);
        tenant.setEstado(Tenant.ESTADO_ACTIVO);
        tenantRepository.save(tenant);
    }
}
