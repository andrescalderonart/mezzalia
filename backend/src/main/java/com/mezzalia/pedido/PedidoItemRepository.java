package com.mezzalia.pedido;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PedidoItemRepository extends JpaRepository<PedidoItem, Long> {
    List<PedidoItem> findByPedidoId(Long pedidoId);

    /**
     * Reporte "que quitan tus clientes" (2026-11-27): lineas de pedidos NO
     * cancelados del tenant desde una fecha. La sucursal, igual que en
     * PedidoRepository, se resuelve via la mesa del pedido.
     */
    @Query("SELECT pi FROM PedidoItem pi WHERE pi.tenantId = :tenantId AND pi.pedidoId IN "
            + "(SELECT p.id FROM Pedido p WHERE p.tenantId = :tenantId AND p.creadoEn >= :desde "
            + "AND p.estado <> com.mezzalia.pedido.EstadoPedido.CANCELADO "
            + "AND p.estado <> com.mezzalia.pedido.EstadoPedido.POR_CONFIRMAR)")
    List<PedidoItem> lineasDesde(@Param("tenantId") Long tenantId, @Param("desde") LocalDateTime desde);

    @Query("SELECT pi FROM PedidoItem pi WHERE pi.tenantId = :tenantId AND pi.pedidoId IN "
            + "(SELECT p.id FROM Pedido p WHERE p.tenantId = :tenantId AND p.creadoEn >= :desde "
            + "AND p.estado <> com.mezzalia.pedido.EstadoPedido.CANCELADO "
            + "AND p.estado <> com.mezzalia.pedido.EstadoPedido.POR_CONFIRMAR AND p.mesaId IN "
            + "(SELECT m.id FROM Mesa m WHERE m.tenantId = :tenantId AND m.sucursalId = :sucursalId))")
    List<PedidoItem> lineasDesdeEnSucursal(@Param("tenantId") Long tenantId, @Param("desde") LocalDateTime desde,
                                           @Param("sucursalId") Long sucursalId);
}
