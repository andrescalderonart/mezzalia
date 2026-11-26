package com.mezzalia.llamado.dto;

import com.mezzalia.llamado.TipoLlamado;
import jakarta.validation.constraints.NotNull;

public record CrearLlamadoRequest(@NotNull TipoLlamado tipo) {}
