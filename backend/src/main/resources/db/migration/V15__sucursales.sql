-- V15: RF-10 (documentacion-tecnica.md §7) -- "Un tenant puede tener 1 o
-- mas sucursales; cada sucursal tiene su propia carta, mesas y pedidos,
-- pero comparte el tenant y la facturacion." Hasta esta migracion, ninguna
-- tabla del dominio modelaba sucursales -- ver el aviso de alcance real en
-- Sucursal.java: esta migracion crea la tabla y siembra una sucursal por
-- defecto para cada tenant EXISTENTE (para que Mesa, en V16, tenga a que
-- apuntar sin dejar filas huerfanas), pero categorias_menu/items_menu/
-- pedidos no se tocan todavia.
--
-- Backfill de "Sucursal Principal" para cada tenant existente, ANTES de que
-- cualquier fila nueva pueda necesitarla (V16 depende de que esto ya haya
-- corrido). direccion NULL a proposito: no hay ningun dato de direccion
-- existente en el sistema del que inferirla.

CREATE TABLE sucursales (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT        NOT NULL,
    nombre      VARCHAR(150)  NOT NULL,
    direccion   VARCHAR(255)  NULL,
    activa      TINYINT(1)    NOT NULL DEFAULT 1,
    creado_en   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_sucursales_tenant_id (tenant_id),
    CONSTRAINT fk_sucursales_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sucursales (tenant_id, nombre, activa)
SELECT id, 'Sucursal Principal', 1 FROM tenants;
