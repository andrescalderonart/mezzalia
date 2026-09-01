package com.mezzalia.pedido;

/**
 * RF-06: ciclo de vida de un pedido en cocina/salon. Las transiciones
 * validas entre estos valores viven en PedidoService.TRANSICIONES_VALIDAS,
 * no aca -- un enum no es el lugar para reglas de negocio sobre el ORDEN en
 * que sus valores pueden sucederse.
 */
public enum EstadoPedido {
    RECIBIDO,
    EN_PREPARACION,
    LISTO,
    ENTREGADO,
    CANCELADO
}
