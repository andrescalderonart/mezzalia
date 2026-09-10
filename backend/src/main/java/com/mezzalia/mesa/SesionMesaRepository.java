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

    /**
     * Uso exclusivo de MesaService.obtenerSesionesActivas: trae, en una sola
     * consulta, la sesión vigente de cada mesa de la lista (si tiene) --
     * evita N+1 queries al listar mesas con GET /api/admin/mesas.
     * generarSesion() ya garantiza que exista a lo sumo una vigente por
     * mesa en el uso normal (invalida cualquier anterior antes de crear la
     * nueva), pero esta query no depende de esa garantía: si alguna vez
     * hubiera más de una para la misma mesa, el llamador se queda con la
     * de expiraEn más lejano (ver el Comparator en MesaService).
     */
    List<SesionMesa> findByMesaIdInAndExpiraEnAfter(List<Long> mesaIds, LocalDateTime ahora);
}
