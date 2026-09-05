package com.mezzalia.sucursal;

import com.mezzalia.tenant.EntidadConTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * RF-10/documentacion-tecnica.md §7: "Un tenant puede tener 1 o más
 * sucursales; cada sucursal tiene su propia carta, mesas y pedidos, pero
 * comparte el tenant y la facturación." Entidad que hasta esta rama NO
 * existía en ninguna parte del código -- Mesa.java, V8__mesas.sql y
 * V11__pedidos.sql documentaban explícitamente "sin sucursal_id, mismo
 * motivo: el resto del dominio tampoco lo modela todavía".
 *
 * ALCANCE REAL de esta rama (léase antes de asumir que RF-10 está
 * cerrado): esta entidad y su CRUD SÍ existen, y Mesa SÍ queda asociada a
 * una sucursal (ver Mesa.sucursalId, V16__mesas_sucursal_id.sql). Pero
 * CategoriaMenu/ItemMenu/Pedido NO se tocan en esta rama -- siguen siendo
 * tenant-scoped únicamente, sin distinguir sucursal. Eso significa que,
 * para un tenant con más de una sucursal activa, la carta y los pedidos
 * siguen siendo UNA sola lista compartida entre todas sus sucursales,
 * no una por sucursal como pide el RF completo. Tocar eso además implica
 * el camino público sin autenticación de creación de pedidos
 * (MenuPublicoController/PedidoService) -- el endpoint de mayor tráfico y
 * mayor sensibilidad de todo el sistema -- y este entorno no tiene forma
 * de compilar ni correr la suite de tests real antes de abrir el PR. Se
 * prefirió una rama mas chica y verificable (Sucursal + Mesa) a una que
 * tocara ese camino sin poder probarla. Completar
 * CategoriaMenu/ItemMenu/Pedido queda como el siguiente paso explícito,
 * no un olvido.
 *
 * Soft delete via `activa`, mismo patrón que CategoriaMenu/ItemMenu/Mesa:
 * una Mesa histórica podría seguir referenciando esta sucursal.
 */
@Entity
@Table(name = "sucursales")
@Getter
@Setter
public class Sucursal extends EntidadConTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 255)
    private String direccion;

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
