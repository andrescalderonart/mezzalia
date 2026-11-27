package com.mezzalia.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * Fija la zona horaria de la JVM al arrancar (2026-11-27).
 *
 * Todo el dominio usa LocalDateTime.now() (pedidos, turnos, llamados,
 * sesiones de mesa) y la API devuelve esas fechas SIN zona. Si el hosting
 * corre en UTC (lo normal), las horas quedaban 5 horas corridas respecto a
 * Colombia: la cocina mostraba pedidos "de hace 300 minutos", el reporte
 * cortaba los dias a las 7 p. m. y el cierre de turnos se desfasaba. Fijarla
 * aqui la hace independiente del servidor, y coincide con la URL de MySQL
 * (serverTimezone=America/Bogota).
 *
 * Limitacion conocida, a proposito: es UNA zona para toda la aplicacion. El
 * dia que haya restaurantes en otro pais (Tenant.zonaHoraria ya existe), lo
 * correcto es guardar instantes con zona (Instant/OffsetDateTime) y
 * convertir por tenant; mientras tanto basta con APP_ZONA_HORARIA.
 */
@Slf4j
@Configuration
public class ZonaHorariaConfig {

    @Value("${app.zona-horaria:America/Bogota}")
    private String zonaHoraria;

    @PostConstruct
    void fijarZonaHoraria() {
        TimeZone.setDefault(TimeZone.getTimeZone(zonaHoraria));
        log.info("Zona horaria de la aplicacion: {}", TimeZone.getDefault().getID());
    }
}
