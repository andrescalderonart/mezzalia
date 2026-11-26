package com.mezzalia.llamado.dto;

import com.mezzalia.llamado.Llamado;
import com.mezzalia.llamado.TipoLlamado;

import java.time.LocalDateTime;

public record LlamadoDto(Long id, Long mesaId, TipoLlamado tipo, LocalDateTime creadoEn, boolean pendiente) {
    public static LlamadoDto desde(Llamado l) {
        return new LlamadoDto(l.getId(), l.getMesaId(), l.getTipo(), l.getCreadoEn(), l.estaPendiente());
    }
}
