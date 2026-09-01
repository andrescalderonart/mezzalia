package com.mezzalia.mesa;

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
 * RF-08: el QR de una mesa codifica un token de sesión, no el id de la
 * mesa/tenant directamente -- un id secuencial sería adivinable/enumerable,
 * un token aleatorio (UUID v4, 122 bits de entropía real vía SecureRandom)
 * no. §9 exige que expire en un tiempo razonable para que fotografiar el QR
 * y pedir desde otro lugar deje de funcionar pasado ese tiempo -- ver
 * MesaService.HORAS_VIGENCIA_SESION.
 *
 * `tenant_id` se repite acá (igual que en CategoriaMenu/ItemMenu) por
 * consistencia con el resto del dominio, aunque la resolución pública por
 * token (MesaService.resolverSesionValida) NO depende del filtro de
 * tenant -- el token mismo es la credencial; el tenant se descubre a partir
 * de él, no al revés. El filtro de Hibernate importa recién para lo que pasa
 * DESPUÉS de resolver esta sesión (ver TenantFilterActivator) y para
 * eventuales queries admin futuras (listar sesiones de un tenant).
 */
@Entity
@Table(name = "sesiones_mesa")
@Getter
@Setter
public class SesionMesa extends EntidadConTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "mesa_id", nullable = false)
    private Long mesaId;

    @Column(nullable = false, unique = true, length = 36)
    private String token;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private LocalDateTime creadaEn;

    @PrePersist
    private void alCrear() {
        if (creadaEn == null) {
            creadaEn = LocalDateTime.now();
        }
    }
}
