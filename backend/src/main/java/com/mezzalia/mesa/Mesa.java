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
 * Una mesa física del restaurante. Hoy sin `sucursal_id` propio: el resto
 * del dominio ya implementado (menu.CategoriaMenu/ItemMenu) todavía no
 * modela sucursales -- solo tenant_id -- así que Mesa sigue el mismo nivel
 * de granularidad real en vez de adelantar un concepto (Sucursal) que no
 * existe todavía en ninguna otra parte del código. documentacion-tecnica.md
 * §7 sí modela SUCURSAL explícitamente; si se implementa más adelante, esta
 * clase necesita `sucursal_id` igual que categorias_menu/items_menu -- no
 * es un olvido, es una simplificación deliberada consistente con lo ya
 * construido.
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
     */
    @Column(name = "codigo_unico", length = 50)
    private String codigoUnico;

    /** Soft delete -- una SesionMesa/Pedido histórico podría seguir
     * referenciando esta mesa el día que exista el módulo pedidos. */
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
