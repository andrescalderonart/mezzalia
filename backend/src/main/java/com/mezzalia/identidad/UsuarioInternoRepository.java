package com.mezzalia.identidad;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioInternoRepository extends JpaRepository<UsuarioInterno, Long> {

    /**
     * Búsqueda GLOBAL (sin filtro de tenant) a propósito: en el momento del
     * login todavía no sabemos el tenant del usuario — es justamente lo que
     * este método resuelve, junto con el rol, antes de emitir el JWT. Por
     * eso el username es único a nivel de toda la plataforma (decisión de
     * producto: "usuario y contraseña", sin selector de restaurante previo).
     */
    Optional<UsuarioInterno> findByUsername(String username);

    boolean existsByUsername(String username);

    /** Para el arranque en produccion (PrimerSuperAdminConfig): ¿ya hay alguno? */
    boolean existsByRol(RolUsuario rol);

    /**
     * Uso exclusivo de UsuarioInternoAdminService (RF-09: gestión de
     * usuarios internos con roles, §2 del MVP -- "gestión de usuarios
     * internos con roles" estaba listada como incluida en el panel del
     * restaurante pero nunca se había construido, solo seeds fijos vía
     * SeedDatosDemoConfig). Filtra por tenant_id explícito además del
     * filtro de Hibernate -- mismo motivo documentado en el resto del
     * proyecto (MenuService, MesaService...): SUPER_ADMIN no tiene
     * tenant_id propio y el filtro nunca se activa para él, así que
     * cualquier query de listado necesita su propio guardia explícito
     * (ver UsuarioInternoAdminService.tenantActual()) además de esta
     * condición de repositorio.
     */
    List<UsuarioInterno> findByTenantIdOrderByUsernameAsc(Long tenantId);

    /**
     * Uso exclusivo de AsignacionSucursalService.listarUsuariosDe: una vez
     * resueltos los ids de usuario asignados a una sucursal (via
     * AsignacionSucursalRepository.findBySucursalIdOrderByUsuarioInternoId),
     * esto los trae ya ordenados por username y con el mismo guardia
     * explícito de tenant_id que findByTenantIdOrderByUsernameAsc -- las
     * filas de asignacion en teoría ya solo pueden apuntar a usuarios del
     * mismo tenant (ver AsignacionSucursalService.asignar), pero esta
     * consulta no confía únicamente en esa invariante para no filtrar por
     * accidente usuarios de otro tenant si esa garantía llegara a romperse.
     */
    List<UsuarioInterno> findByIdInAndTenantIdOrderByUsernameAsc(List<Long> ids, Long tenantId);
}
