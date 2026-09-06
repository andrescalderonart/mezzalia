package com.mezzalia.identidad.servicio;

import com.mezzalia.identidad.RolUsuario;
import com.mezzalia.identidad.UsuarioInterno;
import com.mezzalia.identidad.UsuarioInternoRepository;
import com.mezzalia.identidad.excepcion.UsuarioInternoExceptions;
import com.mezzalia.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * RF-09/documentacion-tecnica.md §2: "gestión de usuarios internos con
 * roles" estaba listada como parte incluida del panel del restaurante en el
 * alcance del MVP, pero nunca se había construido -- lo único que existía
 * era SeedDatosDemoConfig (perfil `dev`, un admin.demo/mesero.demo fijos) y
 * SeedSuperAdminConfig, ambos documentados explícitamente como "fuera de
 * alcance de este PR" en su momento. Este servicio es ese CRUD, exclusivo
 * de ADMIN_RESTAURANTE gestionando los usuarios de SU PROPIO tenant.
 *
 * Nunca borra usuarios (hard delete): mismo criterio de soft-delete que el
 * resto del dominio (Mesa.activa, ItemMenu.activo, CategoriaMenu.activo) --
 * UsuarioInterno YA tenía el campo `activo` desde el modelo original
 * (pensado para SeedDatosDemoConfig/AuthService.login), así que
 * activar/desactivar no necesita ninguna migración nueva.
 */
@Service
@RequiredArgsConstructor
public class UsuarioInternoAdminService {

    private final UsuarioInternoRepository usuarioInternoRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Defensa explícita, mismo criterio que el resto de los servicios de
     * este proyecto: SUPER_ADMIN no tiene tenant_id (TenantContext vacío a
     * propósito, ver JwtAuthenticationFilter), así que sin este guardia un
     * token SUPER_ADMIN vería un 500/NullPointerException en vez de un 403
     * explícito -- aunque en la práctica @PreAuthorize("hasRole('ADMIN_RESTAURANTE')")
     * en el controlador ya debería impedir que SUPER_ADMIN llegue hasta acá.
     */
    @Transactional(readOnly = true)
    public List<UsuarioInterno> listarUsuarios() {
        return usuarioInternoRepository.findByTenantIdOrderByUsernameAsc(tenantActual());
    }

    /**
     * Chequeo de aplicación (leer-antes-de-escribir) para la unicidad de
     * `username`, con un catch de DataIntegrityViolationException como
     * backstop real de la restricción UNIQUE ya existente en la columna
     * (ver V3__usuarios_internos.sql) -- mismo patrón de dos capas que
     * TenantService.crearTenant/MesaService.crearMesa: el chequeo cubre el
     * caso común con un mensaje de negocio claro, la restricción de BD
     * cierra la carrera real entre dos altas simultáneas con el mismo
     * username (acá más plausible que en Mesa.codigo: el username es único
     * a nivel de TODA la plataforma, no solo del tenant, así que dos
     * ADMIN_RESTAURANTE de restaurantes distintos podrían chocar entre sí).
     */
    @Transactional
    public UsuarioInterno crearUsuario(String username, String passwordEnTextoPlano, RolUsuario rol) {
        if (rol == RolUsuario.SUPER_ADMIN) {
            throw new UsuarioInternoExceptions.RolNoPermitidoException(rol);
        }
        if (usuarioInternoRepository.existsByUsername(username)) {
            throw new UsuarioInternoExceptions.UsuarioYaExisteException(username);
        }
        UsuarioInterno usuario = new UsuarioInterno();
        usuario.setUsername(username);
        usuario.setPasswordHash(passwordEncoder.encode(passwordEnTextoPlano));
        usuario.setRol(rol);
        usuario.setTenantId(tenantActual());
        usuario.setActivo(true);
        try {
            return usuarioInternoRepository.save(usuario);
        } catch (DataIntegrityViolationException ex) {
            throw new UsuarioInternoExceptions.UsuarioYaExisteException(username);
        }
    }

    /**
     * Idempotente a propósito (mismo criterio que
     * TenantService.suspenderTenant/reactivarTenant): desactivar un usuario
     * ya inactivo, o reactivar uno ya activo, es un no-op exitoso, no un
     * error.
     */
    @Transactional
    public void desactivarUsuario(Long usuarioId) {
        UsuarioInterno usuario = obtenerUsuarioDelTenant(usuarioId);
        if (esElUsuarioAutenticadoActual(usuario)) {
            throw new UsuarioInternoExceptions.NoPuedeDesactivarseASiMismoException();
        }
        usuario.setActivo(false);
        usuarioInternoRepository.save(usuario);
    }

    @Transactional
    public void reactivarUsuario(Long usuarioId) {
        UsuarioInterno usuario = obtenerUsuarioDelTenant(usuarioId);
        usuario.setActivo(true);
        usuarioInternoRepository.save(usuario);
    }

    /**
     * Único camino para cambiar la contraseña de OTRO usuario -- no existe
     * flujo de "olvidé mi contraseña" por email en este proyecto (no hay
     * módulo de notificaciones todavía, ver documentacion-tecnica.md §12),
     * así que esto es, en la práctica, el mecanismo real de recuperación:
     * el ADMIN_RESTAURANTE resetea la contraseña de un MESERO que la
     * olvidó. No requiere la contraseña anterior a propósito -- distinto de
     * un "cambiar mi propia contraseña" (que este proyecto tampoco tiene
     * todavía, ver el reporte de brechas), donde sí correspondería pedirla.
     */
    @Transactional
    public void resetearPassword(Long usuarioId, String passwordNuevaEnTextoPlano) {
        UsuarioInterno usuario = obtenerUsuarioDelTenant(usuarioId);
        usuario.setPasswordHash(passwordEncoder.encode(passwordNuevaEnTextoPlano));
        usuarioInternoRepository.save(usuario);
    }

    /**
     * Misma defensa explícita que obtenerMesa/obtenerItem/obtenerPedido en
     * el resto del sistema: no confiar solo en el filtro de Hibernate para
     * una carga por id, y devolver siempre "no encontrado" (nunca 403) para
     * no confirmarle a un ADMIN_RESTAURANTE que el id existe en otro
     * tenant -- ni siquiera que existe en absoluto.
     */
    private UsuarioInterno obtenerUsuarioDelTenant(Long id) {
        UsuarioInterno usuario = usuarioInternoRepository.findById(id)
                .orElseThrow(() -> new UsuarioInternoExceptions.UsuarioNoEncontradoException(id));
        if (!tenantActual().equals(usuario.getTenantId())) {
            throw new UsuarioInternoExceptions.UsuarioNoEncontradoException(id);
        }
        return usuario;
    }

    private boolean esElUsuarioAutenticadoActual(UsuarioInterno usuario) {
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        return autenticacion != null && autenticacion.getName().equals(usuario.getUsername());
    }

    private Long tenantActual() {
        Long tenantId = TenantContext.obtenerTenantId();
        if (tenantId == null) {
            throw new UsuarioInternoExceptions.SinTenantEnContextoException();
        }
        return tenantId;
    }
}
