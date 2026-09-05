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
 * `telefono` se ENMASCARA salvo que quien pida el dato sea
 * ADMIN_RESTAURANTE -- documentacion-tecnica.md §10 lo exige explícitamente:
 * "acceso restringido por rol (un mesero no necesita ver el telefono
 * completo, un admin_restaurante si)". Este chequeo NO existía hasta ahora:
 * ComensalAdminController dejaba esto marcado explícitamente como "pendiente
 * aparte, no arrancado" en el mismo commit que introdujo el borrado de
 * datos -- cualquier usuario autenticado del tenant (incluido MESERO) veía
 * el teléfono completo del comensal en GET /api/admin/pedidos. Se enmascara
 * acá, en el borde de serialización hacia el cliente (ver
 * PedidoAdminController), y no en la entidad ni en el servicio: Comensal
 * sigue devolviendo el teléfono real y descifrado para cualquier otro uso
 * interno (ComensalService.eliminarDatosComensal no lo necesita, pero un
 * futuro contacto por WhatsApp desde el backend no debería heredar una
 * máscara pensada solo para la UI del panel).
 *
 * `id` se agrega (documentacion-tecnica.md §10, Habeas Data) para que el
 * panel pueda pedir ComensalAdminController#eliminarDatos(id) sin tener que
 * resolver el id por otro camino -- el personal ya ve este DTO adentro de
 * un pedido concreto al atenderlo, es el punto natural donde identifican de
 * qué comensal se trata si alguien pide que borren sus datos. `id` nunca se
 * enmascara: no es un dato personal en sí mismo y el panel lo necesita
 * igual para MESERO también (ej. para poder distinguir dos pedidos del
 * mismo comensal), a diferencia de `telefono`.
 *
 * documentacion-tecnica.md §10 exige "acceso restringido por rol (un
 * mesero no necesita ver el telefono completo, un admin_restaurante si)".
 * Por eso no existe un único `desde(Comensal)` sin más: el llamador
 * SIEMPRE tiene que decidir explícitamente si quien va a recibir este DTO
 * puede ver el teléfono completo -- no hay default implícito "completo"
 * que un caller nuevo pueda heredar sin darse cuenta. Ver
 * PedidoAdminController para el único punto donde hoy se decide ese
 * booleano según el rol autenticado.
 */
public record ComensalDto(Long id, String nombre, String telefono) {

    public static ComensalDto desde(Comensal comensal, boolean telefonoCompleto) {
        String telefono = telefonoCompleto ? comensal.getTelefono() : enmascarar(comensal.getTelefono());
        return new ComensalDto(comensal.getId(), comensal.getNombre(), telefono);
    }

    /**
     * Máscara de longitud fija ("***" + últimos 4 caracteres) en vez de
     * reemplazar carácter por carácter: alcanza para que un MESERO pueda
     * confirmar "es el mismo número que me dijo el comensal" sin exponer
     * el número completo, y de paso no delata la longitud real del dato
     * original (un `telefono` de 7 dígitos y uno de 12 quedan con el mismo
     * formato de salida). No es un requisito de seguridad criptográfica
     * -- MESERO ya es personal autenticado del mismo tenant -- es la
     * aplicación literal del principio de mínimo privilegio que pide §10.
     * `null`/vacío se devuelve tal cual: no hay nada que enmascarar y
     * enmascarar `null` como "***null" sería peor que no enmascarar nada.
     */
    private static String enmascarar(String telefono) {
        if (telefono == null || telefono.isBlank()) {
            return telefono;
        }
        String ultimosDigitos = telefono.length() <= 4 ? telefono : telefono.substring(telefono.length() - 4);
        return "***" + ultimosDigitos;
    }
}
