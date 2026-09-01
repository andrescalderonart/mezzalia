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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Log de auditoria de cambios de estado de un pedido -- mismo criterio que
 * menu.HistorialPrecioItem: solo se inserta, nunca se edita ni se borra,
 * siempre dentro de la misma transaccion que el UPDATE de Pedido.estado
 * (ver PedidoService.cambiarEstado/crearPedido).
 *
 * `estadoAnterior` es nullable a proposito: la primera fila de un pedido
 * (al crearlo, RECIBIDO) no tiene un "antes". `usuario` guarda el username
 * autenticado para un cambio hecho desde el panel (RF-09: puede ser
 * ADMIN_RESTAURANTE o MESERO), o el literal "comensal" para la creacion
 * publica sin JWT (no hay username que registrar en ese camino).
 */
@Entity
@Table(name = "pedido_estado_historial")
@Getter
@Setter
public class PedidoEstadoHistorial extends EntidadConTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 20)
    private EstadoPedido estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 20)
    private EstadoPedido estadoNuevo;

    @Column(nullable = false, length = 150)
    private String usuario;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    private void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
