package com.mezzalia.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemMenuRepository extends JpaRepository<ItemMenu, Long> {

    /** sucursalId se pasa explícito además de categoriaId a propósito: es la
     * misma defensa en profundidad que el resto del proyecto (no confiar
     * solo en que categoriaId ya implique la sucursal correcta), aunque hoy
     * ambos siempre coincidan por construcción (ver ItemMenu.sucursalId). */
    List<ItemMenu> findByCategoriaIdAndSucursalIdAndActivoTrue(Long categoriaId, Long sucursalId);
}
