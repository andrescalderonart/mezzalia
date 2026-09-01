-- V9: sesiones de mesa -- RF-08: el QR de una mesa codifica este token, no
-- el id de la mesa/tenant. `token` es UNIQUE porque la resolucion publica
-- (MesaService.resolverSesionValida) busca por token SIN conocer el tenant
-- todavia -- si no fuera globalmente unico, dos tenants podrian colisionar.
-- `expira_en` implementa el limite de vigencia de documentacion-tecnica.md
-- §9 (QR fotografiado y reusado a distancia deja de funcionar pasado ese
-- tiempo).

CREATE TABLE sesiones_mesa (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT        NOT NULL,
    mesa_id     BIGINT        NOT NULL,
    token       VARCHAR(36)   NOT NULL,
    expira_en   DATETIME      NOT NULL,
    creada_en   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_sesiones_mesa_token (token),
    KEY idx_sesiones_mesa_tenant_id (tenant_id),
    KEY idx_sesiones_mesa_mesa_id (mesa_id),
    CONSTRAINT fk_sesiones_mesa_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_sesiones_mesa_mesa
        FOREIGN KEY (mesa_id) REFERENCES mesas (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
