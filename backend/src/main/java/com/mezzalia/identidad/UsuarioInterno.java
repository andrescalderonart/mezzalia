package com.mezzalia.identidad;

import com.mezzalia.tenant.EntidadConTenant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Usuario del panel del restaurante o del panel super-admin (RF-09).
 *
 * tenant_id es NULLABLE a propósito: es la única excepción conocida a "todo
 * lo de negocio tiene tenant" (§6) porque SUPER_ADMIN es el equipo de
 * Mezzalia, no pertenece a ningún restaurante. Todo lo demás (ADMIN_RESTAURANTE,
 * MESERO) DEBE tener tenant_id — se valida en @PrePersist/@PreUpdate acá abajo
 * y también a nivel de base de datos con un CHECK constraint (ver
 * V3__usuarios_internos.sql) para que la regla no dependa solo de que el
 * código Java se acuerde de aplicarla.
 */
@Entity
@Table(name = "usuarios_internos")
@Getter
@Setter
public class UsuarioInterno extends EntidadConTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RolUsuario rol;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    @PreUpdate
    private void validarInvariantes() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
        boolean esSuperAdmin = rol == RolUsuario.SUPER_ADMIN;
        if (esSuperAdmin && tenantId != null) {
            throw new IllegalStateException(
                    "Un usuario SUPER_ADMIN no debe tener tenant_id asignado (username=" + username + ")");
        }
        if (!esSuperAdmin && tenantId == null) {
            throw new IllegalStateException(
                    "Un usuario con rol " + rol + " requiere tenant_id (username=" + username + ")");
        }
    }
}
