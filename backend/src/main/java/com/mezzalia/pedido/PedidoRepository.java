package com.mezzalia.pedido;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /**
     * Backstop de idempotencia (RF-04) -- ver PedidoService.crearPedido
     * para el porque de tenantId explicito aca ademas del filtro de
     * Hibernate: esta consulta respalda directamente la restriccion UNIQUE
     * (tenant_id, clave_idempotencia) de V11__pedidos.sql.
     */
    Optional<Pedido> findByTenantIdAndClaveIdempotencia(Long tenantId, String claveIdempotencia);

    List<Pedido> findByEstadoOrderByCreadoEnAsc(EstadoPedido estado);

    List<Pedido> findAllByOrderByCreadoEnAsc();
}
