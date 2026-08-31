package com.mezzalia.identidad;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Se guarda el HASH (SHA-256) del refresh token, nunca el valor en claro —
 * ver comentario en V4__refresh_tokens.sql. No extiende EntidadConTenant: el
 * aislamiento acá se hace por usuario_id, no por tenant_id directamente (un
 * refresh token es intrínsecamente de UN usuario, y ese usuario ya está
 * atado a su tenant en UsuarioInterno).
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(nullable = false)
    private boolean revocado = false;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    protected RefreshToken() {
        // JPA
    }

    public RefreshToken(Long usuarioId, String tokenHash, LocalDateTime expiraEn) {
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.expiraEn = expiraEn;
        this.creadoEn = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public LocalDateTime getExpiraEn() {
        return expiraEn;
    }

    public boolean isRevocado() {
        return revocado;
    }

    public void revocar() {
        this.revocado = true;
    }

    public boolean estaVigente() {
        return !revocado && expiraEn.isAfter(LocalDateTime.now());
    }
}
