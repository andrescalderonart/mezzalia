package com.mezzalia.tenant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenants")
@Getter
@Setter
public class Tenant {

    public static final String ESTADO_ACTIVO = "ACTIVO";
    public static final String ESTADO_SUSPENDIDO = "SUSPENDIDO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    @Column(nullable = false)
    private String plan;

    @Column(nullable = false)
    private String moneda;

    @Column(name = "zona_horaria", nullable = false)
    private String zonaHoraria;

    @Column(nullable = false)
    private String estado;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    // ── Branding ────────────────────────────────────────────────────────────

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "color_primario", length = 7)
    private String colorPrimario;

    @Column(name = "color_secundario", length = 7)
    private String colorSecundario;

    // ── Ciclo de vida ────────────────────────────────────────────────────────

    @PrePersist
    private void antesDeGuardar() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
        if (estado == null) {
            estado = ESTADO_ACTIVO;
        }
    }

    /**
     * RF-11: un tenant suspendido conserva sus datos pero deja de ser servido
     * por los endpoints públicos (carta, branding). No confundir con borrado.
     */
    public boolean estaSuspendido() {
        return ESTADO_SUSPENDIDO.equalsIgnoreCase(estado);
    }
}
