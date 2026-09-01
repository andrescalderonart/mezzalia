package com.mezzalia.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Activa el filtro de Hibernate "tenantFilter" (ver EntidadConTenant) y
 * puebla TenantContext para el hilo actual, fuera del único camino que hasta
 * ahora lo hacía (JwtAuthenticationFilter, atado a un JWT autenticado).
 *
 * Por qué existe: los endpoints públicos bajo /api/publico/** (ver
 * menu.controlador.MenuPublicoController) resuelven el tenant a partir de un
 * token de sesión de mesa, NO de un JWT -- no hay Authorization header, así
 * que JwtAuthenticationFilter nunca activa el filtro para ese request. Sin
 * este componente, reutilizar MenuService desde el camino público (a
 * propósito, para no duplicar lógica ya probada) ejecutaría sus queries CON
 * EL FILTRO DE TENANT DESACTIVADO -- exactamente la fuga entre tenants que
 * documentacion-tecnica.md §6 marca como el bug más común y más grave en un
 * SaaS multi-tenant mal implementado.
 *
 * EntidadConTenant.java ya advertía esto explícitamente: "Cualquier código
 * que abra su propio EntityManager fuera de [el camino de
 * JwtAuthenticationFilter] ... debe seguir filtrando por tenant_id de forma
 * explícita." Este componente es esa pieza, para el camino público.
 *
 * A propósito NO se reutiliza/refactoriza JwtAuthenticationFilter para
 * compartir este código: es un filtro de seguridad ya verificado en
 * producción (ver el bug real de @Order documentado en
 * common.excepcion.GlobalExceptionHandler) y tocarlo sin poder correr la
 * suite de tests desde este entorno es un riesgo innecesario. La duplicación
 * de estas pocas líneas es el costo aceptado de no arriesgar ese código.
 */
@Component
public class TenantFilterActivator {

    private static final String NOMBRE_FILTRO_TENANT = "tenantFilter";

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Ejecuta `accion` con TenantContext y el filtro de Hibernate activados
     * para `tenantId`, garantizando la limpieza de ambos al terminar (éxito
     * o excepción) -- mismo patrón try/finally que JwtAuthenticationFilter,
     * necesario porque el pool de hilos del servidor reusa hilos entre
     * requests.
     */
    public <T> T ejecutarConTenant(Long tenantId, Supplier<T> accion) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId no puede ser null al activar el filtro de tenant");
        }
        Session session = entityManager.unwrap(Session.class);
        TenantContext.establecerTenantId(tenantId);
        session.enableFilter(NOMBRE_FILTRO_TENANT).setParameter("tenantId", tenantId);
        try {
            return accion.get();
        } finally {
            session.disableFilter(NOMBRE_FILTRO_TENANT);
            TenantContext.limpiar();
        }
    }
}
