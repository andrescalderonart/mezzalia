package com.mezzalia.turno;

import com.mezzalia.tenant.EntidadConTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Un turno de trabajo de un UsuarioInterno: pedido explicito de Andres
 * (2026-09-07) para poder ver cuanto tiempo trabajado por dia lleva cada
 * usuario, algo que hasta ahora no existia en ningun lado del dominio (ver
 * V18__turnos.sql para la discusion completa de por que ni
 * identidad.AsignacionSucursal -- sin historial, hard-delete -- ni el
 * ciclo de vida del JWT sirven como proxy de esto).
 *
 * `fin == null` significa "en curso" -- es el unico estado que
 * TurnoService.marcarEntrada/marcarSalida necesitan distinguir, mismo
 * criterio que Turno no tiene un enum de estado propio (analogo a por que
 * AsignacionSucursal no tiene "activa/inactiva": el dato en si mismo, y no
 * un campo de estado aparte, es la fuente de verdad).
 *
 * `cierreAutomatico` distingue un turno que el propio usuario cerro
 * marcando salida de uno que TurnoService.cerrarSiVencido cerro solo por
 * haber superado TOPE_HORAS_TURNO sin que nadie marcara salida -- pensado
 * para que un ADMIN_RESTAURANTE revisando horasPorDiaDe pueda distinguir
 * "trabajo de verdad" de "seguramente se le olvido marcar salida", en vez
 * de mostrar un numero de horas sin ninguna señal de que puede ser un
 * artefacto.
 *
 * `sucursalId` es NULLABLE: ver V18__turnos.sql para por que (ADMIN_RESTAURANTE
 * puede no tener ninguna sucursal seleccionada).
 */
@Entity
@Table(name = "turnos")
@Getter
@Setter
public class Turno extends EntidadConTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioInternoId;

    @Column(name = "sucursal_id")
    private Long sucursalId;

    @Column(nullable = false)
    private LocalDateTime inicio;

    @Column
    private LocalDateTime fin;

    @Column(name = "cierre_automatico", nullable = false)
    private boolean cierreAutomatico = false;
}
