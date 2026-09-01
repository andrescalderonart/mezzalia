package com.mezzalia.mesa.servicio;

import com.mezzalia.mesa.Mesa;
import com.mezzalia.mesa.MesaRepository;
import com.mezzalia.mesa.SesionMesa;
import com.mezzalia.mesa.SesionMesaRepository;
import com.mezzalia.mesa.excepcion.MesaExceptions;
import com.mezzalia.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MesaService {

    /**
     * RF-08/§9: 4 horas es un margen razonable para un servicio de
     * restaurante (almuerzo o cena completos) sin dejar el QR utilizable
     * indefinidamente si alguien lo fotografía y se lo lleva.
     * documentacion-tecnica.md deja esto como una decisión de producto
     * explícita, no un número "correcto" universal -- este es el valor de
     * partida, fácil de ajustar si el negocio decide otra cosa.
     */
    private static final long HORAS_VIGENCIA_SESION = 4;

    private final MesaRepository mesaRepository;
    private final SesionMesaRepository sesionMesaRepository;

    @Transactional(readOnly = true)
    public List<Mesa> listarMesas() {
        return mesaRepository.findByActivaTrueOrderByCodigoAsc();
    }

    @Transactional
    public Mesa crearMesa(String codigo) {
        Mesa mesa = new Mesa();
        mesa.setTenantId(tenantActual());
        mesa.setCodigo(codigo);
        return mesaRepository.save(mesa);
    }

    @Transactional
    public void desactivarMesa(Long mesaId) {
        Mesa mesa = obtenerMesa(mesaId);
        mesa.setActiva(false);
        mesaRepository.save(mesa);
    }

    /**
     * Genera una sesión (y por lo tanto un QR) nueva para la mesa. Invalida
     * explícitamente cualquier sesión previa todavía vigente: regenerar el
     * QR es, por diseño, la forma de "revocar" un QR físico perdido o
     * fotografiado sin querer -- si no se invalidara la anterior, imprimir
     * un QR nuevo no serviría de nada frente a ese riesgo (§9).
     */
    @Transactional
    public SesionMesa generarSesion(Long mesaId) {
        Mesa mesa = obtenerMesa(mesaId);
        LocalDateTime ahora = LocalDateTime.now();

        List<SesionMesa> vigentes = sesionMesaRepository.findByMesaIdAndExpiraEnAfter(mesaId, ahora);
        vigentes.forEach(sesion -> {
            sesion.setExpiraEn(ahora);
            sesionMesaRepository.save(sesion);
        });

        SesionMesa sesion = new SesionMesa();
        sesion.setTenantId(mesa.getTenantId());
        sesion.setMesaId(mesa.getId());
        sesion.setToken(UUID.randomUUID().toString());
        sesion.setExpiraEn(ahora.plusHours(HORAS_VIGENCIA_SESION));
        return sesionMesaRepository.save(sesion);
    }

    /**
     * Camino PÚBLICO -- sin JWT, sin TenantContext poblado todavía. A
     * propósito NO pasa por obtenerMesa()/tenantActual() (que comparan
     * contra TenantContext): acá el token ES la credencial, el tenant se
     * descubre a partir de él, no al revés. Ver TenantFilterActivator para
     * lo que hace el llamador con el tenant ya resuelto.
     */
    @Transactional(readOnly = true)
    public SesionMesa resolverSesionValida(String token) {
        return sesionMesaRepository.findByTokenAndExpiraEnAfter(token, LocalDateTime.now())
                .orElseThrow(MesaExceptions.SesionMesaInvalidaException::new);
    }

    /**
     * Misma defensa explícita que MenuService.obtenerCategoria/obtenerItem:
     * no confiar solo en el filtro de Hibernate para una carga por id, y
     * devolver siempre "no encontrado" (nunca 403) para no confirmarle a un
     * atacante que el recurso existe en otro tenant.
     */
    private Mesa obtenerMesa(Long id) {
        Mesa mesa = mesaRepository.findById(id)
                .filter(Mesa::isActiva)
                .orElseThrow(() -> new MesaExceptions.MesaNoEncontradaException(id));
        Long tenantActual = TenantContext.obtenerTenantId();
        if (tenantActual == null || !tenantActual.equals(mesa.getTenantId())) {
            throw new MesaExceptions.MesaNoEncontradaException(id);
        }
        return mesa;
    }

    private Long tenantActual() {
        Long tenantId = TenantContext.obtenerTenantId();
        if (tenantId == null) {
            throw new MesaExceptions.SinTenantEnContextoException();
        }
        return tenantId;
    }
}
