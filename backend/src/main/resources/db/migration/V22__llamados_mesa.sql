-- V22: "llamar al mesero" y "pedir la cuenta" desde el QR de la mesa
-- (enfoque en bares, 2026-11-26: el momento de mas friccion en un bar no es
-- el primer pedido sino la segunda ronda y la cuenta).
--
-- Un llamado es PENDIENTE mientras atendido_en sea NULL -- mismo criterio
-- que Turno.fin: el dato es la fuente de verdad, sin columna de estado
-- aparte. No guarda datos del comensal (es anonimo, como el pedido sin
-- consentimiento): solo que la mesa X pidio Y a tal hora.
--
-- Mismo alcance de verificacion honesto que V15-V21: sintaxis revisada a
-- mano contra MySQL 8; los tests corren en H2 con el esquema generado desde
-- las entidades, asi que este archivo NO corre en CI. Validar con
-- `docker compose up -d mysql` antes de produccion.

CREATE TABLE llamados_mesa (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    tenant_id     BIGINT      NOT NULL,
    mesa_id       BIGINT      NOT NULL,
    tipo          VARCHAR(20) NOT NULL,
    creado_en     DATETIME    NOT NULL,
    atendido_en   DATETIME    NULL,
    atendido_por  VARCHAR(100) NULL,
    PRIMARY KEY (id),
    KEY idx_llamados_tenant_pendientes (tenant_id, atendido_en),
    KEY idx_llamados_mesa_tipo (mesa_id, tipo, atendido_en),
    CONSTRAINT fk_llamados_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_llamados_mesa   FOREIGN KEY (mesa_id)   REFERENCES mesas (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
