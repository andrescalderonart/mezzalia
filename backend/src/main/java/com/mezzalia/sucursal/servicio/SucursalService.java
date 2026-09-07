package com.mezzalia.sucursal.servicio;

import com.mezzalia.sucursal.Sucursal;
import com.mezzalia.sucursal.SucursalRepository;
import com.mezzalia.sucursal.excepcion.SucursalExceptions;
import com.mezzalia.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * RF-10 (documentacion-tecnica.md §7): CRUD de sucursales, scoped al tenant
 * actual -- ver el javadoc de Sucursal.java para el alcance real de esta
 * rama (Mesa ya referencia una sucursal; CategoriaMenu/ItemMenu/Pedido
 * todavía no).
 */
@Service
@RequiredArgsConstructor
public class SucursalService {

    private final SucursalRepository sucursalRepository;

    /**
     * Doble defensa, mas estricta que el patron que el resto de este
     * proyecto usa para "listarX()" (MesaService.listarMesas() y
     * equivalentes de menu/pedido dependen SOLO del @Filter de Hibernate
     * para el aislamiento de la query, confiando en que
     * JwtAuthenticationFilter lo active antes de llegar aca): primero,
     * tenantActual() bloquea a SUPER_ADMIN, que nunca tiene TenantContext
     * poblado; segundo, el propio repositorio filtra por tenantId
     * explicito en la query, no solo por el filtro de sesion. La razon
     * para no
     * conformarse con el filtro aca: un test @DataJpaTest (ver
     * SucursalServiceTest.unTenantNuncaVeSucursalesDeOtroTenant, que
     * encontro esto) nunca activa ese filtro, e igual necesita poder
     * probar el aislamiento real -- confiar solo en el filtro hace que
     * ese caso sea imposible de verificar sin levantar el filtro de
     * Spring MVC completo. Mismo razonamiento aplicaria a
     * MesaService/MenuService/PedidoService si alguna vez se les escribe
     * un test equivalente; no se tocan aca por alcance de este fix.
     */
    @Transactional(readOnly = true)
    public List<Sucursal> listarSucursales() {
        Long tenantId = tenantActual();
        return sucursalRepository.findByTenantIdAndActivaTrueOrderByNombreAsc(tenantId);
    }

