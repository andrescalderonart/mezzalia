package com.mezzalia.menu;

import com.mezzalia.tenant.EntidadConTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ítem (plato) de la carta. `precio` es BigDecimal a propósito -- nunca
 * double/float: es dinero (RF-05, RF-12). Soft delete via `activo`: un
 * pedido histórico puede seguir referenciando este ítem, así que nunca se
 * borra físicamente (documentacion-tecnica.md §8).
 *
 * `sucursalId` (2026-09-10): SIEMPRE igual al sucursalId de su
 * CategoriaMenu -- se hereda de la categoría en MenuService.crearItem, no
 * se recibe ni se asigna de forma independiente. No hay FK compuesta que
 * lo exija a nivel de base de datos (mismo criterio que la unicidad de
 * código de mesa: invariante de aplicación, no de esquema); si alguna vez
 * se encuentra un item cuyo sucursalId no coincide con el de su categoría,
 * es un bug de MenuService, no un caso válido a soportar.
 */
@Entity
@Table(name = "items_menu")
@Getter
@Setter
public class ItemMenu extends EntidadConTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    @Column(name = "categoria_id", nullable = false)
    private Long categoriaId;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 1000)
    private String descripcion;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @Column(name = "imagen_url", length = 500)
    private String imagenUrl;

    @Column(nullable = false)
    private boolean disponible = true;

    @Column(nullable = false)
    private boolean activo = true;

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
