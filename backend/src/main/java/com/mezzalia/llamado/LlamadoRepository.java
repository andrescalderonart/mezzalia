package com.mezzalia.llamado;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LlamadoRepository extends JpaRepository<Llamado, Long> {

    /** Para no duplicar: si la mesa ya tiene un llamado igual sin atender, se reusa. */
    Optional<Llamado> findFirstByMesaIdAndTipoAndAtendidoEnIsNull(Long mesaId, TipoLlamado tipo);

    /** Backstop de abuso del endpoint publico (mismo criterio que Pedido). */
    long countByMesaIdAndCreadoEnAfter(Long mesaId, LocalDateTime desde);

    List<Llamado> findByTenantIdAndAtendidoEnIsNullOrderByCreadoEnAsc(Long tenantId);

    /** Igual que PedidoRepository: la sucursal se resuelve via Mesa. */
    @Query("SELECT l FROM Llamado l WHERE l.tenantId = :tenantId AND l.atendidoEn IS NULL AND l.mesaId IN "
            + "(SELECT m.id FROM Mesa m WHERE m.tenantId = :tenantId AND m.sucursalId = :sucursalId) "
            + "ORDER BY l.creadoEn ASC")
    List<Llamado> pendientesDeSucursal(@Param("tenantId") Long tenantId, @Param("sucursalId") Long sucursalId);
}
