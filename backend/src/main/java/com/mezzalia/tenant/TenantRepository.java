package com.mezzalia.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    boolean existsByNombre(String nombre);

    Optional<Tenant> findByNombre(String nombre);

    /**
     * Uso exclusivo de TenantService.listarTenants() (SUPER_ADMIN): a
     * diferencia de las entidades tenant-scoped del resto del sistema
     * (Mesa, ItemMenu, Pedido...), Tenant NO tiene su propia columna
     * tenant_id ni pasa por el filtro de Hibernate -- ES la tabla que
     * define cada tenant. Ordenado por nombre para una lista estable en la
     * UI, incluye tenants suspendidos a proposito (el super-admin necesita
     * verlos para poder reactivarlos).
     */
    List<Tenant> findAllByOrderByNombreAsc();
}
