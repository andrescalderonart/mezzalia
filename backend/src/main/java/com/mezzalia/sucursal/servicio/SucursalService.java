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
     * Defensa explícita, mismo criterio que el resto de los servicios de
     * este proyecto: el filtro de Hibernate nunca se activa para
     * SUPER_ADMIN (TenantContext vacío a propósito), así que un listado sin
     * este guardia expondría sucursales de TODOS los tenants a la vez.
     */
    @Transactional(readOnly = true)
    public List<Sucursal> listarSucursales() {
        tenantActual();
        return sucursalRepository.findByActivaTrueOrderByNombreAsc();
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

    @Transactional
    public void reactivarSucursal(Long sucursalId) {
        Sucursal sucursal = obtenerSucursal(sucursalId);
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

    private Sucursal obtenerSucursal(Long id) {
        Sucursal sucursal = sucursalRepository.findById(id)
                .filter(Sucursal::isActiva)
                .orElseThrow(() -> new SucursalExceptions.SucursalNoEncontradaException(id));
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
