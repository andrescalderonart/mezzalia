package com.mezzalia.identidad.seguridad;

import com.mezzalia.identidad.RolUsuario;
import com.mezzalia.identidad.excepcion.AuthExceptions;
import com.mezzalia.identidad.servicio.JwtService;
import com.mezzalia.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Único punto donde: (1) se autentica el request a partir del JWT, y
 * (2) se puebla + limpia TenantContext y se activa + desactiva el filtro de
 * Hibernate "tenantFilter" (ver EntidadConTenant). Se hace todo en un solo
 * filtro, no en dos separados, para que el finally de limpieza sea uno solo
 * y no dependa de que dos filtros distintos mantengan el orden correcto.
 *
 * A propósito NO lleva @Component: un OncePerRequestFilter que es bean de
 * Spring se auto-registra ADEMÁS como filtro genérico de servlet container
 * (vía FilterRegistrationBean automático de Spring Boot), fuera de la cadena
 * de Spring Security — sumado al addFilterBefore(...) explícito en
 * SecurityConfig, correría DOS VECES por request. Por eso el bean se declara
 * a mano en SecurityConfig (@Bean jwtAuthenticationFilter(...)), nunca vía
 * component scanning.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String PREFIJO_BEARER = "Bearer ";
    private static final String NOMBRE_FILTRO_TENANT = "tenantFilter";

    private final JwtService jwtService;

    @PersistenceContext
    private EntityManager entityManager;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        boolean filtroTenantActivado = false;

        try {
            if (header != null && header.startsWith(PREFIJO_BEARER)) {
                String token = header.substring(PREFIJO_BEARER.length());
                try {
                    Claims claims = jwtService.validarYObtenerClaims(token);
                    String username = jwtService.obtenerUsername(claims);
                    RolUsuario rol = jwtService.obtenerRol(claims);
                    Long tenantId = jwtService.obtenerTenantId(claims);

                    List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // tenantId ausente = SUPER_ADMIN (ver JwtService/UsuarioInterno):
                    // no se activa el filtro, sus queries corren sin restricción de
                    // tenant a propósito (necesita ver/gestionar todos los tenants).
                    if (tenantId != null) {
                        TenantContext.establecerTenantId(tenantId);
                        entityManager.unwrap(Session.class)
                                .enableFilter(NOMBRE_FILTRO_TENANT)
                                .setParameter("tenantId", tenantId);
                        filtroTenantActivado = true;
                    }
                } catch (AuthExceptions.TokenInvalidoException ex) {
                    // Token presente pero inválido/expirado: NO se lanza la excepción
                    // hacia arriba. Este filtro corre ANTES de ExceptionTranslationFilter
                    // en la cadena de Spring Security (se registra con
                    // addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)),
                    // así que una excepción sin capturar acá termina en un 500 crudo del
                    // servlet container, no en un 401 JSON como el resto de la API.
                    // Se deja el request sin autenticar y que la capa de autorización
                    // más adelante en la cadena decida 401/403 de forma estándar.
                    log.debug("JWT inválido en request a {}: {}", request.getRequestURI(), ex.getMessage());
                    SecurityContextHolder.clearContext();
                }
            }

            filterChain.doFilter(request, response);

        } finally {
            // CRÍTICO: el pool de hilos de Tomcat reusa hilos entre requests.
            // Sin este finally, un request N+1 atendido por el mismo hilo
            // heredaría el tenant_id del request N si algo corta el flujo
            // normal antes de llegar acá — exactamente la fuga entre tenants
            // que documentacion-tecnica.md §6 marca como "el bug más común en
            // SaaS multi-tenant mal implementados". Se limpia SIEMPRE.
            if (filtroTenantActivado) {
                entityManager.unwrap(Session.class).disableFilter(NOMBRE_FILTRO_TENANT);
            }
            TenantContext.limpiar();
        }
    }
}
