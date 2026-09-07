package com.mezzalia.turno;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TurnoRepository extends JpaRepository<Turno, Long> {

    /**
     * "El" turno en curso de un usuario -- a lo sumo uno a la vez, ver
     * TurnoService.marcarEntrada (rechaza una segunda entrada mientras
     * exista uno con fin == null). `findFirst` en vez de exigir unicidad a
     * nivel de base de datos: es una invariante de aplicacion, no algo que
     * valga la pena modelar con un UNIQUE parcial (MySQL 8 no soporta
     * indices unicos filtrados de forma nativa sin columnas generadas
     * adicionales, y esta tabla no lo necesita para nada mas).
     */
    Optional<Turno> findFirstByUsuarioInternoIdAndFinIsNull(Long usuarioInternoId);

    /**
     * Uso exclusivo de TurnoService.horasPorDiaDe: todos los turnos de un
     * usuario cuyo INICIO caiga en la ventana de reporte -- un turno se le
     * atribuye entero al dia de su inicio (ver el javadoc de clase de
     * TurnoService), asi que alcanza con filtrar por inicio, nunca hace
     * falta mirar fin para decidir si un turno entra o no en el reporte.
     */
    List<Turno> findByUsuarioInternoIdAndInicioGreaterThanEqualOrderByInicioAsc(
            Long usuarioInternoId, LocalDateTime desde);
}
