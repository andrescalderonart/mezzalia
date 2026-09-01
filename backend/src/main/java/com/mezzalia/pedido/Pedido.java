package com.mezzalia.pedido;

import com.mezzalia.tenant.EntidadConTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RF-03/RF-04/RF-05: un pedido de una mesa. `total` se calcula y persiste
 * en el servidor a partir de PedidoItem.subtotal (nunca se recibe del
 * cliente) -- ver PedidoService.crearPedido. `claveIdempotencia` es el UUID
 * que genera el frontend (crypto.randomUUID()) antes de enviar el POST; la
 * restriccion UNIQUE (tenant_id, clave_idempotencia) en V11__pedidos.sql es
 * la garantia real de que un doble-tap o un reintento de red nunca crea dos
 * pedidos -- ver el comentario largo en PedidoService.crearPedido.
 *
 * Sin `sucursal_id` por el mismo motivo documentado en Mesa.java: el resto
 * del dominio ya construido tampoco modela sucursales todavia.
 *
 * uniqueConstraints declarado aca en la entidad (no solo en
 * V11__pedidos.sql) a proposito: el perfil `h2` de desarrollo local NO
 * corre Flyway (ver application-h2.yml), genera el esquema directo desde
 * las entidades con ddl-auto -- sin esto, la garantia real de idempotencia
 * (RF-04, ver PedidoService.crearPedido) simplemente no existiria en local,
 * solo en MySQL/produccion, dejando un vacio de pruebas exactamente en el
 * mecanismo mas sensible de este modulo.
 */
@Entity
@Table(name = "pedidos", uniqueConstraints = @UniqueConstraint(
        name = "uq_pedidos_tenant_clave_idem", columnNames = {"tenant_id", "clave_idempotencia"}))
@Getter
@Setter
public class Pedido extends EntidadConTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "mesa_id", nullable = false)
    private Long mesaId;

    /** Nullable a proposito: sin consentimiento del comensal (RF-13), un
     * pedido puede existir perfectamente sin ningun dato personal asociado. */
    @Column(name = "comensal_id")
    private Long comensalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPedido estado;

    @Column(name = "clave_idempotencia", nullable = false, length = 36)
    private String claveIdempotencia;

    @Column(length = 500)
    private String notas;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @PrePersist
    private void alCrear() {
        LocalDateTime ahora = LocalDateTime.now();
        if (creadoEn == null) {
            creadoEn = ahora;
        }
        actualizadoEn = ahora;
    }

    @PreUpdate
    private void alActualizar() {
        actualizadoEn = LocalDateTime.now();
    }
}
