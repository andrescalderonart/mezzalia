package com.mezzalia.llamado;

import com.mezzalia.tenant.EntidadConTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Una mesa llamando al mesero o pidiendo la cuenta desde el QR (ver
 * V22__llamados_mesa.sql). Pendiente mientras {@code atendidoEn == null}.
 *
 * Sin relacion JPA con Mesa (solo mesaId), igual que Pedido: la sucursal se
 * resuelve via la mesa en las consultas (ver LlamadoRepository).
 */
@Entity
@Table(name = "llamados_mesa")
@Getter
@Setter
public class Llamado extends EntidadConTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "mesa_id", nullable = false)
    private Long mesaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoLlamado tipo;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "atendido_en")
    private LocalDateTime atendidoEn;

    @Column(name = "atendido_por", length = 100)
    private String atendidoPor;

    @PrePersist
    private void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }

    public boolean estaPendiente() {
        return atendidoEn == null;
    }
}
