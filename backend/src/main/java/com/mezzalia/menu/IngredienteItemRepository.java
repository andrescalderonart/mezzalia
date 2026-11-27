package com.mezzalia.menu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface IngredienteItemRepository extends JpaRepository<IngredienteItem, Long> {

    List<IngredienteItem> findByItemMenuIdOrderByOrdenAscIdAsc(Long itemMenuId);

    /** Una sola consulta para toda la carta publica (sin N+1 por plato). */
    List<IngredienteItem> findByItemMenuIdInOrderByOrdenAscIdAsc(Collection<Long> itemMenuIds);
}
