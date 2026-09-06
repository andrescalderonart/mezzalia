package com.mezzalia.mesa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MesaRepository extends JpaRepository<Mesa, Long> {

    /**
     * RF-10 PR4: reemplaza a la antigua findByActivaTrueOrderByCodigoAsc()
     * (sin tenantId), que dependía SOLO del @Filter de Hibernate para el
     * aislamiento entre tenants -- el mismo gap ya documentado y cerrado
     * para SucursalRepository (ver ese archivo): un SUPER_ADMIN, para quien
     * el filtro nunca se activa, o cualquier código que abra su propio
     * EntityManager fuera del ciclo de vida normal de un request HTTP
     * autenticado, recibía las mesas de TODOS los tenants. MesaService.
     * listarMesas() ya cerraba ese hueco con un guardia explícito
     * (tenantActual() lanzando antes de llegar acá) pero la query en sí
     * seguía sin scoping propio -- ahora lo tiene.
     */
    List<Mesa> findByTenantIdAndActivaTrueOrderByCodigoAsc(Long tenantId);

    /**
     * RF-10 PR4: variante acotada por sucursal, para cuando
     * MesaService.sucursalActual() devuelve un id concreto (MESERO con
     * sucursal ya seleccionada, o ADMIN_RESTAURANTE enfocado en una).
     */
    List<Mesa> findByTenantIdAndSucursalIdAndActivaTrueOrderByCodigoAsc(Long tenantId, Long sucursalId);

    /**
     * Usado por MesaService.crearMesa para el chequeo de unicidad de
     * `codigo` por tenant -- ver el comentario largo ahi mismo sobre por
     * que es un chequeo de aplicacion (leer-antes-de-escribir) y no una
     * restriccion UNIQUE de base de datos.
     */
    boolean existsByTenantIdAndCodigoAndActivaTrue(Long tenantId, String codigo);
}
