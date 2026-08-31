-- V7: auditoria de cambios de precio (RF-12, obligatorio segun
-- documentacion-tecnica.md: "todo cambio de precio en la carta queda
-- registrado - quien, cuando, valor anterior/nuevo"). Se escribe desde
-- MenuService dentro de la MISMA transaccion que actualiza items_menu.precio
-- -- nunca un paso manual separado que alguien pueda olvidar.

CREATE TABLE historial_precio_item (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id       BIGINT        NOT NULL,
    item_id         BIGINT        NOT NULL,
    usuario         VARCHAR(100)  NOT NULL,
    precio_anterior DECIMAL(12,2) NOT NULL,
    precio_nuevo    DECIMAL(12,2) NOT NULL,
    modificado_en   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_historial_precio_item_tenant_id (tenant_id),
    KEY idx_historial_precio_item_item_id (item_id),
    CONSTRAINT fk_historial_precio_item_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_historial_precio_item_item
        FOREIGN KEY (item_id) REFERENCES items_menu (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
