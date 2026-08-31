-- V5: categorias de la carta (menu)
-- Aislado por tenant_id como toda entidad de negocio (documentacion-tecnica.md
-- §6). Soft delete via `activo`: una categoria nunca se borra fisicamente
-- porque items existentes podrian seguir referenciandola.

CREATE TABLE categorias_menu (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT        NOT NULL,
    nombre      VARCHAR(150)  NOT NULL,
    orden       INT           NOT NULL DEFAULT 0,
    activo      TINYINT(1)    NOT NULL DEFAULT 1,
    creado_en   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_categorias_menu_tenant_id (tenant_id),
    CONSTRAINT fk_categorias_menu_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
