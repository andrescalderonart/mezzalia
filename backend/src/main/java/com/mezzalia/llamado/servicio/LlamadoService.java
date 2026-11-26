package com.mezzalia.llamado.servicio;

import com.mezzalia.llamado.Llamado;
import com.mezzalia.llamado.LlamadoRepository;
import com.mezzalia.llamado.TipoLlamado;
import com.mezzalia.llamado.excepcion.LlamadoExceptions;
import com.mezzalia.mesa.Mesa;
import com.mezzalia.mesa.MesaRepository;
import com.mezzalia.sucursal.SucursalContext;
import com.mezzalia.sucursal.excepcion.SucursalExceptions;
import com.mezzalia.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * "Llamar al mesero" y "pedir la cuenta" desde el QR (enfoque en bares,
 * 2026-11-26). Dos caminos, con las mismas defensas que PedidoService:
 *
 * - Publico (comensal anonimo): {@link #llamar} corre SIEMPRE dentro de
 *   TenantFilterActivator.ejecutarConTenant (ver MenuPublicoController), asi
 *   que TenantContext ya esta poblado.
 * - Panel (mesero/admin): {@link #pendientes} y {@link #atender}, con el
 *   mismo patron "sucursalActual()" que PedidoService: un MESERO solo ve y
 *   atiende los llamados de su sucursal.
 */
@Service
@RequiredArgsConstructor
public class LlamadoService {

    // Backstop de abuso del endpoint publico sin auth: mas generoso de lo
    // que una mesa real necesita, mismo criterio que LIMITE_PEDIDOS_POR_VENTANA.
    static final int LIMITE_LLAMADOS_POR_VENTANA = 10;
    static final int MINUTOS_VENTANA_LIMITE = 5;

    private final LlamadoRepository llamadoRepository;
    private final MesaRepository mesaRepository;

    /**
     * Si la mesa ya tiene un llamado IGUAL sin atender, se devuelve ese en
     * vez de crear otro: tocar "llamar al mesero" cinco veces por impaciencia
     * no debe llenar el panel de cinco avisos.
     */
    @Transactional
    public Llamado llamar(Long mesaId, TipoLlamado tipo) {
        Long tenantId = tenantActual();
        Mesa mesa = mesaDelTenant(mesaId, tenantId);

        var existente = llamadoRepository.findFirstByMesaIdAndTipoAndAtendidoEnIsNull(mesa.getId(), tipo);
        if (existente.isPresent()) {
            return existente.get();
        }
        LocalDateTime desde = LocalDateTime.now().minusMinutes(MINUTOS_VENTANA_LIMITE);
        if (llamadoRepository.countByMesaIdAndCreadoEnAfter(mesa.getId(), desde) >= LIMITE_LLAMADOS_POR_VENTANA) {
            throw new LlamadoExceptions.DemasiadosLlamadosException();
        }

        Llamado llamado = new Llamado();
        llamado.setTenantId(tenantId);
        llamado.setMesaId(mesa.getId());
        llamado.setTipo(tipo);
        return llamadoRepository.save(llamado);
    }

    @Transactional(readOnly = true)
    public List<Llamado> pendientes() {
        Long tenantId = tenantActual();
        Long sucursalId = sucursalActual();
        return sucursalId != null
                ? llamadoRepository.pendientesDeSucursal(tenantId, sucursalId)
                : llamadoRepository.findByTenantIdAndAtendidoEnIsNullOrderByCreadoEnAsc(tenantId);
    }

    /** Idempotente: atender uno ya atendido no cambia quien ni cuando lo atendio. */
    @Transactional
    public Llamado atender(Long id) {
        Long tenantId = tenantActual();
        Llamado llamado = llamadoRepository.findById(id)
                .filter(l -> tenantId.equals(l.getTenantId()))
                .orElseThrow(() -> new LlamadoExceptions.LlamadoNoEncontradoException(id));
        Long sucursalId = sucursalActual();
        if (sucursalId != null) {
            Long sucursalDeLaMesa = mesaRepository.findById(llamado.getMesaId()).map(Mesa::getSucursalId).orElse(null);
            if (!sucursalId.equals(sucursalDeLaMesa)) {
                // Mismo "no encontrado" que un id inexistente: no confirmar
                // que existe en otra sucursal.
                throw new LlamadoExceptions.LlamadoNoEncontradoException(id);
            }
        }
        if (llamado.estaPendiente()) {
            llamado.setAtendidoEn(LocalDateTime.now());
            llamado.setAtendidoPor(usuarioActual());
            llamado = llamadoRepository.save(llamado);
        }
        return llamado;
    }

    private Mesa mesaDelTenant(Long mesaId, Long tenantId) {
        // La mesa viene de una SesionMesa ya validada, pero se revalida contra
        // el tenant igual que el resto del proyecto (no confiar solo en el
        // filtro de Hibernate para cargas por id).
        return mesaRepository.findById(mesaId)
                .filter(m -> tenantId.equals(m.getTenantId()))
                .orElseThrow(() -> new IllegalStateException("La mesa de la sesion no pertenece al tenant activo"));
    }

    private Long tenantActual() {
        Long tenantId = TenantContext.obtenerTenantId();
        if (tenantId == null) {
            throw new LlamadoExceptions.SinTenantEnContextoException();
        }
        return tenantId;
    }

    /** Mismo patron (duplicado a proposito) que PedidoService.sucursalActual(). */
    private Long sucursalActual() {
        if (SucursalContext.requiereSeleccion() && SucursalContext.obtenerSucursalId() == null) {
            throw new SucursalExceptions.SucursalNoSeleccionadaException();
        }
        return SucursalContext.obtenerSucursalId();
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "sistema";
    }
}
