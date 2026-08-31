package com.mezzalia.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemMenuRepository extends JpaRepository<ItemMenu, Long> {
    List<ItemMenu> findByCategoriaIdAndActivoTrue(Long categoriaId);
}
