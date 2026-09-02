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

    /**
     * Defensa explicita, mismo criterio que crearMesa()/obtenerMesa():
     * este metodo depende del filtro de Hibernate (tenantFilter) para el
     * aislamiento entre tenants, pero ese filtro NUNCA se activa para
     * SUPER_ADMIN (TenantContext vacio a proposito -- ver
     * JwtAuthenticationFilter, "tenantId ausente = SUPER_ADMIN"). Sin este
     * guardia, un token SUPER_ADMIN recibia las mesas de TODOS los
     * restaurantes en una sola lista con GET /api/admin/mesas --
     * tenantActual() lanza si no hay contexto, cerrando ese hueco igual que
     * ya cierra el resto de metodos de escritura de este servicio.
     */
    @Transactional(readOnly = true)
    public List<Mesa> listarMesas() {
        tenantActual();
        return mesaRepository.findByActivaTrueOrderByCodigoAsc();
    }

    /**
     * Unicidad de `codigo` por tenant, solo entre mesas ACTIVAS (RF-09,
     * decision tomada en esta sesion tras encontrar que no existia ninguna
     * restriccion: dos mesas "M1" simultaneas dejarian a un mesero sin
     * forma de distinguir cual QR es cual).
     *
     * Chequeo de aplicacion (leer-antes-de-escribir), NO una restriccion
     * UNIQUE de base de datos como la de Pedido.claveIdempotencia, a
     * proposito, por dos motivos:
     *  1. El alcance real es "unico entre las activas", no "unico para
     *     siempre": no hay endpoint para renombrar el codigo de una mesa,
     *     asi que si se desactiva "M1" y despues se quiere dar de alta una
     *     mesa fisica nueva con esa misma etiqueta, eso deberia poder
     *     hacerse. Una UNIQUE KEY de MySQL no distingue activa/inactiva sin
     *     un indice parcial (que MySQL no soporta de forma nativa como si
     *     lo hace Postgres) -- habria bloqueado esa reutilizacion legitima
     *     para siempre.
     *  2. Esta es una accion administrativa de baja frecuencia y ritmo
     *     humano (un ADMIN_RESTAURANTE dando de alta una mesa desde el
     *     panel), no el endpoint publico sin auth de pedidos -- el perfil
     *     de riesgo de una carrera real aca (dos admins creando "M1" en el
     *     mismo instante) es muchisimo mas bajo que el de RF-04, que si
     *     justifica la garantia dura de una restriccion de base de datos.
     */
    @Transactional
    public Mesa crearMesa(String codigo) {
        Long tenantId = tenantActual();
        if (mesaRepository.existsByTenantIdAndCodigoAndActivaTrue(tenantId, codigo)) {
            throw new MesaExceptions.CodigoMesaDuplicadoException(codigo);
        }
        Mesa mesa = new Mesa();
        mesa.setTenantId(tenantId);
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
