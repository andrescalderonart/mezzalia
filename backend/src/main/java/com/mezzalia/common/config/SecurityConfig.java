package com.mezzalia.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mezzalia.identidad.seguridad.JwtAuthenticationFilter;
import com.mezzalia.identidad.servicio.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationFilter;
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
            .authorizeHttpRequests(auth -> auth
                // Login/refresh/logout: por definición, sin autenticación previa.
                .requestMatchers("/api/auth/**").permitAll()
                // Carta pública, branding y pedidos sin login (QR del comensal)
                .requestMatchers("/api/publico/**").permitAll()
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
