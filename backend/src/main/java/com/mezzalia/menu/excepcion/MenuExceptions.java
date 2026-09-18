package com.mezzalia.menu.excepcion;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.math.BigDecimal;

public final class MenuExceptions {

    private MenuExceptions() {}

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class CategoriaNoEncontradaException extends RuntimeException {
        public CategoriaNoEncontradaException(Long id) {
            super("Categoría no encontrada con id: " + id);
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class ItemMenuNoEncontradoException extends RuntimeException {
        public ItemMenuNoEncontradoException(Long id) {
            super("Ítem de carta no encontrado con id: " + id);
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class PrecioInvalidoException extends RuntimeException {
        public PrecioInvalidoException(BigDecimal precio) {
            super("El precio debe ser mayor o igual a cero: " + precio);
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class ArchivoInvalidoException extends RuntimeException {
        public ArchivoInvalidoException(String motivo) {
            super(motivo);
        }
    }

    /**
     * RF-05: un plato puede pasar a "agotado" (ItemMenu.disponible = false)
     * en cualquier momento, incluso mientras un comensal ya lo tenia en su
     * carrito. Distinto de ItemMenuNoEncontradoException (404): el item SI
     * existe y SI pertenece a este tenant -- por eso 409 CONFLICT, no 404 --
     * el comensal ya lo vio en la carta que le sirvio este mismo tenant, asi
     * que no hay riesgo de confirmarle a un desconocido que un recurso
     * existe en otro tenant (ver MenuService.obtenerItem para ese criterio).
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    public static class ItemNoDisponibleException extends RuntimeException {
        public ItemNoDisponibleException(Long id) {
            super("El item con id " + id + " ya no esta disponible");
        }
    }

    /**
     * SUPER_ADMIN (equipo Mezzalia) no tiene tenant_id propio -- no gestiona
     * la carta de un restaurante específico. Si de alguna forma llega hasta
     * acá (no debería, ver RolUsuario), es un 403 explícito, no un 500
     * genérico ni un NullPointerException silencioso.
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class SinTenantEnContextoException extends RuntimeException {
        public SinTenantEnContextoException() {
            super("Esta operación requiere un usuario de restaurante autenticado (no SUPER_ADMIN)");
        }
    }

    /**
     * 2026-09-10: MenuService.duplicarMenuDesdeSucursal. Mismo criterio de
     * "no encontrado genérico" que el resto de este servicio -- si
     * sucursalOrigenId pertenece a otro tenant, la consulta no devuelve
     * nada y cae en esta misma excepción, sin confirmarle a nadie que ese
     * id existe en otro restaurante.
     */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class SucursalOrigenSinCartaException extends RuntimeException {
        public SucursalOrigenSinCartaException() {
            super("La sucursal de origen no tiene ninguna carta activa para duplicar");
        }
    }

    /**
     * 2026-09-10: MenuService.duplicarMenuDesdeSucursal. Decisión de
     * producto explícita (Andrés, 2026-09-10): nunca duplicar encima de una
     * carta ya existente -- el usuario debe vaciar la sucursal destino a
     * mano primero si de verdad quiere reemplazarla.
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    public static class SucursalDestinoYaTieneCartaException extends RuntimeException {
        public SucursalDestinoYaTieneCartaException() {
            super("La sucursal destino ya tiene una carta activa; no se puede duplicar encima de una carta existente");
        }
    }

    /**
     * 2026-09-11: MenuService.actualizarModelo3d. Un modelo 3D solo con la
     * URL de Android (glb) o solo con la de iOS (usdz) no sirve para nada
     * -- confirmaría en la carta pública un botón "Ver en 3D" que solo
     * funciona en un sistema operativo. Se exigen las dos juntas, o
     * ninguna (para poder quitarle el modelo 3D al item).
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class Modelo3dIncompletoException extends RuntimeException {
        public Modelo3dIncompletoException() {
            super("Un modelo 3D requiere modeloGlbUrl y modeloUsdzUrl juntos (o ninguno de los dos)");
        }
    }
}
