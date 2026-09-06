package com.mezzalia.sucursal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SucursalRepository extends JpaRepository<Sucursal, Long> {

    /**
     * Scoped por tenant_id explicito en la query -- NO se deja el
     * aislamiento entre tenants solo en manos del @Filter de Hibernate
     * (ver el hallazgo real en SucursalServiceTest.
     * unTenantNuncaVeSucursalesDeOtroTenant: ese test corre en
     * @DataJpaTest, donde el filtro NUNCA se activa -- lo mismo que pasa
     * en produccion si algun dia una request llega a este metodo sin
     * pasar por JwtAuthenticationFilter). Mismo criterio de
     * defensa-en-profundidad que el resto del proyecto aplica a
     * obtenerX(id); MesaService.listarMesas()/MenuService equivalentes
     * hoy SI dependen unicamente del filtro para este caso -- no se tocan
     * en este fix por alcance, pero es la misma clase de gap.
     */
    List<Sucursal> findByTenantIdAndActivaTrueOrderByNombreAsc(Long tenantId);

    long countByTenantIdAndActivaTrue(Long tenantId);
}
