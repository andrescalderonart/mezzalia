package com.mezzalia.pedido.dto;

import com.mezzalia.pedido.Comensal;
import com.mezzalia.pedido.EstadoPedido;
import com.mezzalia.pedido.Pedido;
import com.mezzalia.pedido.PedidoItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoAdminDto(
        Long id,
        Long mesaId,
        EstadoPedido estado,
        BigDecimal total,
        String notas,
        List<PedidoItemDto> items,
        ComensalDto comensal,
        LocalDateTime creadoEn,
        LocalDateTime actualizadoEn
) {
    public static PedidoAdminDto desde(Pedido pedido, List<PedidoItem> items, Comensal comensal) {
        List<PedidoItemDto> itemsDto = items.stream().map(PedidoItemDto::desde).toList();
        ComensalDto comensalDto = comensal != null ? ComensalDto.desde(comensal) : null;
        return new PedidoAdminDto(
                pedido.getId(), pedido.getMesaId(), pedido.getEstado(), pedido.getTotal(), pedido.getNotas(),
                itemsDto, comensalDto, pedido.getCreadoEn(), pedido.getActualizadoEn());
    }
}
