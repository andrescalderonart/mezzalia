package com.mezzalia.menu;

import com.mezzalia.tenant.EntidadConTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Un ingrediente de un plato, para la vista "desarmada" de la carta y para
 * que el comensal pueda quitarlo (ver V23__ingredientes_quitables.sql).
 * La sucursal se hereda del ItemMenu (no se duplica la columna).
 */
@Entity
@Table(name = "ingredientes_item")
@Getter
@Setter
public class IngredienteItem extends EntidadConTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "item_menu_id", nullable = false)
    private Long itemMenuId;

    @Column(nullable = false, length = 80)
    private String nombre;

    /** Foto de la capa (idealmente PNG/WEBP sin fondo). Opcional: sin foto se muestra solo el nombre. */
    @Column(name = "imagen_url", length = 500)
    private String imagenUrl;

    /** De arriba hacia abajo en la vista apilada. */
    @Column(nullable = false)
    private int orden;

    /** El pan de la hamburguesa no se quita; la cebolla si. */
    @Column(nullable = false)
    private boolean removible = true;
}
