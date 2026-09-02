package com.mezzalia.pedido;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    /**
     * Backstop de abuso (no de idempotencia) para el endpoint publico sin
     * auth -- ver PedidoService.crearPedido/LIMITE_PEDIDOS_POR_VENTANA.
     * Consulta a BD en vez de un contador en memoria a proposito: sin esto,
     * un contador en memoria necesitaria su propia limpieza periodica (o
     * fuga de memoria por mesa_id acumulados para siempre) y ademas dejaria
     * de ser correcto el dia que haya mas de una instancia corriendo --
     * mismo tipo de limitacion ya documentado y aceptado en
     * AlmacenamientoImagenService para el mismo escenario "una sola
     * instancia hoy". Esta consulta no tiene ese problema porque la fuente
     * de verdad ya es la tabla pedidos, no estado nuevo en memoria.
     */
    long countByMesaIdAndCreadoEnAfter(Long mesaId, LocalDateTime desde);
}
