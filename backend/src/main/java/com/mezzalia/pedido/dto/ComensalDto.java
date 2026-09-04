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
 * que comensal se trata si alguien pide que borren sus datos. `id` nunca se
 * enmascara: no es un dato personal en si mismo y el panel lo necesita
 * igual para MESERO tambien (ej. para poder distinguir dos pedidos del
 * mismo comensal), a diferencia de `telefono`.
 *
 * documentacion-tecnica.md §10 exige "acceso restringido por rol (un
 * mesero no necesita ver el telefono completo, un admin_restaurante si)".
 * Por eso no existe un unico `desde(Comensal)` sin mas: el llamador
 * SIEMPRE tiene que decidir explicitamente si quien va a recibir este DTO
 * puede ver el telefono completo -- no hay default implicito "completo"
 * que un caller nuevo pueda heredar sin darse cuenta. Ver
 * PedidoAdminController para el unico punto donde hoy se decide ese
 * booleano segun el rol autenticado.
 */
public record ComensalDto(Long id, String nombre, String telefono) {

    public static ComensalDto desde(Comensal comensal, boolean telefonoCompleto) {
        String telefono = telefonoCompleto ? comensal.getTelefono() : enmascarar(comensal.getTelefono());
        return new ComensalDto(comensal.getId(), comensal.getNombre(), telefono);
    }

    /**
     * Mascara de longitud fija ("***" + ultimos 4 caracteres) en vez de
     * reemplazar caracter por caracter: alcanza para que un MESERO pueda
     * confirmar "es el mismo numero que me dijo el comensal" sin exponer
     * el numero completo, y de paso no delata la longitud real del dato
     * original (un `telefono` de 7 digitos y uno de 12 quedan con el mismo
     * formato de salida). No es un requisito de seguridad criptografica
     * -- MESERO ya es personal autenticado del mismo tenant -- es la
     * aplicacion literal del principio de minimo privilegio que pide §10.
     * `null`/vacio se devuelve tal cual: no hay nada que enmascarar y
     * enmascarar `null` como "***null" seria peor que no enmascarar nada.
     */
    private static String enmascarar(String telefono) {
        if (telefono == null || telefono.isBlank()) {
            return telefono;
        }
        String ultimosDigitos = telefono.length() <= 4 ? telefono : telefono.substring(telefono.length() - 4);
        return "***" + ultimosDigitos;
    }
}
