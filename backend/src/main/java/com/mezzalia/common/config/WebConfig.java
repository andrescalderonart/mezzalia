package com.mezzalia.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Sirve las imágenes subidas de platos (ver menu.servicio.AlmacenamientoImagenService)
 * como archivos estáticos bajo /uploads/**. Debe ser público -- ver
 * SecurityConfig, permitAll para /uploads/** -- porque el comensal ve estas
 * fotos en la carta digital sin haber iniciado sesión (RF-01).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String directorioUploads;

    public WebConfig(@Value("${app.uploads.dir}") String directorioUploads) {
        this.directorioUploads = directorioUploads;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String ubicacion = Path.of(directorioUploads).toAbsolutePath().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(ubicacion);
    }
}
