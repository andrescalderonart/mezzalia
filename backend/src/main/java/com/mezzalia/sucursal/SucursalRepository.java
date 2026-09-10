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
     * obtenerX(id). MesaService.listarMesas() y PedidoService.
     * listarPedidos() ya cerraron la misma clase de gap en RF-10 PR4 (ver
     * MesaRepository/PedidoRepository) -- no queda ningun listado
     * conocido en el proyecto que dependa solo del filtro de Hibernate.
     */
    List<Sucursal> findByTenantIdAndActivaTrueOrderByNombreAsc(Long tenantId);

    long countByTenantIdAndActivaTrue(Long tenantId);

    /**
     * Uso exclusivo de AsignacionSucursalService.listarSucursalesDe:
     * devuelve las sucursales por id SIN filtrar por activa -- a
     * diferencia del resto de los listados de este repositorio, esta
     * vista es para que ADMIN_RESTAURANTE pueda auditar/gestionar
     * asignaciones existentes, incluidas las que apuntan a una sucursal
     * ya desactivada (SucursalDto.activa le informa al frontend cual es
     * cual, no se ocultan del todo).
     */
    List<Sucursal> findByTenantIdAndIdInOrderByNombreAsc(Long tenantId, List<Long> ids);

    /**
     * Uso exclusivo de AuthService.misSucursalesDisponibles para
     * ADMIN_RESTAURANTE, cuando llama a POST /api/admin/sesion/sucursal
     * habiendo elegido activamente una sucursal para "enfocar" su sesion
     * (ver el javadoc de MesaService.sucursalActual()) -- filtra
     * activa=true a proposito: no tendria sentido dejarlo enfocar una
     * sucursal ya cerrada.
     */
    List<Sucursal> findByTenantIdAndActivaTrueAndIdInOrderByNombreAsc(Long tenantId, List<Long> ids);

    /**
     * Uso exclusivo de AuthService.seleccionarSucursal: valida, en una
     * sola consulta, que el id recibido en POST /api/admin/sesion/sucursal
     * sea una sucursal ACTIVA del TENANT del usuario autenticado -- mismo
     * criterio de "no confiar solo en lo que manda el cliente" que
     * MesaService.crearMesa aplica via SucursalService.
     * obtenerSucursalActivaDelTenantActual (acá no se reusa ese método
     * porque AuthService no depende de TenantContext -- resuelve el
     * tenant directo desde el UsuarioInterno autenticado, ver el javadoc
     * de AuthService.seleccionarSucursal).
     */
    boolean existsByIdAndTenantIdAndActivaTrue(Long id, Long tenantId);

    /**
     * Uso exclusivo de SucursalService.listarSucursalesTodas, para la
     * vista de gestión de ADMIN_RESTAURANTE (SucursalesPage.jsx): a
     * diferencia de findByTenantIdAndActivaTrueOrderByNombreAsc (que
     * respalda el GET general, también consumido por MESERO para elegir
     * sucursal -- ver SucursalAdminController), esta sí incluye las
     * inactivas, porque el ADMIN_RESTAURANTE necesita poder reactivarlas o
     * ver sus meseros asignados. No exponer este método vía el GET
     * general: el filtro de activa=true ahí es intencional, no un
     * descuido.
     */
    List<Sucursal> findByTenantIdOrderByNombreAsc(Long tenantId);
}
