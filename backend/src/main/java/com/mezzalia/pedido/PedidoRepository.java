package com.mezzalia.pedido;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * @deprecated RF-10 PR4: dependen SOLO del @Filter de Hibernate para el
     * aislamiento entre tenants -- el mismo gap que SucursalRepository/
     * MesaRepository ya cerraron (ver esos archivos), aca sin cerrar
     * todavia a nivel de METODO porque PedidoServiceTest.
     * pedidosSeOrdenanPorFechaDeCreacionAscendente los usa directo contra
     * el repositorio para verificar el estado total de la base (no a
     * traves de PedidoService, que SI ya usa las variantes con tenantId
     * de abajo desde este mismo PR) -- no se borran para no romper ese
     * test sin poder recompilarlo en este entorno. PedidoService.
     * listarPedidos ya NO llama a ninguno de estos dos.
     */
    List<Pedido> findByEstadoOrderByCreadoEnAsc(EstadoPedido estado);

    List<Pedido> findAllByOrderByCreadoEnAsc();

    /** RF-10 PR4: reemplazo tenant-scoped de findAllByOrderByCreadoEnAsc(),
     * usado por PedidoService.listarPedidos. */
    List<Pedido> findByTenantIdOrderByCreadoEnAsc(Long tenantId);

    /** RF-10 PR4: reemplazo tenant-scoped de findByEstadoOrderByCreadoEnAsc(),
     * usado por PedidoService.listarPedidos. */
    List<Pedido> findByTenantIdAndEstadoOrderByCreadoEnAsc(Long tenantId, EstadoPedido estado);

    /**
     * RF-10 PR4: variantes acotadas por sucursal, para cuando
     * PedidoService.sucursalActual() devuelve un id concreto (MESERO con
     * sucursal ya seleccionada, o ADMIN_RESTAURANTE enfocado en una).
     * Pedido no tiene su propia columna sucursal_id (ver el javadoc de
     * Pedido.java -- decisión explícita de esta rama), así que el
     * scoping se resuelve con un JOIN contra Mesa a nivel de JPQL: usar el
     * nombre de la entidad "Mesa" acá NO agrega una dependencia de
     * compilación de este módulo hacia com.mezzalia.mesa (es una cadena
     * de texto que Hibernate resuelve contra su metamodelo en tiempo de
     * ejecución, no un import de clase Java) -- coherente con que Mesa no
     * tiene @ManyToOne hacia Pedido ni viceversa en ningún lado del
     * proyecto.
     */
    @Query("SELECT p FROM Pedido p WHERE p.tenantId = :tenantId AND p.mesaId IN "
            + "(SELECT m.id FROM Mesa m WHERE m.tenantId = :tenantId AND m.sucursalId = :sucursalId) "
            + "ORDER BY p.creadoEn ASC")
    List<Pedido> findByTenantIdAndSucursalIdOrderByCreadoEnAsc(
            @Param("tenantId") Long tenantId, @Param("sucursalId") Long sucursalId);

    @Query("SELECT p FROM Pedido p WHERE p.tenantId = :tenantId AND p.estado = :estado AND p.mesaId IN "
            + "(SELECT m.id FROM Mesa m WHERE m.tenantId = :tenantId AND m.sucursalId = :sucursalId) "
            + "ORDER BY p.creadoEn ASC")
    List<Pedido> findByTenantIdAndEstadoAndSucursalIdOrderByCreadoEnAsc(
            @Param("tenantId") Long tenantId, @Param("estado") EstadoPedido estado, @Param("sucursalId") Long sucursalId);

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

    /**
     * Uso exclusivo de ComensalService.eliminarDatosComensal (Habeas Data,
     * documentacion-tecnica.md §10): antes de borrar un Comensal hace falta
     * desvincular cada pedido que lo referencia (comensal_id es NULLABLE a
     * proposito, ver Pedido.java) -- la FK fk_pedidos_comensal
     * (V11__pedidos.sql) no tiene ON DELETE SET NULL, asi que un DELETE
     * directo sobre comensales fallaria si queda algun pedido apuntandole.
     */
    List<Pedido> findByComensalId(Long comensalId);
}
