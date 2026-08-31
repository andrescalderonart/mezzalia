package com.mezzalia.identidad.dto;

public record TokensRespuesta(
        String accessToken,
        String refreshToken,
        long expiraEnSegundos
) {}
