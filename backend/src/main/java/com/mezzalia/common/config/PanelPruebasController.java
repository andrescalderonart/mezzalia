package com.mezzalia.common.config;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Auditoria 2026-09-08: gate explicito para /test.html (panel de pruebas
 * manual, ver backend/src/main/resources/static/test.html).
 *
 * Spring Boot empaqueta TODO lo que hay en static/ en el jar sin excepcion
 * por perfil (los recursos estaticos no tienen @Profile) -- sin este
 * controlador, ese archivo quedaba servible en CUALQUIER ambiente, incluida
 * produccion, porque SecurityConfig lo permite sin autenticacion a
 * proposito (es una herramienta manual, no debe pedir login para poder
 * usarla). No otorgaba ningun permiso que la API real no otorgue ya, pero
 * era superficie de reconocimiento gratis (muestra la forma exacta de la
 * API de auth) y, si el perfil `dev` llegara a activarse por error en
 * produccion, tiene las credenciales demo precargadas en los inputs --
 * convertiria un error de configuracion en una explotacion de un clic.
 *
 * Este @GetMapping, al ser una ruta explicita, tiene prioridad sobre el
 * resource handler estatico por defecto de Spring (ver WebConfig) --
 * intercepta la request ANTES de que el archivo se sirva, y decide segun el
 * perfil activo en tiempo de ejecucion: el MISMO mecanismo (@Profile) que
 * ya usan SeedDatosDemoConfig/SeedSuperAdminConfig para no correr en
 * produccion, en vez de depender de un paso de build/deploy nuevo que
 * tambien se pudiera olvidar.
 *
 * 404 (no 403) a proposito: no hay ninguna identidad a la que "autorizar"
 * aca, mismo criterio de "no encontrado" que el resto de la API usa para no
 * confirmarle a un desconocido que el recurso existe.
 */
@RestController
public class PanelPruebasController {

    private final Environment environment;

    public PanelPruebasController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/test.html")
    public ResponseEntity<String> panelPruebas() throws IOException {
        if (!environment.acceptsProfiles(Profiles.of("dev"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        ClassPathResource recurso = new ClassPathResource("static/test.html");
        String contenido = new String(recurso.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(contenido);
    }
}
