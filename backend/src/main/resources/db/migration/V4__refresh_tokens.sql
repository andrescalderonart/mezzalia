-- V4: refresh tokens — necesarios para poder revocar sesiones (logout,
-- detección de reuso/robo) en vez de un JWT de larga duración imposible de
-- invalidar antes de su expiración natural (documentacion-tecnica.md §5.2
-- pide explícitamente refresh token "rotable").
--
-- Se guarda el HASH del token, nunca el valor en texto plano: si esta tabla
-- se filtra, no debe ser directamente utilizable (mismo principio que
-- password_hash en usuarios_internos).

CREATE TABLE refresh_tokens (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    usuario_id    BIGINT        NOT NULL,
    token_hash    VARCHAR(255)  NOT NULL,
    expira_en     DATETIME      NOT NULL,
    revocado      TINYINT(1)    NOT NULL DEFAULT 0,
    creado_en     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_refresh_tokens_token_hash (token_hash),
    KEY idx_refresh_tokens_usuario_id (usuario_id),
    CONSTRAINT fk_refresh_tokens_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuarios_internos (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
