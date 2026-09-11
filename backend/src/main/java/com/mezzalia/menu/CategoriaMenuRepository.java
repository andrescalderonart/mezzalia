package com.mezzalia.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoriaMenuRepository extends JpaRepository<CategoriaMenu, Long> {

    List<CategoriaMenu> findBySucursalIdAndActivoTrueOrderByOrdenAsc(Long sucursalId);

    /** Usado por MenuService.duplicarMenuDesdeSucursal para rechazar duplicar
     * encima de una carta ya existente en la sucursal destino. */
    boolean existsBySucursalIdAndActivoTrue(Long sucursalId);
}
