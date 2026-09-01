package com.mezzalia.pedido.dto;

import com.mezzalia.pedido.EstadoPedido;
import com.mezzalia.pedido.Pedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Contrato PUBLICO (respuesta de POST /api/publico/mesas/{token}/pedidos) --
 * a proposito sin datos de Comensal: el comensal ya sabe lo que escribio en
 * su propio formulario, no necesita que el backend se lo confirme, y este
 * DTO es tambien lo primero que ve cualquiera que intercepte la respuesta de
 * un endpoint sin autenticacion. Ver PedidoAdminDto para el contrato
 * separado del panel autenticado (mismo criterio que ItemPublicoDto vs
 * ItemMenuDto).
 */
public record PedidoDto(
        Long id,
        Long mesaId,
        EstadoPedido estado,
        BigDecimal total,
        String notas,
        List<PedidoItemDto> items,
        LocalDateTime creadoEn
) {
    public static PedidoDto desde(Pedido pedido, List<PedidoItemDto> items) {
        return new PedidoDto(
                pedido.getId(), pedido.getMesaId(), pedido.getEstado(), pedido.getTotal(),
                pedido.getNotas(), items, pedido.getCreadoEn());
    }
}
