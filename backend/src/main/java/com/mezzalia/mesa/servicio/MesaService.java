package com.mezzalia.mesa.servicio;

import com.mezzalia.mesa.Mesa;
import com.mezzalia.mesa.MesaRepository;
import com.mezzalia.mesa.SesionMesa;
import com.mezzalia.mesa.SesionMesaRepository;
import com.mezzalia.mesa.excepcion.MesaExceptions;
import com.mezzalia.sucursal.SucursalContext;
import com.mezzalia.sucursal.excepcion.SucursalExceptions;
import com.mezzalia.sucursal.servicio.SucursalService;
import com.mezzalia.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final SucursalService sucursalService;

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
     *
     * RF-10 PR4: ahora ADEMAS acota por sucursalActual() -- ver el javadoc
     * de ese metodo. MesaRepository.findByTenantIdAndActivaTrueOrderByCodigoAsc
     * reemplaza a la vieja findByActivaTrueOrderByCodigoAsc() (sin
     * tenantId): esta query ya no depende UNICAMENTE del filtro de
     * Hibernate para el aislamiento de tenant tampoco, cerrando el mismo
     * tipo de gap que SucursalRepository ya habia cerrado antes.
     */
    @Transactional(readOnly = true)
    public List<Mesa> listarMesas() {
        Long tenantId = tenantActual();
        Long sucursalId = sucursalActual();
        if (sucursalId != null) {
            return mesaRepository.findByTenantIdAndSucursalIdAndActivaTrueOrderByCodigoAsc(tenantId, sucursalId);
        }
        return mesaRepository.findByTenantIdAndActivaTrueOrderByCodigoAsc(tenantId);
    }

    /**
     * Unicidad de `codigo` por tenant, solo entre mesas ACTIVAS (RF-09).
     *
     * El chequeo de aplicacion (leer-antes-de-escribir) de abajo sigue
     * existiendo por UX -- falla rapido con un mensaje claro sin esperar a
     * un catch -- pero YA NO es la unica defensa. El backstop real es la
     * restriccion UNIQUE(tenant_id, codigo_unico) agregada en
     * V14__mesas_unicidad_codigo_activo.sql: ver el comentario largo en
     * Mesa.java sobre por que vive sobre una columna espejo
     * (`codigoUnico`, mantenida a mano, null cuando la mesa esta
     * desactivada) y no sobre `codigo` directamente ni sobre un indice
     * funcional de MySQL 8 -- se prioriza que el mismo UNIQUE KEY se
     * pueda generar tambien desde la entidad JPA para el perfil h2 de
     * desarrollo/tests, igual que Pedido.claveIdempotencia.
     *
     * Esto cierra una condicion de carrera real (dos altas simultaneas del
     * mismo codigo para el mismo tenant) que antes solo dependia del
     * chequeo de aplicacion de abajo -- documentada en su momento como
     * bajo riesgo por ser una accion administrativa de ritmo humano, pero
     * bajo riesgo no es cero riesgo, y el costo de cerrarla de verdad era
     * bajo. Se cierra al auditar el codigo, no porque el riesgo percibido
     * haya cambiado.
     */
    @Transactional
    public Mesa crearMesa(String codigo, Long sucursalId) {
        Long tenantId = tenantActual();
        // RF-10: valida que la sucursal pertenezca de verdad al tenant
        // actual y esté activa ANTES de tocar la unicidad de código --
        // mismo criterio de "no confiar solo en el FK" que el resto de
        // este proyecto (ver el javadoc de Mesa.sucursalId). Lanza
        // SucursalNoEncontradaException (404) si no, nunca deja crear una
        // mesa "huérfana" de una sucursal ajena.
        sucursalService.obtenerSucursalActivaDelTenantActual(sucursalId);
        if (mesaRepository.existsByTenantIdAndCodigoAndActivaTrue(tenantId, codigo)) {
            throw new MesaExceptions.CodigoMesaDuplicadoException(codigo);
        }
        Mesa mesa = new Mesa();
        mesa.setTenantId(tenantId);
        mesa.setSucursalId(sucursalId);
        mesa.setCodigo(codigo);
        mesa.setCodigoUnico(codigo);
        try {
            return mesaRepository.save(mesa);
        } catch (DataIntegrityViolationException ex) {
            // Backstop de BD ante la carrera -- mismo patron que
            // PedidoService.crearPedido/TenantService.crearTenant: no se
            // ejecuta ningun otro statement despues de este catch en esta
            // transaccion, solo se traduce la excepcion de bajo nivel de
            // Spring Data a la misma excepcion de negocio que el chequeo
            // de arriba, y se deja que @Transactional haga rollback
            // completo. El perdedor de la carrera real recibe el mismo
            // 409 que el caso comun, no un 500.
            throw new MesaExceptions.CodigoMesaDuplicadoException(codigo);
        }
    }

    @Transactional
    public void desactivarMesa(Long mesaId) {
        Mesa mesa = obtenerMesa(mesaId);
        mesa.setActiva(false);
        // Libera el codigo para reuso -- ver el comentario largo en
        // Mesa.java y en crearMesa de arriba sobre por que la restriccion
        // UNIQUE real vive sobre codigo_unico (que admite multiples NULL)
        // y no sobre codigo directamente. Sin esta linea, el UNIQUE KEY
        // nuevo bloquearia para siempre la reutilizacion legitima de un
        // codigo de mesa desactivada -- exactamente lo que este diseno
        // esta pensado para seguir permitiendo.
        mesa.setCodigoUnico(null);
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
     * RF-10 PR4: uso exclusivo de PedidoService (sucursalActual()/
     * obtenerPedido) -- Pedido no tiene su propio sucursal_id (decisión
     * explícita de esta rama, ver el javadoc de Pedido.java: la carta y
     * los pedidos siguen siendo tenant-scoped únicamente a nivel de
     * columna), pero para poder acotar qué MESERO ve/opera qué pedidos por
     * sucursal sin agregar esa columna todavía, hace falta poder resolver
     * a qué sucursal pertenece la MESA de un pedido. Devuelve null si la
     * mesa no existe o no pertenece al tenant actual -- el llamador decide
     * qué hacer con eso (ver PedidoService.obtenerPedido: una mesa no
     * resoluble se trata igual que "no pertenece a la sucursal actual",
     * nunca como "sin restricción").
     */
    @Transactional(readOnly = true)
    public Long obtenerSucursalIdDeMesa(Long mesaId) {
        Long tenantId = TenantContext.obtenerTenantId();
        return mesaRepository.findById(mesaId)
                .filter(m -> tenantId != null && tenantId.equals(m.getTenantId()))
                .map(Mesa::getSucursalId)
                .orElse(null);
    }

    /**
     * Misma defensa explícita que MenuService.obtenerCategoria/obtenerItem:
     * no confiar solo en el filtro de Hibernate para una carga por id, y
     * devolver siempre "no encontrado" (nunca 403) para no confirmarle a un
     * atacante que el recurso existe en otro tenant.
     *
     * RF-10 PR4: ADEMAS valida sucursalActual() cuando corresponde (MESERO
     * con sucursal seleccionada, o ADMIN_RESTAURANTE enfocado en una) --
     * sin esto, un MESERO de la sucursal A podía, conociendo o adivinando
     * el id, desactivar una mesa o regenerar el QR de una mesa de la
     * sucursal B del MISMO tenant: exactamente el caso de prueba que
     * docs/propuesta-multisucursal-rf10.md §7 pide cerrar ("un MESERO de
     * la sucursal A no puede leer ni escribir mesas/pedidos de la sucursal
     * B"). Cuando sucursalActual() es null (ADMIN_RESTAURANTE sin
     * enfocar), este chequeo es un no-op: comportamiento idéntico al que
     * existía antes de esta rama.
     */
    private Mesa obtenerMesa(Long id) {
        Mesa mesa = mesaRepository.findById(id)
                .filter(Mesa::isActiva)
                .orElseThrow(() -> new MesaExceptions.MesaNoEncontradaException(id));
        Long tenantActual = TenantContext.obtenerTenantId();
        if (tenantActual == null || !tenantActual.equals(mesa.getTenantId())) {
            throw new MesaExceptions.MesaNoEncontradaException(id);
        }
        Long sucursalId = sucursalActual();
        if (sucursalId != null && !sucursalId.equals(mesa.getSucursalId())) {
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

    /**
     * RF-10 PR4 (docs/propuesta-multisucursal-rf10.md §4): "sucursalActual()"
     * propio de este servicio, no un @Filter global de sucursal -- ver
     * sucursal.SucursalContext para el porqué (un filtro global que hay
     * que desactivar según el rol ya generó un bug real en este proyecto
     * para tenant/SUPER_ADMIN, ver el javadoc de listarMesas). Devuelve
     * null cuando el rol autenticado puede ver TODAS las sucursales del
     * tenant (ADMIN_RESTAURANTE sin sucursal seleccionada, igual que
     * siempre); devuelve un id concreto para acotar la query cuando
     * corresponde. MESERO SIEMPRE debe tener una sucursal seleccionada
     * para llegar hasta acá -- si su JWT no trae el claim, es que nunca
     * seleccionó una tras el login (o perdió la selección en el último
     * /api/auth/refresh, ver AuthService.emitirPar), y se lo bloquea con
     * un 409 explícito en vez de mostrarle silenciosamente las mesas de
     * TODAS las sucursales del tenant (la fuga exacta que este diseño
     * existe para evitar) o una lista vacía sin explicación.
     */
    private Long sucursalActual() {
        if (SucursalContext.requiereSeleccion() && SucursalContext.obtenerSucursalId() == null) {
            throw new SucursalExceptions.SucursalNoSeleccionadaException();
        }
        return SucursalContext.obtenerSucursalId();
    }
}
