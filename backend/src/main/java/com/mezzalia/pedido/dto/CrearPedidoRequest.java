package com.mezzalia.pedido.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Endpoint publico, sin auth (el token de la URL es la unica credencial) --
 * @Size(max=30) en items NO es una decision arbitraria de UI: sin este
 * limite, ItemSolicitadoRequest.cantidad (Min(1)/Max(50) por item) no
 * impedia mandar una lista con miles de entradas distintas en un solo POST,
 * cada una disparando una consulta a MenuService.obtenerItemDisponibleParaPedido
 * dentro de PedidoService.crearPedido -- superficie de abuso real contra un
 * endpoint sin auth ni rate-limit por payload. 30 items distintos en un
 * pedido ya cubre una mesa grande pidiendo cosas distintas cada uno; el
 * limite de repeticion de pedidos completos (no de items) vive en
 * PedidoService.crearPedido (ver LIMITE_PEDIDOS_POR_VENTANA).
 */
public record CrearPedidoRequest(
        @NotBlank @Size(max = 36) String claveIdempotencia,
        @NotEmpty @Size(max = 30) @Valid List<ItemSolicitadoRequest> items,
        @Size(max = 500) String notas,
        @Valid ComensalRequest comensal,
        // "Pedido de la mesa": apodo opcional de quien pide (ver V24).
        @Size(max = 40) String apodo
) {
    /** Sin apodo (la forma que ya usaba todo el codigo). */
    public CrearPedidoRequest(String claveIdempotencia, List<ItemSolicitadoRequest> items, String notas, ComensalRequest comensal) {
        this(claveIdempotencia, items, notas, comensal, null);
    }
}
