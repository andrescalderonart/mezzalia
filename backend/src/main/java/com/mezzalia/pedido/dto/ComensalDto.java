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
 * ADMIN_RESTAURANTE -- documentacion-tecnica.md §10 lo exige explicitamente:
 * "acceso restringido por rol (un mesero no necesita ver el telefono
 * completo, un admin_restaurante si)". Este chequeo NO existia hasta ahora:
 * ComensalAdminController dejaba esto marcado explicitamente como "pendiente
 * aparte, no arrancado" en el mismo commit que introdujo el borrado de
 * datos -- cualquier usuario autenticado del tenant (incluido MESERO) veia
 * el telefono completo del comensal en GET /api/admin/pedidos. Se enmascara
 * aca, en el borde de serializacion hacia el cliente (ver
 * PedidoAdminController), y no en la entidad ni en el servicio: Comensal
 * sigue devolviendo el telefono real y descifrado para cualquier otro uso
 * interno (ComensalService.eliminarDatosComensal no lo necesita, pero un
 * futuro contacto por WhatsApp desde el backend no deberia heredar una
 * mascara pensada solo para la UI del panel).
 *
 * `id` se agrega (documentacion-tecnica.md §10, Habeas Data) para que el
 * panel pueda pedir ComensalAdminController#eliminarDatos(id) sin tener que
 * resolver el id por otro camino -- el personal ya ve este DTO adentro de
 * un pedido concreto al atenderlo, es el punto natural donde identifican de
 * que comensal se trata si alguien pide que borren sus datos.
 */
public record ComensalDto(Long id, String nombre, String telefono) {

    private static final int DIGITOS_VISIBLES = 2;
    private static final char CARACTER_MASCARA = '•';

    /**
     * @param puedeVerTelefonoCompleto true solo para ADMIN_RESTAURANTE (ver
     *                                 PedidoAdminController.esAdminRestaurante) --
     *                                 MESERO siempre recibe el telefono
     *                                 enmascarado, nunca el valor real.
     */
    public static ComensalDto desde(Comensal comensal, boolean puedeVerTelefonoCompleto) {
        String telefono = comensal.getTelefono();
        String telefonoParaMostrar = puedeVerTelefonoCompleto ? telefono : enmascarar(telefono);
        return new ComensalDto(comensal.getId(), comensal.getNombre(), telefonoParaMostrar);
    }

    /**
     * Deja visibles los ultimos DIGITOS_VISIBLES caracteres (suficiente para
     * que un mesero confirme "es el mismo comensal que ya atendio antes" sin
     * poder leer ni marcar el numero completo) y enmascara el resto con
     * CARACTER_MASCARA. Un telefono nulo/vacio se devuelve tal cual (nada
     * que enmascarar); uno mas corto que DIGITOS_VISIBLES se enmascara por
     * completo -- no queda nada seguro que mostrar de un numero ya casi
     * todo visible.
     */
    private static String enmascarar(String telefono) {
        if (telefono == null || telefono.isBlank()) {
            return telefono;
        }
        if (telefono.length() <= DIGITOS_VISIBLES) {
            return String.valueOf(CARACTER_MASCARA).repeat(telefono.length());
        }
        String sufijo = telefono.substring(telefono.length() - DIGITOS_VISIBLES);
        return String.valueOf(CARACTER_MASCARA).repeat(telefono.length() - DIGITOS_VISIBLES) + sufijo;
    }
}
