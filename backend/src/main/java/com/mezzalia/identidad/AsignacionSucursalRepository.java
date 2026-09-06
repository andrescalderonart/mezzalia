package com.mezzalia.identidad;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AsignacionSucursalRepository extends JpaRepository<AsignacionSucursal, Long> {

    List<AsignacionSucursal> findByUsuarioInternoIdOrderBySucursalId(Long usuarioInternoId);

    boolean existsByUsuarioInternoIdAndSucursalId(Long usuarioInternoId, Long sucursalId);

    /**
     * Delete derivado de Spring Data -- ya idempotente por naturaleza (0
     * filas afectadas si la asignacion no existia no lanza ninguna
     * excepcion), asi que AsignacionSucursalService.desasignar no necesita
     * ningun chequeo previo de existencia para lograr el mismo criterio de
     * "no-op exitoso" que el resto del proyecto usa para
     * activar/desactivar (ver SucursalService.desactivarSucursal,
     * UsuarioInternoAdminService.desactivarUsuario).
     */
    void deleteByUsuarioInternoIdAndSucursalId(Long usuarioInternoId, Long sucursalId);
}
