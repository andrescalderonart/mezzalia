package com.mezzalia.identidad.excepcion;

import com.mezzalia.identidad.RolUsuario;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * RF-09/§2: CRUD de usuarios internos (admin_restaurante gestiona sus
 * propios mesero/admin_restaurante) -- ver
 * identidad.servicio.UsuarioInternoAdminService.
 */
public final class UsuarioInternoExceptions {

    private UsuarioInternoExceptions() {}

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class UsuarioYaExisteException extends RuntimeException {
        public UsuarioYaExisteException(String username) {
            super("Ya existe un usuario con el username: " + username);
        }
    }

    /**
     * Mismo criterio de "no encontrado" genérico que
     * MenuService/MesaService/PedidoService/ComensalService: nunca 403 acá,
     * para no confirmarle a un ADMIN_RESTAURANTE que un id de usuario
     * existe en OTRO tenant.
     */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class UsuarioNoEncontradoException extends RuntimeException {
        public UsuarioNoEncontradoException(Long id) {
            super("Usuario no encontrado con id: " + id);
        }
    }

    /**
     * Este CRUD es exclusivo de ADMIN_RESTAURANTE gestionando su propio
     * tenant -- ni siquiera ADMIN_RESTAURANTE puede crear/ascender a
     * SUPER_ADMIN desde acá (SUPER_ADMIN no tiene tenant_id, ver
     * UsuarioInterno.validarInvariantes -- permitirlo acá sería la forma
     * mas simple de un escalamiento de privilegios entre tenants).
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class RolNoPermitidoException extends RuntimeException {
        public RolNoPermitidoException(RolUsuario rol) {
            super("No se puede asignar el rol " + rol + " desde este endpoint");
        }
    }

    /**
     * Caso límite real (documentacion-tecnica.md §9 pide pensar estos
     * casos): si un ADMIN_RESTAURANTE pudiera desactivar su propia cuenta y
     * fuera el único ADMIN_RESTAURANTE activo del tenant, nadie podría
     * volver a gestionar usuarios de ese restaurante -- ni siquiera
     * SUPER_ADMIN tiene un camino para reactivarlo (esta operación es
     * tenant-scoped, no expuesta a SUPER_ADMIN). Se bloquea la
     * autodesactivación sin excepción, sin intentar distinguir "sos el
     * único admin activo" (esa consulta agrega una carrera propia sin
     * eliminar el riesgo real).
     */
    /**
     * SUPER_ADMIN (equipo Mezzalia) no tiene tenant_id propio -- no
     * gestiona usuarios de un restaurante específico. Mismo criterio que
     * MenuExceptions/MesaExceptions/PedidoExceptions.SinTenantEnContextoException
     * en el resto del sistema: 403 explícito, no un 500 genérico ni un
     * NullPointerException silencioso si de alguna forma se llega hasta acá
     * (no debería, @PreAuthorize("hasRole('ADMIN_RESTAURANTE')") en el
     * controlador ya lo impide).
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public static class SinTenantEnContextoException extends RuntimeException {
        public SinTenantEnContextoException() {
            super("Esta operación requiere un usuario de restaurante autenticado (no SUPER_ADMIN)");
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class NoPuedeDesactivarseASiMismoException extends RuntimeException {
        public NoPuedeDesactivarseASiMismoException() {
            super("No podés desactivar tu propia cuenta");
        }
    }
}
