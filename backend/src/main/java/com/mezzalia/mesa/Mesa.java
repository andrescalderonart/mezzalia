package com.mezzalia.mesa;

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

import java.time.LocalDateTime;

/**
 * Una mesa física del restaurante. Hoy sin `sucursal_id` propio: el resto
 * del dominio ya implementado (menu.CategoriaMenu/ItemMenu) todavía no
 * modela sucursales -- solo tenant_id -- así que Mesa sigue el mismo nivel
 * de granularidad real en vez de adelantar un concepto (Sucursal) que no
 * existe todavía en ninguna otra parte del código. documentacion-tecnica.md
 * §7 sí modela SUCURSAL explícitamente; si se implementa más adelante, esta
 * clase necesita `sucursal_id` igual que categorias_menu/items_menu -- no
 * es un olvido, es una simplificación deliberada consistente con lo ya
 * construido.
 */
@Entity
@Table(name = "mesas")
@Getter
@Setter
public class Mesa extends EntidadConTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 50)
    private String codigo;

    /** Soft delete -- una SesionMesa/Pedido histórico podría seguir
     * referenciando esta mesa el día que exista el módulo pedidos. */
    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    private void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
