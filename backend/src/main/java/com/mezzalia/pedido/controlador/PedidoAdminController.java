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
 * pedidos -- es el trabajo diario del mesero en el salon. Por eso, a
 * diferencia de MenuAdminController/MesaAdminController, NINGUN endpoint de
 * este controlador lleva @PreAuthorize("hasRole('ADMIN_RESTAURANTE')"): la
 * autenticacion que ya exige SecurityConfig para /api/admin/** alcanza para
 * ambos roles del tenant.
 */
@RestController
@RequestMapping("/api/admin/pedidos")
@RequiredArgsConstructor
public class PedidoAdminController {

    private final PedidoService pedidoService;

    /**
     * Sin WebSocket (ver documentacion-tecnica.md, roadmap): el panel
     * consulta este endpoint por polling cada 5-8s (ver PedidosPage.jsx en
     * app-admin) en vez de mantener una conexion persistente -- mas simple
     * de operar en un solo VPS sin infraestructura extra, al costo de
     * hasta ~8s de latencia percibida en ver un pedido nuevo. Aceptado
     * explicitamente para el alcance actual.
     *
     * N+1 conocido y aceptado: por cada pedido se hace una query de items y
     * otra de comensal. Para el tamaño esperado (un restaurante, pedidos
     * activos de un turno) esto no es un problema real; si la lista crece
     * mucho (reportes historicos, por ejemplo) esto necesitaria un fetch
     * por lote en vez de por pedido -- no es la necesidad de hoy.
     */
    @GetMapping
    public ResponseEntity<List<PedidoAdminDto>> listarPedidos(
            @RequestParam(required = false) EstadoPedido estado) {
        List<Pedido> pedidos = pedidoService.listarPedidos(estado);
        List<PedidoAdminDto> dtos = pedidos.stream()
                .map(pedido -> {
                    List<PedidoItem> items = pedidoService.listarItems(pedido.getId());
                    Comensal comensal = pedidoService.obtenerComensal(pedido.getComensalId());
                    return PedidoAdminDto.desde(pedido, items, comensal);
                })
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<PedidoAdminDto> cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest request) {
        Pedido pedido = pedidoService.cambiarEstado(id, request.estado());
        List<PedidoItem> items = pedidoService.listarItems(pedido.getId());
        Comensal comensal = pedidoService.obtenerComensal(pedido.getComensalId());
        return ResponseEntity.ok(PedidoAdminDto.desde(pedido, items, comensal));
    }
}
