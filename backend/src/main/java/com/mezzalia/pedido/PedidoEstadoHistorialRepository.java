package com.mezzalia.pedido;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PedidoEstadoHistorialRepository extends JpaRepository<PedidoEstadoHistorial, Long> {
    List<PedidoEstadoHistorial> findByPedidoIdOrderByCreadoEnAsc(Long pedidoId);
}
