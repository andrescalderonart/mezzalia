package com.mezzalia.tenant;

import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Superclase marcadora para cualquier entidad de dominio que pertenece a un tenant.
 *
 * Implementa el "enforcement centralizado" que exige documentacion-tecnica.md §6:
 * ninguna entidad de negocio (menu, pedidos, usuarios internos, etc.) debería depender
 * de que cada desarrollador se acuerde de agregar `WHERE tenant_id = ?` a mano en cada
 * repositorio nuevo.
 *
 * Cómo se usa:
 * 1. La entidad extiende esta clase (hereda el @FilterDef/@Filter).
 * 2. La entidad declara su propia columna `tenant_id` con el nombre y nullability que
 *    le correspondan (la mayoría NOT NULL; ver UsuarioInterno para la única excepción
 *    conocida: filas SUPER_ADMIN sin tenant).
 * 3. TenantHibernateFilterActivator (activado desde JwtAuthenticationFilter) llama a
 *    session.enableFilter("tenantFilter").setParameter("tenantId", ...) una vez por
 *    request autenticado.
 *
 * IMPORTANTE — esto es un mecanismo de defensa en profundidad, no el único control:
 * el filtro de Hibernate solo se activa dentro del ciclo de vida normal de un request
 * HTTP autenticado (ver JwtAuthenticationFilter). Cualquier código que abra su propio
 * EntityManager fuera de ese camino (un job programado, un hilo async, un test) NO
 * tiene el filtro activado automáticamente y debe seguir filtrando por tenant_id de
 * forma explícita en la query. No asuman que "extender esta clase" es suficiente por
 * sí solo.
 */
@MappedSuperclass
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class EntidadConTenant {
}
