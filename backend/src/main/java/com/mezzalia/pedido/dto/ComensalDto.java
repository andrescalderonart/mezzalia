package com.mezzalia.pedido.dto;

import com.mezzalia.pedido.Comensal;

/**
 * Solo para el panel autenticado (ver PedidoAdminDto) -- el personal SI
 * necesita ver nombre/telefono para poder contactar al comensal (ej.
 * confirmar un pedido con nota rara, avisar una demora). `telefono` llega
 * aca ya DESCIFRADO: Comensal.getTelefono() pasa por
 * TelefonoEncriptadoConverter de forma transparente al leer la entidad, ver
 * Comensal.java.
 *
 * `id` se agrega (documentacion-tecnica.md §10, Habeas Data) para que el
 * panel pueda pedir ComensalAdminController#eliminarDatos(id) sin tener que
 * resolver el id por otro camino -- el personal ya ve este DTO adentro de
 * un pedido concreto al atenderlo, es el punto natural donde identifican de
 * que comensal se trata si alguien pide que borren sus datos.
 */
public record ComensalDto(Long id, String nombre, String telefono) {
    public static ComensalDto desde(Comensal comensal) {
        return new ComensalDto(comensal.getId(), comensal.getNombre(), comensal.getTelefono());
    }
}
