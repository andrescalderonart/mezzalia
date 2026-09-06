package com.mezzalia.sucursal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SucursalRepository extends JpaRepository<Sucursal, Long> {
    List<Sucursal> findByActivaTrueOrderByNombreAsc();

    long countByTenantIdAndActivaTrue(Long tenantId);
}
