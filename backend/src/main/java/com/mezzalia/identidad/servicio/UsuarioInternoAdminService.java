package com.mezzalia.identidad.servicio;

import com.mezzalia.common.seguridad.EventosSeguridad;
import com.mezzalia.common.seguridad.PoliticaPassword;

import com.mezzalia.identidad.RolUsuario;
import com.mezzalia.identidad.UsuarioInterno;
import com.mezzalia.identidad.UsuarioInternoRepository;
import com.mezzalia.identidad.excepcion.UsuarioInternoExceptions;
import com.mezzalia.tenant.TenantContext;
import com.mezzalia.tenant.servicio.TenantService;
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
    private final TenantService tenantService;

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
        exigirPasswordSegura(passwordEnTextoPlano, username);
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
     * Hallazgo posterior al cierre de la auditoria del 2026-09-08 (SS1.1):
     * hasta este metodo, no existia NINGUN camino de API para crear el
     * primer usuario de un tenant nuevo. TenantAdminController.crearTenant
     * (SUPER_ADMIN) solo crea la fila de Tenant; crearUsuario (arriba) esta
     * detras de @PreAuthorize("hasRole('ADMIN_RESTAURANTE')") a nivel de
     * clase en el controller y siempre resuelve el tenant via
     * tenantActual() (TenantContext, poblado del JWT del que llama) -- un
     * SUPER_ADMIN nunca tiene tenantId, asi que ese mismo endpoint no puede
     * servir para dar de alta el admin inicial de un tenant que el
     * SUPER_ADMIN acaba de crear. En la practica, antes de este metodo, la
     * unica forma de onboardear un restaurante nuevo de punta a punta era
     * un INSERT manual a la base -- justo lo que documentacion-tecnica.md
     * §5 (fase 1, "onboarding asistido por ustedes") da por hecho que existe
     * como capacidad, pero nunca se habia construido.
     *
     * Deliberadamente SEPARADO de TenantService.crearTenant (dos llamadas
     * de API, no una transaccion atomica que abarque ambas cosas):
     * TenantService (modulo `tenant`) no importa nada de otros modulos --
     * es la base de la que todos los demas modulos dependen (identidad,
     * menu, mesa, sucursal, pedido y turno ya importan `tenant`, nunca al
     * reves). Meter la creacion de un UsuarioInterno dentro de
     * TenantService.crearTenant invertiria esa direccion de dependencia.
     * Este metodo vive en `identidad` (que SI puede depender de `tenant`,
     * ver TenantContext) y llama a TenantService.obtenerParaAdministracion
     * primero para poder devolver un 404 claro (TenantNoEncontradoException)
     * si el tenantId no existe -- sin este chequeo, la unica senal de error
     * seria una DataIntegrityViolationException por el FK
     * fk_usuarios_internos_tenant, un 500 opaco en vez de un error de
     * negocio legible.
     *
     * `tenantId` viene explicito por parametro, no de TenantContext (que
     * para SUPER_ADMIN esta vacio a proposito) -- unica excepcion al
     * criterio de "nunca confiar en un tenant que el cliente podria
     * manipular en el body" que documenta CrearUsuarioInternoRequest: aca
     * el llamador (SUPER_ADMIN, ya autorizado por @PreAuthorize a nivel de
     * clase del controller) es precisamente quien decide para que tenant es
     * el alta, via el path de la URL
     * (/api/admin/tenants/{tenantId}/admin-inicial) -- mismo patron que
     * TenantAdminController.actualizarBranding/suspenderTenant/reactivarTenant,
     * que tambien toman el id del path, no del body.
     *
     * No valida "que no haya ya un admin para este tenant": no hace falta,
     * tener mas de un ADMIN_RESTAURANTE por tenant no es un estado
     * invalido (ver UsuarioInterno, no hay ninguna regla de unicidad de rol
     * por tenant, solo la de username a nivel de plataforma).
     */
    @Transactional
    public UsuarioInterno crearAdminInicial(Long tenantId, String username, String passwordEnTextoPlano) {
        tenantService.obtenerParaAdministracion(tenantId);
        exigirPasswordSegura(passwordEnTextoPlano, username);
        if (usuarioInternoRepository.existsByUsername(username)) {
            throw new UsuarioInternoExceptions.UsuarioYaExisteException(username);
        }
        UsuarioInterno usuario = new UsuarioInterno();
        usuario.setUsername(username);
        usuario.setPasswordHash(passwordEncoder.encode(passwordEnTextoPlano));
        usuario.setRol(RolUsuario.ADMIN_RESTAURANTE);
        usuario.setTenantId(tenantId);
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
        exigirPasswordSegura(passwordNuevaEnTextoPlano, usuario.getUsername());
        usuario.setPasswordHash(passwordEncoder.encode(passwordNuevaEnTextoPlano));
        usuarioInternoRepository.save(usuario);
        EventosSeguridad.passwordCambiada(usuario.getId());
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

    /** OWASP ASVS 5.0 V6.2 -- ver common.seguridad.PoliticaPassword. */
    private static void exigirPasswordSegura(String password, String username) {
        String problema = PoliticaPassword.problema(password, username);
        if (problema != null) {
            throw new UsuarioInternoExceptions.PasswordInseguraException(problema);
        }
    }
}
