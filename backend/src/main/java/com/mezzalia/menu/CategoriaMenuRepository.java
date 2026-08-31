package com.mezzalia.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoriaMenuRepository extends JpaRepository<CategoriaMenu, Long> {
    List<CategoriaMenu> findByActivoTrueOrderByOrdenAsc();
}
