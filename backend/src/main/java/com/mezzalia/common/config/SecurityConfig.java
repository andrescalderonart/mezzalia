package com.mezzalia.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // TODO (módulo identidad): inyectar JwtAuthenticationFilter y agregar
    //      .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
    //      El filtro extrae tenant_id y rol del JWT y los almacena en SecurityContext + TenantContext.

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Carta pública, branding y pedidos sin login (QR del comensal)
                .requestMatchers("/api/publico/**").permitAll()
                // Gestión de tenants: exclusivo del equipo Mezzalia (SUPER_ADMIN)
                .requestMatchers("/api/admin/tenants/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/api/superadmin/**").hasRole("SUPER_ADMIN")
                // Todo lo demás del panel del restaurante requiere usuario autenticado
                .requestMatchers("/api/admin/**").authenticated()
                .anyRequest().authenticated()
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}