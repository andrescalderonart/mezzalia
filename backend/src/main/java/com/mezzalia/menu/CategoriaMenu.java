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

import java.time.LocalDateTime;

/**
 * Categoría de la carta (ej. "Entradas", "Fuertes", "Postres").
 * Soft delete via `activo`, nunca borrado físico: un ItemMenu podría seguir
 * referenciando esta categoría.
 *
 * `sucursalId` (2026-09-10): cada sucursal tiene una carta totalmente
 * independiente -- decisión de producto, no el modelo "menú maestro +
 * overrides" que también se evaluó. Consecuencia directa: una sucursal
 * nueva arranca con la carta vacía (ver MenuService.duplicarMenuDesdeSucursal
 * para la herramienta que mitiga eso). Todo ItemMenu que referencia una
 * categoría hereda su sucursalId -- nunca se asigna por separado (ver
 * MenuService.crearItem), así que "item y su categoría en sucursales
 * distintas" es estructuralmente imposible, no solo validado.
 */
@Entity
@Table(name = "categorias_menu")
@Getter
@Setter
public class CategoriaMenu extends EntidadConTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false)
    private int orden = 0;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    private void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
