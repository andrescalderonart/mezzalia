package com.mezzalia.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistorialPrecioItemRepository extends JpaRepository<HistorialPrecioItem, Long> {
    List<HistorialPrecioItem> findByItemIdOrderByModificadoEnDesc(Long itemId);
}
