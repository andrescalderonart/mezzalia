-- V23: ingredientes por plato que el comensal puede ver "desarmados" y
-- quitar ("sin cebolla") desde la carta (2026-11-26). Tres cambios:
--
-- 1) ingredientes_item: cada ingrediente de un plato, con su foto (una capa
--    por ingrediente, sin fondo idealmente) y si se puede quitar o no (el pan
--    de una hamburguesa no se quita). `orden` es de arriba hacia abajo en la
--    vista apilada. Borrado fisico: los pedidos guardan una COPIA del nombre
--    (ver pedido_items.sin_ingredientes), asi que borrar un ingrediente no
--    altera el historial.
-- 2) items_menu.presentacion_ingredientes: APILADO (hamburguesas, sandwiches:
--    las capas se separan hacia arriba) o ESPARCIDO (bandejas, ensaladas: los
--    ingredientes se muestran alrededor).
-- 3) pedido_items.sin_ingredientes: los nombres quitados, separados por
--    salto de linea, copiados en el momento del pedido (igual que
--    nombre_item y precio_unitario ya son una copia).
--
-- Mismo alcance de verificacion honesto que V15-V22: revisado a mano contra
-- MySQL 8, no ejecutado aqui (los tests usan H2 desde las entidades).

CREATE TABLE ingredientes_item (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT       NOT NULL,
    item_menu_id  BIGINT       NOT NULL,
    nombre        VARCHAR(80)  NOT NULL,
    imagen_url    VARCHAR(500) NULL,
    orden         INT          NOT NULL DEFAULT 0,
    removible     TINYINT(1)   NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_ingredientes_item (item_menu_id, orden),
    KEY idx_ingredientes_tenant (tenant_id),
    CONSTRAINT fk_ingredientes_tenant FOREIGN KEY (tenant_id)    REFERENCES tenants (id),
    CONSTRAINT fk_ingredientes_item   FOREIGN KEY (item_menu_id) REFERENCES items_menu (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE items_menu
    ADD COLUMN presentacion_ingredientes VARCHAR(20) NOT NULL DEFAULT 'APILADO' AFTER modelo_usdz_url;

ALTER TABLE pedido_items
    ADD COLUMN sin_ingredientes VARCHAR(500) NULL AFTER subtotal;
