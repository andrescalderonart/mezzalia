-- V1: esquema inicial — tabla de tenants y estructura base
-- Estrategia: schema compartido + tenant_id en cada tabla de dominio (ver documentacion-tecnica.md §6)

CREATE TABLE tenants (
    id            BIGINT          NOT NULL AUTO_INCREMENT,
    nombre        VARCHAR(255)    NOT NULL,
    plan          VARCHAR(50)     NOT NULL DEFAULT 'STARTER',
    moneda        VARCHAR(10)     NOT NULL DEFAULT 'COP',
    zona_horaria  VARCHAR(50)     NOT NULL DEFAULT 'America/Bogota',
    estado        VARCHAR(50)     NOT NULL DEFAULT 'ACTIVO',
    creado_en     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tenants_nombre (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;