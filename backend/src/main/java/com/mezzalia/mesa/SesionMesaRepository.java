package com.mezzalia.mesa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SesionMesaRepository extends JpaRepository<SesionMesa, Long> {

    /**
     * Sin filtro de tenant a propósito: en el camino público todavía no se
     * conoce el tenant -- se descubre a partir de esta búsqueda. `token` es
     * UNIQUE, así que no hay ambigüedad posible entre tenants distintos.
     */
    Optional<SesionMesa> findByTokenAndExpiraEnAfter(String token, LocalDateTime ahora);

    List<SesionMesa> findByMesaIdAndExpiraEnAfter(Long mesaId, LocalDateTime ahora);
}
