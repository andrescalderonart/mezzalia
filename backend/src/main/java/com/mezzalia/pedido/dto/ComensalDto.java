package com.mezzalia.pedido.dto;

import com.mezzalia.pedido.Comensal;

/**
 * Solo para el panel autenticado (ver PedidoAdminDto) -- el personal SI
 * necesita ver nombre/telefono para poder contactar al comensal (ej.
 * confirmar un pedido con nota rara, avisar una demora). `telefono` llega
 * aca ya DESCIFRADO: Comensal.getTelefono() pasa por
 * TelefonoEncriptadoConverter de forma transparente al leer la entidad, ver
 * Comensal.java.
 */
public record ComensalDto(String nombre, String telefono) {
    public static ComensalDto desde(Comensal comensal) {
        return new ComensalDto(comensal.getNombre(), comensal.getTelefono());
    }
}
