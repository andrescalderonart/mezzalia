package com.mezzalia.menu;

import com.mezzalia.tenant.EntidadConTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * RF-12: "Todo cambio de precio en la carta queda registrado (quién, cuándo,
 * valor anterior/nuevo)". Es un log de auditoría -- solo se inserta, nunca se
 * edita ni se borra desde la aplicación, siempre dentro de la misma
 * transacción que el UPDATE de ItemMenu.precio (ver MenuService).
 */
@Entity
@Table(name = "historial_precio_item")
@Getter
@Setter
public class HistorialPrecioItem extends EntidadConTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(nullable = false, length = 100)
    private String usuario;

    @Column(name = "precio_anterior", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioAnterior;

    @Column(name = "precio_nuevo", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioNuevo;

    @Column(name = "modificado_en", nullable = false, updatable = false)
    private LocalDateTime modificadoEn;

    @PrePersist
    private void alCrear() {
        if (modificadoEn == null) {
            modificadoEn = LocalDateTime.now();
        }
    }
}
