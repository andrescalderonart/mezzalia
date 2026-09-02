package com.mezzalia.mesa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MesaRepository extends JpaRepository<Mesa, Long> {
    List<Mesa> findByActivaTrueOrderByCodigoAsc();

    /**
     * Usado por MesaService.crearMesa para el chequeo de unicidad de
     * `codigo` por tenant -- ver el comentario largo ahi mismo sobre por
     * que es un chequeo de aplicacion (leer-antes-de-escribir) y no una
     * restriccion UNIQUE de base de datos.
     */
    boolean existsByTenantIdAndCodigoAndActivaTrue(Long tenantId, String codigo);
}
