-- V3: usuarios internos (identidad) — login del panel restaurante y super-admin
-- RF-09: admin_restaurante edita carta; mesero solo ve/actualiza pedidos.
-- tenant_id es NULL únicamente para SUPER_ADMIN (equipo Mezzalia, sin restaurante).
-- El CHECK constraint es la misma regla que ya se valida en @PrePersist/@PreUpdate
-- de UsuarioInterno.java — a propósito duplicada: la aplicación puede tener bugs,
-- la base de datos es la última línea de defensa (§6, §10).

CREATE TABLE usuarios_internos (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id      BIGINT        NULL,
    username       VARCHAR(100)  NOT NULL,
    password_hash  VARCHAR(255)  NOT NULL,
    rol            VARCHAR(30)   NOT NULL,
    activo         TINYINT(1)    NOT NULL DEFAULT 1,
    creado_en      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_usuarios_internos_username (username),
    KEY idx_usuarios_internos_tenant_id (tenant_id),
    CONSTRAINT fk_usuarios_internos_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT chk_usuarios_internos_tenant_por_rol CHECK (
        (rol = 'SUPER_ADMIN' AND tenant_id IS NULL)
        OR (rol <> 'SUPER_ADMIN' AND tenant_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
