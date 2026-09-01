-- V10: datos opcionales del comensal (RF-13/Habeas Data). Se inserta una
-- fila aca UNICAMENTE si hubo consentimiento explicito -- ver
-- pedido.servicio.PedidoService.crearComensalSiAplica. `telefono` guarda un
-- valor CIFRADO (AES-256-GCM, ver pedido.seguridad.TelefonoEncriptadoConverter),
-- nunca el numero en texto plano -- por eso el ancho de columna es mayor al
-- de un telefono normal (base64 de iv+cifrado+tag ocupa mas que el original).

CREATE TABLE comensales (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id   BIGINT        NOT NULL,
    nombre      VARCHAR(150)  NULL,
    telefono    VARCHAR(255)  NULL,
    creado_en   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_comensales_tenant_id (tenant_id),
    CONSTRAINT fk_comensales_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
