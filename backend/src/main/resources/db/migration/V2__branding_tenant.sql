-- V2: branding personalizado por tenant
-- Permite que cada restaurante configure su logo y paleta de colores,
-- consultados por app-cliente al escanear el QR de la mesa antes de cargar el menú.

ALTER TABLE tenants
    ADD COLUMN logo_url         VARCHAR(500)  NULL COMMENT 'URL del logo del restaurante (CDN o almacenamiento externo)',
    ADD COLUMN color_primario   VARCHAR(7)    NULL COMMENT 'Color primario en formato #RRGGBB',
    ADD COLUMN color_secundario VARCHAR(7)    NULL COMMENT 'Color secundario en formato #RRGGBB';