package com.mezzalia.identidad;

import com.mezzalia.tenant.EntidadConTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * RF-10 PR4 (docs/propuesta-multisucursal-rf10.md §3/§5): asignacion
 * muchos-a-muchos entre UsuarioInterno (rol MESERO) y Sucursal -- ver
 * identidad.servicio.AsignacionSucursalService para las reglas de negocio
 * (gestion exclusiva de ADMIN_RESTAURANTE, usuario asignado exclusivamente
 * MESERO -- un ADMIN_RESTAURANTE nunca necesita fila aca, ya ve todas las
 * sucursales de su tenant sin restriccion).
 *
 * Tabla de asignacion pura, sin historial: a diferencia del resto del
 * dominio (Mesa.activa, Sucursal.activa, UsuarioInterno.activo), desasignar
 * BORRA la fila (hard delete) en vez de soft-delete -- esta fila no es un
 * registro de negocio con valor historico propio (nadie necesita saber
 * "este mesero estuvo asignado a esta sucursal entre tal y tal fecha"), es
 * un permiso vigente o no vigente, igual que una fila de una tabla de
 * roles/permisos tipica (ver AsignacionSucursalRepository.
 * deleteByUsuarioInternoIdAndSucursalId).
 *
 * tenant_id explicito (no derivado via join a usuario/sucursal) por el
 * mismo motivo documentado en V17__usuario_sucursales.sql: permite filtrar
 * esta tabla sin JOIN y sirve de chequeo redundante a nivel de FK de base
 * de datos.
 */
@Entity
@Table(
        name = "usuario_sucursales",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_usuario_sucursales_usuario_sucursal",
                columnNames = {"usuario_id", "sucursal_id"}
        )
)
@Getter
@Setter
public class AsignacionSucursal extends EntidadConTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioInternoId;

    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    private void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
