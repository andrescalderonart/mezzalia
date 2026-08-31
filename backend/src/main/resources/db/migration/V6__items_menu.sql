-- V6: items de la carta (platos)
-- precio en DECIMAL, nunca FLOAT/DOUBLE (es dinero). RF-05: el precio del
-- pedido siempre se recalcula en servidor contra esta tabla; el payload del
-- cliente nunca es fuente de verdad.
-- Borrado de un item referenciado por pedidos historicos = soft delete
-- (activo=false), nunca DELETE fisico (documentacion-tecnica.md §8), para no
-- romper la integridad de pedidos pasados.

CREATE TABLE items_menu (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id      BIGINT         NOT NULL,
    categoria_id   BIGINT         NOT NULL,
    nombre         VARCHAR(150)   NOT NULL,
    descripcion    VARCHAR(1000)  NULL,
    precio         DECIMAL(12,2)  NOT NULL,
    imagen_url     VARCHAR(500)   NULL,
    disponible     TINYINT(1)     NOT NULL DEFAULT 1,
    activo         TINYINT(1)     NOT NULL DEFAULT 1,
    creado_en      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_items_menu_tenant_id (tenant_id),
    KEY idx_items_menu_categoria_id (categoria_id),
    CONSTRAINT fk_items_menu_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_items_menu_categoria
        FOREIGN KEY (categoria_id) REFERENCES categorias_menu (id),
    CONSTRAINT chk_items_menu_precio_no_negativo CHECK (precio >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
