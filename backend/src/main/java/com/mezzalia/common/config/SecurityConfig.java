package com.mezzalia.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mezzalia.identidad.seguridad.JwtAuthenticationFilter;
import com.mezzalia.identidad.servicio.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Instant;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Bean declarado a mano (no @Component en la clase del filtro) — ver el
    // comentario en JwtAuthenticationFilter.java sobre por qué un
    // OncePerRequestFilter con @Component se registraría dos veces.
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
        return new JwtAuthenticationFilter(jwtService);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Auditoria 2026-09-08: CSP como segunda capa contra robo de
            // sesion via XSS -- el access y el refresh token viven en
            // localStorage (ver frontend/app-admin/src/api/client.js), no en
            // cookies httpOnly, porque no hay CSRF que mitigar (CSRF esta
            // deshabilitado arriba a proposito). Eso deja "que nunca haya
            // XSS" como el unico perimetro real; hoy no hay ningun
            // dangerouslySetInnerHTML/innerHTML en ninguno de los dos
            // frontends, pero esto es la capa que hay que tener puesta ANTES
            // de necesitarla, no despues. style-src/font-src incluyen Google
            // Fonts porque ambos index.html lo cargan asi (ver
            // frontend/*/index.html). Cubre lo que ESTE backend sirve
            // directamente (API, /uploads, /test.html) -- si en produccion
            // el HTML/JS compilado de las SPA termina servido por un reverse
            // proxy separado en vez de pasar por Spring, hay que replicar
            // esta misma cabecera ahi tambien; no se puede confirmar esa
            // topologia desde este repo todavia (ver docker-compose.yml,
            // servicio backend comentado).
            .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; "
                + "script-src 'self'; "
                + "style-src 'self' https://fonts.googleapis.com; "
                + "font-src 'self' https://fonts.gstatic.com; "
                + "img-src 'self' data:; "
                + "connect-src 'self'; "
                + "base-uri 'self'; "
                + "form-action 'self'; "
                + "frame-ancestors 'none'")))
            .authorizeHttpRequests(auth -> auth
                // Login/refresh/logout: por definición, sin autenticación previa.
                .requestMatchers("/api/auth/**").permitAll()
                // Carta pública, branding y pedidos sin login (QR del comensal)
                .requestMatchers("/api/publico/**").permitAll()
                // Panel de pruebas manual en dev (ver docs). El permitAll sigue siendo
                // necesario (es una herramienta sin login), pero YA NO alcanza por si solo
                // para mantenerlo fuera de produccion -- ver common.config.PanelPruebasController
                // (auditoria 2026-09-08), que devuelve 404 salvo que el perfil dev este activo.
                .requestMatchers("/test.html").permitAll()
                // Fotos de platos: publicas porque el comensal las ve en la carta sin login (RF-01)
                .requestMatchers("/uploads/**").permitAll()
                // Health check del hosting/orquestador: sin login (el balanceador no
                // tiene token). Solo health esta expuesto (ver management.* en
                // application.yml); cualquier otro /actuator/** cae en
                // anyRequest().authenticated() y ademas no esta publicado.
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                // Gestión de tenants: exclusivo del equipo Mezzalia (SUPER_ADMIN)
                .requestMatchers("/api/admin/tenants/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/api/superadmin/**").hasRole("SUPER_ADMIN")
                // Todo lo demás del panel del restaurante requiere usuario autenticado
                .requestMatchers("/api/admin/**").authenticated()
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptionHandling -> exceptionHandling
                // Sin esto, Spring Security responde 401/403 sin cuerpo (o con el
                // formato por defecto del servlet container) para fallos de auth,
                // inconsistente con el JSON {timestamp,status,error,mensaje} que
                // devuelve el resto de la API vía GlobalExceptionHandler.
                .authenticationEntryPoint((request, response, authException) ->
                    escribirError(response, HttpStatus.UNAUTHORIZED, "No autenticado"))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    escribirError(response, HttpStatus.FORBIDDEN, "No tiene permisos para este recurso"))
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void escribirError(HttpServletResponse response, HttpStatus status, String mensaje) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "mensaje", mensaje
        )));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
