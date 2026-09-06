package com.mezzalia.identidad.seguridad;

import com.mezzalia.identidad.RolUsuario;
import com.mezzalia.identidad.excepcion.AuthExceptions;
import com.mezzalia.identidad.servicio.JwtService;
import com.mezzalia.sucursal.SucursalContext;
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
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

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
    // documentacion-tecnica.md §11: logging.pattern.console (application.yml)
    // ya espera %X{tenantId}/%X{requestId} en cada linea -- MDC es lo que los
    // llena. Van en las mismas claves que Logback lee, no son arbitrarias.
    private static final String CLAVE_MDC_TENANT_ID = "tenantId";
    private static final String CLAVE_MDC_REQUEST_ID = "requestId";
    private static final String CABECERA_REQUEST_ID = "X-Request-Id";

    private final JwtService jwtService;

    @PersistenceContext
    private EntityManager entityManager;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Se genera SIEMPRE, para TODO request (autenticado o no: login
        // fallido, carta pública sin login, etc. también deben poder
        // rastrearse en los logs) -- a propósito NUNCA se confía en un
        // "X-Request-Id" que mande el cliente para esto: ese header no está
        // validado/saneado en ningún lado, y logging.pattern.console
        // (application.yml) interpola %X{requestId} en un string que
        // simula JSON sin escapar comillas ni saltos de línea -- un cliente
        // que mande `X-Request-Id: a","nivel":"FAKE` podría forjar/romper
        // la línea de log completa (log injection). Generar un UUID propio
        // cierra ese vector por completo; igual se devuelve en la respuesta
        // (siempre, incluso en errores) para que un reporte de un usuario
        // real se pueda correlacionar con esta línea de log exacta.
        String requestId = UUID.randomUUID().toString();
        MDC.put(CLAVE_MDC_REQUEST_ID, requestId);
        response.setHeader(CABECERA_REQUEST_ID, requestId);

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
                        MDC.put(CLAVE_MDC_TENANT_ID, String.valueOf(tenantId));
                        entityManager.unwrap(Session.class)
                                .enableFilter(NOMBRE_FILTRO_TENANT)
                                .setParameter("tenantId", tenantId);
                        filtroTenantActivado = true;

                        // RF-10 PR4 (docs/propuesta-multisucursal-rf10.md §4):
                        // ver sucursal.SucursalContext para el porque de una
                        // clase aparte de TenantContext. requiereSeleccion()
                        // marca true SOLO para MESERO -- es el rol para el
                        // que MesaService/PedidoService EXIGEN una sucursal ya
                        // elegida antes de listar/operar (ver
                        // "sucursalActual()" en esos servicios); un
                        // ADMIN_RESTAURANTE sin este claim sigue viendo todas
                        // las sucursales de su tenant, comportamiento
                        // idéntico al que existía antes de esta rama.
                        SucursalContext.marcarRequiereSeleccion(rol == RolUsuario.MESERO);
                        Long sucursalId = jwtService.obtenerSucursalId(claims);
                        if (sucursalId != null) {
                            SucursalContext.establecerSucursalId(sucursalId);
                        }
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
            // Mismo motivo que TenantContext.limpiar() de arriba: el pool de
            // hilos de Tomcat reusa hilos entre requests, así que esto se
            // limpia SIEMPRE, no solo cuando se estableció explícitamente
            // (ver SucursalContext.limpiar() -- no-op barato si nunca se usó).
            SucursalContext.limpiar();
            // Mismo motivo, van juntas con TenantContext/SucursalContext:
            // MDC.remove de una clave nunca puesta es un no-op seguro, y sin
            // este limpiado el mismo hilo de Tomcat reusado en el request
            // N+1 arrancaría logueando el tenantId/requestId del request N
            // hasta que este filtro los pise de nuevo.
            MDC.remove(CLAVE_MDC_TENANT_ID);
            MDC.remove(CLAVE_MDC_REQUEST_ID);
        }
    }
}
