package com.mezzalia.identidad.servicio;

import com.mezzalia.identidad.AsignacionSucursal;
import com.mezzalia.identidad.AsignacionSucursalRepository;
import com.mezzalia.identidad.RolUsuario;
import com.mezzalia.identidad.UsuarioInterno;
import com.mezzalia.identidad.UsuarioInternoRepository;
import com.mezzalia.identidad.excepcion.AsignacionSucursalExceptions;
import com.mezzalia.identidad.excepcion.UsuarioInternoExceptions;
import com.mezzalia.sucursal.Sucursal;
import com.mezzalia.sucursal.servicio.SucursalService;
import com.mezzalia.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * RF-10 PR4 (docs/propuesta-multisucursal-rf10.md §3, decision de negocio
 * ya resuelta con el equipo): un MESERO puede estar asignado a varias
 * sucursales del mismo tenant (muchos-a-muchos) -- la asignacion la
 * gestiona EXCLUSIVAMENTE ADMIN_RESTAURANTE (ver
 * UsuarioInternoAdminController, mismo @PreAuthorize de clase que el resto
 * del CRUD de usuarios internos). Un ADMIN_RESTAURANTE nunca necesita fila
 * en esta tabla -- ya ve todas las sucursales de su tenant sin restriccion
 * (ver AuthService.misSucursalesDisponibles); asignarle una seria dato
 * muerto que ningun camino de la aplicacion lee (ver
 * AsignacionSucursalExceptions.RolNoPermitidoParaAsignacionException).
 */
@Service
@RequiredArgsConstructor
public class AsignacionSucursalService {

    private final AsignacionSucursalRepository asignacionSucursalRepository;
    private final UsuarioInternoRepository usuarioInternoRepository;
    private final SucursalService sucursalService;

    @Transactional(readOnly = true)
    public List<Sucursal> listarSucursalesDe(Long usuarioId) {
        UsuarioInterno usuario = obtenerUsuarioDelTenant(usuarioId);
        List<Long> idsAsignados = asignacionSucursalRepository
                .findByUsuarioInternoIdOrderBySucursalId(usuario.getId()).stream()
                .map(AsignacionSucursal::getSucursalId)
                .toList();
        return sucursalService.listarPorIds(idsAsignados);
    }

    /**
     * Idempotente a proposito -- mismo criterio que
     * UsuarioInternoAdminService.desactivarUsuario/
     * SucursalService.desactivarSucursal: asignar una sucursal ya
     * asignada es un no-op exitoso, no un error.
     *
     * Valida EXPLICITAMENTE que tanto el usuario como la sucursal
     * pertenezcan al tenant actual (obtenerUsuarioDelTenant/
     * SucursalService.obtenerSucursalActivaDelTenantActual, que ademas
     * exige que la sucursal este ACTIVA -- no tendria sentido asignar a
     * alguien a trabajar en un local cerrado) -- sin esto, un
     * ADMIN_RESTAURANTE adivinando ids podria asignar a SU mesero una
     * sucursal de OTRO tenant, algo que no tiene ningun sentido de negocio
     * y que el FK de V17__usuario_sucursales.sql solo detecta como
     * backstop de ultima linea, no como control principal.
     */
    @Transactional
    public void asignar(Long usuarioId, Long sucursalId) {
        UsuarioInterno usuario = obtenerUsuarioDelTenant(usuarioId);
        if (usuario.getRol() != RolUsuario.MESERO) {
            throw new AsignacionSucursalExceptions.RolNoPermitidoParaAsignacionException(usuario.getRol());
        }
        Sucursal sucursal = sucursalService.obtenerSucursalActivaDelTenantActual(sucursalId);
        if (asignacionSucursalRepository.existsByUsuarioInternoIdAndSucursalId(usuario.getId(), sucursal.getId())) {
            return;
        }
        AsignacionSucursal asignacion = new AsignacionSucursal();
        asignacion.setTenantId(tenantActual());
        asignacion.setUsuarioInternoId(usuario.getId());
        asignacion.setSucursalId(sucursal.getId());
        asignacionSucursalRepository.save(asignacion);
    }

    /**
     * Idempotente (ver AsignacionSucursalRepository.
     * deleteByUsuarioInternoIdAndSucursalId): desasignar una sucursal que
     * el usuario ya no tenia es un no-op exitoso. Solo valida que el
     * usuario pertenezca al tenant actual -- si la sucursal no existe o es
     * de otro tenant, sencillamente no hay ninguna fila que borrar, mismo
     * resultado observable que "ya estaba desasignada".
     */
    @Transactional
    public void desasignar(Long usuarioId, Long sucursalId) {
        UsuarioInterno usuario = obtenerUsuarioDelTenant(usuarioId);
        asignacionSucursalRepository.deleteByUsuarioInternoIdAndSucursalId(usuario.getId(), sucursalId);
    }

    /**
     * Misma defensa explicita que UsuarioInternoAdminService.
     * obtenerUsuarioDelTenant: no confiar solo en el filtro de Hibernate,
     * y "no encontrado" generico (nunca 403) para no confirmarle a un
     * ADMIN_RESTAURANTE que el id existe en otro tenant.
     */
    private UsuarioInterno obtenerUsuarioDelTenant(Long id) {
        UsuarioInterno usuario = usuarioInternoRepository.findById(id)
                .orElseThrow(() -> new UsuarioInternoExceptions.UsuarioNoEncontradoException(id));
        if (!tenantActual().equals(usuario.getTenantId())) {
            throw new UsuarioInternoExceptions.UsuarioNoEncontradoException(id);
        }
        return usuario;
    }

    private Long tenantActual() {
        Long tenantId = TenantContext.obtenerTenantId();
        if (tenantId == null) {
            throw new UsuarioInternoExceptions.SinTenantEnContextoException();
        }
        return tenantId;
    }
}
