package com.mezzalia.menu.dto;

import com.mezzalia.tenant.dto.BrandingDto;

import java.util.List;

/**
 * Respuesta única para GET /api/publico/mesas/{token}/carta: incluye el
 * branding embebido a propósito, para que app-cliente resuelva la carta
 * completa (incluyendo cómo pintarla) en UNA sola llamada -- relevante para
 * el NFR de "la carta debe cargar en menos de 2s en wifi de restaurante
 * típica" (documentacion-tecnica.md §4: menos round-trips, no más). También
 * evita exponerle a app-cliente el tenantId crudo (secuencial, enumerable)
 * que sí necesita TenantPublicoController -- ver el comentario ahí.
 */
public record CartaPublicaDto(
        Long mesaId,
        String nombreRestaurante,
        BrandingDto branding,
        List<CategoriaPublicaDto> categorias
) {}
