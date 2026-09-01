package com.mezzalia.pedido;

import com.mezzalia.pedido.seguridad.TelefonoEncriptadoConverter;
import com.mezzalia.tenant.EntidadConTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * RF-13/Habeas Data: datos del comensal, siempre opcionales -- esta fila
 * solo se crea si hubo consentimiento explicito (ver
 * PedidoService.crearComensalSiAplica). Nunca se persiste nombre/telefono
 * sin ese consentimiento, ni siquiera parcialmente.
 *
 * `telefono` se cifra en reposo con AES-256-GCM via TelefonoEncriptadoConverter
 * (documentacion-tecnica.md §10) -- la columna guarda base64(iv || cifrado),
 * nunca el numero en texto plano. El cifrado/descifrado es transparente: el
 * resto del codigo que llama a get/setTelefono() siempre ve texto plano, la
 * conversion ocurre en el limite JDBC (ver el converter).
 *
 * `nombre` NO se cifra a proposito: es un dato bastante menos sensible que
 * un numero de telefono (que si permite contactar/identificar a la persona
 * fuera de este sistema) -- una decision de alcance documentada, no un
 * olvido. El texto real de aviso de privacidad para el consentimiento sigue
 * pendiente de revision legal (documentacion-tecnica.md §15.2).
 */
@Entity
@Table(name = "comensales")
@Getter
@Setter
public class Comensal extends EntidadConTenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(length = 150)
    private String nombre;

    @Convert(converter = TelefonoEncriptadoConverter.class)
    @Column(length = 255)
    private String telefono;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    private void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}
