package com.mezzalia.pedido.dto;

import jakarta.validation.constraints.Size;

/**
 * RF-13/Habeas Data: todo opcional. `consentimiento` debe ser true para que
 * PedidoService persista cualquier dato aca -- ver
 * PedidoService.crearComensalSiAplica. El texto real de aviso de privacidad
 * que deberia acompañar este checkbox sigue pendiente de revision legal
 * (documentacion-tecnica.md §15.2); esto es solo el mecanismo.
 */
public record ComensalRequest(
        @Size(max = 150) String nombre,
        @Size(max = 30) String telefono,
        boolean consentimiento
) {}
