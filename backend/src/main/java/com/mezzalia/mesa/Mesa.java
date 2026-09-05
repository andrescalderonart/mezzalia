package com.mezzalia.mesa;

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
 * Una mesa física del restaurante, de UNA sucursal (RF-10,
 * documentacion-tecnica.md §7) -- ver sucursal.Sucursal para el alcance
 * real de esa entidad en este proyecto: existe y Mesa ya la referencia,
 * pero CategoriaMenu/ItemMenu/Pedido todavía no, así que la carta y los
 * pedidos siguen siendo tenant-scoped únicamente por ahora, no
 * sucursal-scoped -- ese es el siguiente paso, no algo que esta clase
 * resuelva por sí sola.
 */
@Entity
@Table(
        name = "mesas",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_mesas_tenant_codigo_unico",
                columnNames = {"tenant_id", "codigo_unico"}
        )
)
@Getter
@Setter
public class Mesa extends EntidadConTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /**
     * RF-10: a qué sucursal del tenant pertenece esta mesa -- validado en
     * MesaService.crearMesa contra SucursalService (pertenece al tenant
     * actual y está activa) antes de guardar, nunca solo confiado al FK de
     * V16__mesas_sucursal_id.sql como única defensa (mismo criterio de
     * "defensa explícita ademas del filtro/FK de base de datos" que el
     * resto de este proyecto).
     */
    @Column(name = "sucursal_id", nullable = false)
    private Long sucursalId;

    @Column(nullable = false, length = 50)
    private String codigo;

    /**
     * Sombra de `codigo`, mantenida por la APLICACION (no una columna
     * generada por la base de datos): vale lo mismo que `codigo` mientras
     * la mesa esta activa, y MesaService.desactivarMesa la pone en null al
     * desactivar. La restriccion UNIQUE real (ver el uniqueConstraints de
     * arriba y V14__mesas_unicidad_codigo_activo.sql) vive sobre ESTA
     * columna, no sobre `codigo` -- SQL estandar permite multiples NULL en
     * una columna UNIQUE (H2 y MySQL coinciden en esto), asi que cualquier
     * cantidad de mesas desactivadas puede compartir codigo_unico=null sin
     * chocar entre si, mientras que dos mesas ACTIVAS con el mismo codigo
     * para el mismo tenant si chocan de verdad -- el mismo efecto que un
     * indice unico parcial (`WHERE activa`), pero expresado con una
     * columna comun en vez de una expresion de base de datos. MySQL 8 SI
     * soporta indices funcionales, pero eso no es portable a como este
     * proyecto genera el esquema H2 de desarrollo/tests (ddl-auto desde
     * las entidades -- ver el mismo criterio en Pedido.claveIdempotencia).
     * Se mantiene a mano en vez de con una columna generada/@Formula de
     * Hibernate por el mismo motivo por el que se necesita escribirla (no
     * solo leerla): ponerla en null al desactivar es la mitad del
     * mecanismo.
     *
     * NOTA RF-10: esta unicidad sigue siendo (tenant_id, codigo_unico), NO
     * (sucursal_id, codigo_unico) -- una decisión deliberada de esta rama,
     * no un olvido: cambiarla a nivel de sucursal es una migración de datos
     * con su propio riesgo (¿qué pasa si dos mesas de sucursales distintas
     * del mismo tenant ya comparten código hoy?) que merece su propio
     * análisis, no un cambio de paso en la migración que introduce
     * sucursal_id.
     */
    @Column(name = "codigo_unico", length = 50)
    private String codigoUnico;

    /** Soft delete -- una SesionMesa/Pedido histórico podría seguir
     * referenciando esta mesa. */
    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    private void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
