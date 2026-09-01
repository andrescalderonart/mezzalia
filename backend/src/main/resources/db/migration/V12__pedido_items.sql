-- V12: lineas de un pedido. `nombre_item` y `precio_unitario` son una FOTO
-- del ItemMenu al momento de crear el pedido (RF-05) -- si el restaurante
-- cambia despues el nombre/precio del plato en la carta, o incluso lo da de
-- baja (soft delete), este pedido historico no cambia. item_menu_id se
-- conserva solo como referencia/trazabilidad (ej. reportes de "mas
-- pedidos"), nunca para releer el precio actual.

CREATE TABLE pedido_items (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id           BIGINT         NOT NULL,
    pedido_id           BIGINT         NOT NULL,
    item_menu_id        BIGINT         NOT NULL,
    nombre_item         VARCHAR(150)   NOT NULL,
    precio_unitario     DECIMAL(12,2)  NOT NULL,
    cantidad            INT            NOT NULL,
    subtotal            DECIMAL(12,2)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_pedido_items_tenant_id (tenant_id),
    KEY idx_pedido_items_pedido_id (pedido_id),
    CONSTRAINT fk_pedido_items_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_pedido_items_pedido
        FOREIGN KEY (pedido_id) REFERENCES pedidos (id),
    CONSTRAINT fk_pedido_items_item_menu
        FOREIGN KEY (item_menu_id) REFERENCES items_menu (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
