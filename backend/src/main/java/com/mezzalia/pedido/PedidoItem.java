package com.mezzalia.pedido;

import com.mezzalia.tenant.EntidadConTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Linea de un pedido. `nombreItem` y `precioUnitario` son una FOTO tomada
 * en el momento de crear el pedido (RF-05), no una referencia viva a
 * ItemMenu -- si el restaurante despues cambia el nombre o el precio del
 * plato en la carta (o incluso lo da de baja con soft-delete), este pedido
 * historico sigue mostrando exactamente lo que el comensal pidio y pago.
 * `itemMenuId` se conserva igual como referencia, solo para trazabilidad
 * (ej. reportes de "platos mas pedidos"), nunca para releer el precio actual.
 */
@Entity
@Table(name = "pedido_items")
@Getter
@Setter
public class PedidoItem extends EntidadConTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @Column(name = "item_menu_id", nullable = false)
    private Long itemMenuId;

    @Column(name = "nombre_item", nullable = false, length = 150)
    private String nombreItem;

    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    @Column(nullable = false)
    private int cantidad;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    /**
     * Ingredientes que el comensal quito ("sin cebolla"), separados por salto
     * de linea. Es una COPIA de los nombres al momento del pedido, como
     * nombreItem/precioUnitario: renombrar o borrar el ingrediente despues no
     * cambia lo que se pidio. null = el plato tal cual.
     */
    @Column(name = "sin_ingredientes", length = 500)
    private String sinIngredientes;
}