    /**
     * Uso exclusivo de AsignacionSucursalService.listarSucursalesDe (RF-10
     * PR4): dado un conjunto de ids de sucursal (las que un MESERO tiene
     * asignadas), devuelve las que pertenecen al tenant actual -- SIN
     * filtrar por activa a proposito, ver el javadoc de
     * SucursalRepository.findByTenantIdAndIdInOrderByNombreAsc. Una lista
     * vacia de ids devuelve una lista vacia sin tocar la base de datos: un
     * `IN ()` vacio es invalido en SQL estandar, y ademas no tendria
     * sentido de negocio ejecutar la consulta para un mesero sin ninguna
     * asignacion todavia.
     */
    @Transactional(readOnly = true)
    public List<Sucursal> listarPorIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return sucursalRepository.findByTenantIdAndIdInOrderByNombreAsc(tenantActual(), ids);
    }

    @Transactional
    public Sucursal crearSucursal(String nombre, String direccion) {
        Sucursal sucursal = new Sucursal();
        sucursal.setTenantId(tenantActual());
        sucursal.setNombre(nombre);
        sucursal.setDireccion(direccion);
        return sucursalRepository.save(sucursal);
    }

    /**
     * Soft delete -- una Mesa podría seguir referenciando esta sucursal
     * (Mesa.sucursalId), nunca borrado físico. Guardia explícito: si esta
     * es la única sucursal activa del tenant, no se deja desactivar (ver
     * javadoc de UltimaSucursalActivaException) -- de lo contrario el
     * tenant queda sin ninguna sucursal donde crear mesas nuevas.
     */
    @Transactional
    public void desactivarSucursal(Long sucursalId) {
        Sucursal sucursal = obtenerSucursal(sucursalId);
        if (sucursalRepository.countByTenantIdAndActivaTrue(sucursal.getTenantId()) <= 1) {
            throw new SucursalExceptions.UltimaSucursalActivaException();
        }
        sucursal.setActiva(false);
        sucursalRepository.save(sucursal);
    }

    /**
     * BUG REAL encontrado por CI (SucursalServiceTest.
     * reactivarSucursalLaVuelveAMostrarEnElListado, error -- no solo
     * assertion failure) y no por revision manual: esto llamaba a
     * obtenerSucursal(id), que filtra por Sucursal::isActiva -- correcto
     * para desactivarSucursal/obtenerSucursalActivaDelTenantActual (solo
     * tiene sentido desactivar o validar algo que esta activo), pero
     * exactamente al reves de lo que reactivar necesita: una sucursal
     * INACTIVA es precisamente lo que hay que encontrar aca, asi que ese
     * filtro hacia que reactivarSucursal() lanzara
     * SucursalNoEncontradaException el 100% de las veces, sobre
     * cualquier id valido -- la funcionalidad estaba completamente rota
     * desde que se escribio, sin importar el estado de la sucursal.
     */
    @Transactional
    public void reactivarSucursal(Long sucursalId) {
        Sucursal sucursal = obtenerSucursalSinImportarEstado(sucursalId);
        sucursal.setActiva(true);
        sucursalRepository.save(sucursal);
    }

    /**
     * Uso exclusivo de mesa.servicio.MesaService (validar que el
     * sucursalId de una mesa nueva pertenezca de verdad al tenant actual y
     * esté activa) -- mismo criterio de "no encontrado" genérico (nunca
     * 403) que obtenerCategoria/obtenerMesa/obtenerItem en el resto del
     * sistema: nunca confirmarle a un ADMIN_RESTAURANTE que un id de
     * sucursal existe en otro tenant.
     */
    @Transactional(readOnly = true)
    public Sucursal obtenerSucursalActivaDelTenantActual(Long sucursalId) {
        return obtenerSucursal(sucursalId);
    }

    /**
     * Uso exclusivo de identidad.servicio.AsignacionSucursalService.
     * listarUsuariosDe: validar que un sucursalId sea del tenant actual
     * SIN exigir que siga activa -- a diferencia de
     * obtenerSucursalActivaDelTenantActual, ver aca no es sobre si tiene
     * sentido operar sobre la sucursal (crear una mesa, asignar a un
     * mesero nuevo), sino sobre poder seguir consultando quién quedó
     * asignado a un local que después se desactivó (las filas de
     * AsignacionSucursal no se tocan al desactivar, ver javadoc de
     * desactivarSucursal). Wrapper público de obtenerSucursalSinImportarEstado
     * (privado, ya usado por reactivarSucursal) para no duplicar la
     * validación de tenant.
     */
    @Transactional(readOnly = true)
    public Sucursal obtenerSucursalDelTenantActualSinImportarEstado(Long sucursalId) {
        return obtenerSucursalSinImportarEstado(sucursalId);
    }

    private Sucursal obtenerSucursal(Long id) {
        Sucursal sucursal = sucursalRepository.findById(id)
                .filter(Sucursal::isActiva)
                .orElseThrow(() -> new SucursalExceptions.SucursalNoEncontradaException(id));
        return validarTenantOLanzar(sucursal, id);
    }

    /**
     * Igual que obtenerSucursal(id) pero SIN el filtro de estado activo --
     * usado por reactivarSucursal() y por
     * obtenerSucursalDelTenantActualSinImportarEstado().
     */
    private Sucursal obtenerSucursalSinImportarEstado(Long id) {
        Sucursal sucursal = sucursalRepository.findById(id)
                .orElseThrow(() -> new SucursalExceptions.SucursalNoEncontradaException(id));
        return validarTenantOLanzar(sucursal, id);
    }

    private Sucursal validarTenantOLanzar(Sucursal sucursal, Long id) {
        Long tenantActual = TenantContext.obtenerTenantId();
        if (tenantActual == null || !tenantActual.equals(sucursal.getTenantId())) {
            throw new SucursalExceptions.SucursalNoEncontradaException(id);
        }
        return sucursal;
    }

    private Long tenantActual() {
        Long tenantId = TenantContext.obtenerTenantId();
        if (tenantId == null) {
            throw new SucursalExceptions.SinTenantEnContextoException();
        }
        return tenantId;
    }
}
