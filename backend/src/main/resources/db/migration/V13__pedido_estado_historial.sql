-- V13: auditoria de cambios de estado de un pedido -- mismo criterio que
-- historial_precio_item (V7): solo se inserta, nunca se edita ni se borra,
-- siempre dentro de la misma transaccion que el UPDATE de pedidos.estado
-- (ver PedidoService.cambiarEstado/crearPedido). estado_anterior es NULL
-- para la primera fila de un pedido (no hay "antes" de RECIBIDO).

CREATE TABLE pedido_estado_historial (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    tenant_id           BIGINT        NOT NULL,
    pedido_id           BIGINT        NOT NULL,
    estado_anterior     VARCHAR(20)   NULL,
    estado_nuevo        VARCHAR(20)   NOT NULL,
    usuario             VARCHAR(150)  NOT NULL,
    creado_en           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_pedido_estado_historial_pedido_id (pedido_id),
    CONSTRAINT fk_pedido_historial_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_pedido_historial_pedido
        FOREIGN KEY (pedido_id) REFERENCES pedidos (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
