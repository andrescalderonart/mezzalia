-- V11: pedidos de una mesa (RF-03/RF-04/RF-05/RF-06). `total` se calcula y
-- persiste en el servidor (nunca llega del cliente, ver
-- pedido.servicio.PedidoService.crearPedido). La restriccion UNIQUE sobre
-- (tenant_id, clave_idempotencia) es la garantia REAL de idempotencia
-- (RF-04) ante un doble-tap o un reintento de red del comensal -- no es
-- solo una optimizacion de consulta, es lo que le impide a la aplicacion
-- crear dos pedidos aunque su propia logica de "leer antes de escribir"
-- falle bajo una carrera (ver el comentario largo en PedidoService).
--
-- Sin sucursal_id, mismo motivo ya documentado en Mesa.java (V8): el resto
-- del dominio construido hasta ahora tampoco modela sucursales todavia.

CREATE TABLE pedidos (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    tenant_id           BIGINT         NOT NULL,
    mesa_id             BIGINT         NOT NULL,
    comensal_id         BIGINT         NULL,
    estado              VARCHAR(20)    NOT NULL,
    clave_idempotencia  VARCHAR(36)    NOT NULL,
    notas               VARCHAR(500)   NULL,
    total               DECIMAL(12,2)  NOT NULL,
    creado_en           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en      DATETIME       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pedidos_tenant_clave_idem (tenant_id, clave_idempotencia),
    KEY idx_pedidos_tenant_id (tenant_id),
    KEY idx_pedidos_mesa_id (mesa_id),
    KEY idx_pedidos_estado (estado),
    CONSTRAINT fk_pedidos_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_pedidos_mesa
        FOREIGN KEY (mesa_id) REFERENCES mesas (id),
    CONSTRAINT fk_pedidos_comensal
        FOREIGN KEY (comensal_id) REFERENCES comensales (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
