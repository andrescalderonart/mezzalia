package com.mezzalia.pedido;

/**
 * RF-06: ciclo de vida de un pedido en cocina/salon. Las transiciones
 * validas entre estos valores viven en PedidoService.TRANSICIONES_VALIDAS,
 * no aca -- un enum no es el lugar para reglas de negocio sobre el ORDEN en
 * que sus valores pueden sucederse.
 */
public enum EstadoPedido {
    /**
     * "Pedido de la mesa" (2026-11-27): todo pedido hecho desde el QR nace
     * aqui y la cocina NO lo ve hasta que el mesero lo confirma (pasa a
     * RECIBIDO). Asi el mesero verifica la mesa, junta lo de todos y filtra
     * pedidos falsos hechos con la foto del QR.
     */
    POR_CONFIRMAR,
    RECIBIDO,
    EN_PREPARACION,
    LISTO,
    ENTREGADO,
    CANCELADO
}
