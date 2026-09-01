-- V8: mesas fisicas del restaurante. RF-08 depende de esta tabla via
-- sesiones_mesa (V9). Sin sucursal_id por ahora -- ver Mesa.java para el
-- motivo: el resto del dominio (categorias_menu/items_menu) tampoco modela
-- sucursales todavia, es una simplificacion consistente con lo ya
-- construido, no un olvido.

CREATE TABLE mesas (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT        NOT NULL,
    codigo      VARCHAR(50)   NOT NULL,
    activa      TINYINT(1)    NOT NULL DEFAULT 1,
    creado_en   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_mesas_tenant_id (tenant_id),
    CONSTRAINT fk_mesas_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
