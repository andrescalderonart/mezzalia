package com.mezzalia.pedido.servicio;

import com.mezzalia.menu.ItemMenu;
import com.mezzalia.menu.servicio.MenuService;
import com.mezzalia.pedido.Comensal;
import com.mezzalia.pedido.ComensalRepository;
import com.mezzalia.pedido.EstadoPedido;
import com.mezzalia.pedido.Pedido;
import com.mezzalia.pedido.PedidoEstadoHistorial;
import com.mezzalia.pedido.PedidoEstadoHistorialRepository;
import com.mezzalia.pedido.PedidoItem;
import com.mezzalia.pedido.PedidoItemRepository;
import com.mezzalia.pedido.PedidoRepository;
import com.mezzalia.pedido.dto.ComensalRequest;
import com.mezzalia.pedido.dto.ItemSolicitadoRequest;
import com.mezzalia.pedido.excepcion.PedidoExceptions;
import com.mezzalia.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PedidoService {

    /**
     * RF-06: un pedido no salta estados ni retrocede. ENTREGADO y CANCELADO
     * son terminales -- ni siquiera ADMIN_RESTAURANTE puede "revivir" un
     * pedido cancelado desde aca; si eso llega a ser un requisito real, es
     * una decision de producto nueva sobre esta tabla, no un bug de ella.
     */
    private static final Map<EstadoPedido, Set<EstadoPedido>> TRANSICIONES_VALIDAS = crearTransicionesValidas();

    private static Map<EstadoPedido, Set<EstadoPedido>> crearTransicionesValidas() {
        Map<EstadoPedido, Set<EstadoPedido>> mapa = new EnumMap<>(EstadoPedido.class);
        mapa.put(EstadoPedido.RECIBIDO, EnumSet.of(EstadoPedido.EN_PREPARACION, EstadoPedido.CANCELADO));
        mapa.put(EstadoPedido.EN_PREPARACION, EnumSet.of(EstadoPedido.LISTO, EstadoPedido.CANCELADO));
        mapa.put(EstadoPedido.LISTO, EnumSet.of(EstadoPedido.ENTREGADO, EstadoPedido.CANCELADO));
        mapa.put(EstadoPedido.ENTREGADO, EnumSet.noneOf(EstadoPedido.class));
        mapa.put(EstadoPedido.CANCELADO, EnumSet.noneOf(EstadoPedido.class));
        return mapa;
    }

    private final PedidoRepository pedidoRepository;
    private final PedidoItemRepository pedidoItemRepository;
    private final PedidoEstadoHistorialRepository historialRepository;
    private final ComensalRepository comensalRepository;
    private final MenuService menuService;

    /**
     * RF-04 (idempotencia) + RF-05 (recalculo de precio en servidor).
     *
     * Se ejecuta SIEMPRE dentro de TenantFilterActivator.ejecutarConTenant
     * (ver MenuPublicoController.crearPedido) -- este metodo asume que
     * TenantContext ya esta poblado, mismo contrato que MenuService/MesaService.
     *
     * Idempotencia -- que SI garantiza y que NO garantiza, sin adornos:
     * primero se busca un pedido existente con esta clave para este tenant
     * (findByTenantIdAndClaveIdempotencia). Si existe, se devuelve tal cual
     * -- el caso normal de "el cliente reintento porque el primer POST se
     * quedo sin respuesta pero si se habia guardado". Si no existe, se
     * intenta crear. Ese chequeo de "leer, despues escribir" por si solo NO
     * cierra una carrera real entre dos requests casi simultaneos con la
     * misma clave -- lo que SI la cierra es la restriccion UNIQUE
     * (tenant_id, clave_idempotencia) de V11__pedidos.sql: la base de datos
     * garantiza que, pase lo que pase, jamas van a existir dos filas de
     * Pedido con la misma clave para el mismo tenant. Lo que la base de
     * datos NO garantiza es que el perdedor de esa carrera reciba en su
     * PRIMERA respuesta el pedido del ganador -- el perdedor recibe
     * PedidoDuplicadoException (409) y debe reintentar el mismo POST; en
     * ese segundo intento, el chequeo de arriba ya va a encontrar la fila
     * del ganador (para entonces con commit confirmado) y la devuelve, sin
     * crear un duplicado. Se opto por este trade-off (backstop de BD +
     * mensaje claro de "reintenta") en vez de una transaccion anidada
     * (REQUIRES_NEW) que "autosane" ese caso limite en la primera respuesta:
     * ese patron tiene sus propias sutilezas de aislamiento entre motores de
     * BD, y este entorno no tiene forma de ejecutar la suite de tests real
     * (sin Maven disponible) para verificarlo antes de confiar en el. La
     * propiedad que SI importa (nunca un pedido duplicado) queda cerrada
     * igual por la restriccion de BD, no por este metodo.
     */
    @Transactional
    public Pedido crearPedido(Long mesaId, String claveIdempotencia, List<ItemSolicitadoRequest> itemsSolicitados,
                               String notas, ComensalRequest comensalRequest) {
        Long tenantId = tenantActual();

        Pedido existente = pedidoRepository.findByTenantIdAndClaveIdempotencia(tenantId, claveIdempotencia)
                .orElse(null);
        if (existente != null) {
            return existente;
        }

        if (itemsSolicitados == null || itemsSolicitados.isEmpty()) {
            throw new PedidoExceptions.PedidoSinItemsException();
        }

        Long comensalId = crearComensalSiAplica(tenantId, comensalRequest);

        Pedido pedido = new Pedido();
        pedido.setTenantId(tenantId);
        pedido.setMesaId(mesaId);
        pedido.setComensalId(comensalId);
        pedido.setEstado(EstadoPedido.RECIBIDO);
        pedido.setClaveIdempotencia(claveIdempotencia);
        pedido.setNotas(notas);

        List<PedidoItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ItemSolicitadoRequest solicitado : itemsSolicitados) {
            // RF-05: el precio SIEMPRE sale de ItemMenu.precio en este
            // instante, nunca del cliente -- ItemSolicitadoRequest ni
            // siquiera tiene un campo de precio (ver esa clase): esto no es
            // solo "no confiar en el precio del cliente", es que
            // estructuralmente no hay ningun precio del cliente que ignorar.
            ItemMenu item = menuService.obtenerItemDisponibleParaPedido(solicitado.itemMenuId());

            PedidoItem pedidoItem = new PedidoItem();
            pedidoItem.setTenantId(tenantId);
            pedidoItem.setItemMenuId(item.getId());
            pedidoItem.setNombreItem(item.getNombre());
            pedidoItem.setPrecioUnitario(item.getPrecio());
            pedidoItem.setCantidad(solicitado.cantidad());
            BigDecimal subtotal = item.getPrecio().multiply(BigDecimal.valueOf(solicitado.cantidad()));
            pedidoItem.setSubtotal(subtotal);

            items.add(pedidoItem);
            total = total.add(subtotal);
        }
        pedido.setTotal(total);

        try {
            pedido = pedidoRepository.save(pedido);
        } catch (DataIntegrityViolationException ex) {
            // Backstop de BD ante la carrera -- ver el comentario del
            // metodo. Esto SI es portable entre motores (a diferencia de un
            // patron que intentara seguir usando la misma transaccion
            // despues del error, lo cual PostgreSQL no permite sin un
            // SAVEPOINT): no se ejecuta ningun otro statement en esta
            // transaccion despues de este catch, solo se traduce la
            // excepcion de bajo nivel de Spring Data a una de negocio clara
            // y se deja que @Transactional haga rollback completo (incluido
            // el INSERT de Comensal de mas arriba, si lo hubo -- no queda
            // un Comensal huerfano del lado perdedor de la carrera). El
            // reintento del cliente parte de una transaccion nueva, que si
            // vera el commit ya confirmado del ganador.
            throw new PedidoExceptions.PedidoDuplicadoException();
        }

        for (PedidoItem pedidoItem : items) {
            pedidoItem.setPedidoId(pedido.getId());
        }
        pedidoItemRepository.saveAll(items);

        registrarCambioEstado(pedido, null, EstadoPedido.RECIBIDO, "comensal");

        return pedido;
    }

    @Transactional(readOnly = true)
    public List<Pedido> listarPedidos(EstadoPedido estado) {
        if (estado != null) {
            return pedidoRepository.findByEstadoOrderByCreadoEnAsc(estado);
        }
        return pedidoRepository.findAllByOrderByCreadoEnAsc();
    }

    @Transactional(readOnly = true)
    public List<PedidoItem> listarItems(Long pedidoId) {
        return pedidoItemRepository.findByPedidoId(pedidoId);
    }

    @Transactional(readOnly = true)
    public Comensal obtenerComensal(Long comensalId) {
        if (comensalId == null) {
            return null;
        }
        return comensalRepository.findById(comensalId).orElse(null);
    }

    /**
     * RF-09: MESERO SI puede cambiar el estado de un pedido -- es su
     * trabajo real en el salon (ver PedidoAdminController, sin
     * @PreAuthorize adicional en este endpoint). Este dominio todavia no
     * modela "mesero asignado a una mesa/turno", asi que cualquier usuario
     * autenticado del tenant puede cambiar el estado de cualquier pedido de
     * ese tenant -- una simplificacion conocida y documentada, no un
     * descuido: no hay ningun RF que pida esa asignacion todavia.
     */
    @Transactional
    public Pedido cambiarEstado(Long pedidoId, EstadoPedido nuevoEstado) {
        Pedido pedido = obtenerPedido(pedidoId);
        EstadoPedido anterior = pedido.getEstado();

        Set<EstadoPedido> permitidos = TRANSICIONES_VALIDAS.getOrDefault(anterior, Set.of());
        if (!permitidos.contains(nuevoEstado)) {
            throw new PedidoExceptions.TransicionEstadoInvalidaException(anterior, nuevoEstado);
        }

        pedido.setEstado(nuevoEstado);
        pedidoRepository.save(pedido);
        registrarCambioEstado(pedido, anterior, nuevoEstado, usuarioActual());
        return pedido;
    }

    private Long crearComensalSiAplica(Long tenantId, ComensalRequest request) {
        // RF-13/Habeas Data: sin consentimiento explicito, NO se persiste
        // ningun dato del comensal -- ni siquiera el nombre solo. Este es
        // el punto real de la aplicacion donde se aplica esa regla, no una
        // validacion de UI que se pueda saltar con una llamada directa a la
        // API. El texto real de aviso de privacidad que deberia acompañar
        // el consentimiento sigue pendiente de revision legal
        // (documentacion-tecnica.md §15.2) -- ver el frontend para el
        // placeholder exacto que se muestra hoy.
        if (request == null || !request.consentimiento()) {
            return null;
        }
        String nombre = blankANull(request.nombre());
        String telefono = blankANull(request.telefono());
        if (nombre == null && telefono == null) {
            return null;
        }
        Comensal comensal = new Comensal();
        comensal.setTenantId(tenantId);
        comensal.setNombre(nombre);
        comensal.setTelefono(telefono);
        return comensalRepository.save(comensal).getId();
    }

    private String blankANull(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    private void registrarCambioEstado(Pedido pedido, EstadoPedido anterior, EstadoPedido nuevo, String usuario) {
        PedidoEstadoHistorial historial = new PedidoEstadoHistorial();
        historial.setTenantId(pedido.getTenantId());
        historial.setPedidoId(pedido.getId());
        historial.setEstadoAnterior(anterior);
        historial.setEstadoNuevo(nuevo);
        historial.setUsuario(usuario);
        historialRepository.save(historial);
    }

    /** Misma defensa explicita que MenuService/MesaService: no confiar solo
     * en el filtro de Hibernate para una carga por id (ver esos comentarios
     * para el porque). Mismo criterio de "no encontrado" generico en vez de
     * 403 para no confirmarle a un atacante que el recurso existe en otro
     * tenant. */
    private Pedido obtenerPedido(Long id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new PedidoExceptions.PedidoNoEncontradoException(id));
        Long tenantActual = TenantContext.obtenerTenantId();
        if (tenantActual == null || !tenantActual.equals(pedido.getTenantId())) {
            throw new PedidoExceptions.PedidoNoEncontradoException(id);
        }
        return pedido;
    }

    private Long tenantActual() {
        Long tenantId = TenantContext.obtenerTenantId();
        if (tenantId == null) {
            throw new PedidoExceptions.SinTenantEnContextoException();
        }
        return tenantId;
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "sistema";
    }
}
