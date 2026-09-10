package com.mezzalia.pedido.controlador;

import com.mezzalia.pedido.Comensal;
import com.mezzalia.pedido.EstadoPedido;
import com.mezzalia.pedido.Pedido;
import com.mezzalia.pedido.PedidoItem;
import com.mezzalia.pedido.dto.CambiarEstadoRequest;
import com.mezzalia.pedido.dto.PedidoAdminDto;
import com.mezzalia.pedido.servicio.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RF-09: tanto ADMIN_RESTAURANTE como MESERO pueden ver y actualizar
 * pedidos -- es el trabajo diario del mesero en el salón. Por eso, a
 * diferencia de MenuAdminController/MesaAdminController, NINGÚN endpoint de
 * este controlador lleva @PreAuthorize("hasRole('ADMIN_RESTAURANTE')"): la
 * autenticación que ya exige SecurityConfig para /api/admin/** alcanza para
 * ambos roles del tenant.
 *
 * Eso NO significa que ambos roles reciban el mismo payload: §10 de
 * documentacion-tecnica.md exige que el teléfono del comensal solo se vea
 * completo para ADMIN_RESTAURANTE. Por eso los dos endpoints que arman un
 * PedidoAdminDto calculan {@link #telefonoCompleto()} a partir del rol
 * autenticado y se lo pasan explícito a PedidoAdminDto.desde() -- el
 * gating no es por endpoint (@PreAuthorize), es por campo dentro de la
 * misma respuesta.
 */
@RestController
@RequestMapping("/api/admin/pedidos")
@RequiredArgsConstructor
public class PedidoAdminController {

    private static final String AUTORIDAD_ADMIN_RESTAURANTE = "ROLE_ADMIN_RESTAURANTE";

    private final PedidoService pedidoService;

    /**
     * Sin WebSocket (ver documentacion-tecnica.md, roadmap): el panel
     * consulta este endpoint por polling cada 5-8s (ver PedidosPage.jsx en
     * app-admin) en vez de mantener una conexión persistente -- más simple
     * de operar en un solo VPS sin infraestructura extra, al costo de
     * hasta ~8s de latencia percibida en ver un pedido nuevo. Aceptado
     * explícitamente para el alcance actual.
     *
     * N+1 conocido y aceptado: por cada pedido se hace una query de items y
     * otra de comensal. Para el tamaño esperado (un restaurante, pedidos
     * activos de un turno) esto no es un problema real; si la lista crece
     * mucho (reportes históricos, por ejemplo) esto necesitaría un fetch
     * por lote en vez de por pedido -- no es la necesidad de hoy.
     */
    @GetMapping
    public ResponseEntity<List<PedidoAdminDto>> listarPedidos(
            @RequestParam(required = false) EstadoPedido estado) {
        boolean telefonoCompleto = telefonoCompleto();
        List<Pedido> pedidos = pedidoService.listarPedidos(estado);
        List<PedidoAdminDto> dtos = pedidos.stream()
                .map(pedido -> {
                    List<PedidoItem> items = pedidoService.listarItems(pedido.getId());
                    Comensal comensal = pedidoService.obtenerComensal(pedido.getComensalId());
                    return PedidoAdminDto.desde(pedido, items, comensal, telefonoCompleto);
                })
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<PedidoAdminDto> cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest request) {
        Pedido pedido = pedidoService.cambiarEstado(id, request.estado());
        List<PedidoItem> items = pedidoService.listarItems(pedido.getId());
        Comensal comensal = pedidoService.obtenerComensal(pedido.getComensalId());
        return ResponseEntity.ok(PedidoAdminDto.desde(pedido, items, comensal, telefonoCompleto()));
    }

    /**
     * true solo para ADMIN_RESTAURANTE. Default seguro es false: un rol
     * nuevo que en el futuro también pueda autenticarse contra
     * /api/admin/** (hoy solo existen ADMIN_RESTAURANTE y MESERO llegando
     * hasta acá -- SUPER_ADMIN no tiene tenant y PedidoService.tenantActual()
     * lo bloquea antes de llegar a este punto) empieza viendo el teléfono
     * enmascarado hasta que alguien decida explícitamente lo contrario,
     * nunca al revés.
     *
     * Visibilidad de paquete (no private) a propósito: PedidoAdminControllerTest
     * la llama directo, mismo patrón que el resto del proyecto prefiere
     * (instanciar la clase real y probar el método, en vez de mockear
     * SecurityContextHolder a través de una capa HTTP completa que este
     * proyecto todavía no monta en tests).
     */
    boolean telefonoCompleto() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority autoridad : auth.getAuthorities()) {
            if (AUTORIDAD_ADMIN_RESTAURANTE.equals(autoridad.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
