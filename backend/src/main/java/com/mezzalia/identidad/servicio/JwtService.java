package com.mezzalia.identidad.servicio;

import com.mezzalia.identidad.RolUsuario;
import com.mezzalia.identidad.UsuarioInterno;
import com.mezzalia.identidad.excepcion.AuthExceptions;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Emite y valida el JWT de ACCESO únicamente (vida corta, ~15 min por
 * defecto). El refresh token es intencionalmente un token opaco guardado
 * (hasheado) en BD, no un JWT — ver AuthService y RefreshToken: así se puede
 * revocar antes de su expiración natural, cosa que un JWT stateless no
 * permite (documentacion-tecnica.md §5.2 pide refresh "rotable").
 */
@Service
public class JwtService {

    private final SecretKey clave;
    private final long expiracionAccesoMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secreto,
            @Value("${app.jwt.expiracion-acceso-ms}") long expiracionAccesoMs) {
        // HS256 exige una clave de al menos 256 bits (32 bytes) — si el secreto
        // configurado es más corto, Keys.hmacShaKeyFor falla rápido y ruidoso
        // en el arranque en vez de generar tokens con una clave débil en silencio.
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.expiracionAccesoMs = expiracionAccesoMs;
    }

    public String generarAccessToken(UsuarioInterno usuario) {
        return generarAccessToken(usuario, null);
    }

    /**
     * RF-10 PR4 (docs/propuesta-multisucursal-rf10.md §3): sucursalId es un
     * segundo claim OPCIONAL, independiente de tenantId -- ausente del
     * claim (no solo null), mismo motivo ya documentado para tenantId:
     * evita que un consumidor descuidado confunda "claim ausente" con
     * "sucursal 0".
     *
     * Distinto de tenantId en un punto importante: tenantId se resuelve
     * UNA sola vez, al emitir el PRIMER access token en el login (siempre
     * a partir del propio UsuarioInterno). sucursalId, en cambio, se
     * agrega recien en un SEGUNDO paso explicito y posterior al login (ver
     * AuthService.seleccionarSucursal/SesionController) -- el mesero elige
     * despues de autenticarse, nunca antes. Ese diseño tiene una
     * limitación conocida, documentada en AuthService.emitirPar, no acá:
     * este método no tiene forma de reconstruir, a partir de un
     * UsuarioInterno solo, cuál era la sucursal elegida en un token
     * anterior -- por eso cada llamada a /api/auth/refresh emite un access
     * token SIN sucursalId, sea cual sea el que tenía el token expirado.
     */
    public String generarAccessToken(UsuarioInterno usuario, Long sucursalId) {
        Date ahora = new Date();
        Date expira = new Date(ahora.getTime() + expiracionAccesoMs);

        var builder = Jwts.builder()
                .subject(usuario.getUsername())
                .claim("rol", usuario.getRol().name())
                .issuedAt(ahora)
                .expiration(expira);

        // tenantId ausente del claim (no solo null) para SUPER_ADMIN: evita
        // que un consumidor descuidado del token confunda "claim tenantId con
        // valor null" con "tenant 0" o falle deserializando un tipo inesperado.
        if (usuario.getTenantId() != null) {
            builder.claim("tenantId", usuario.getTenantId());
        }
        if (sucursalId != null) {
            builder.claim("sucursalId", sucursalId);
        }

        return builder.signWith(clave, Jwts.SIG.HS256).compact();
    }

    public Claims validarYObtenerClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(clave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new AuthExceptions.TokenInvalidoException("expirado");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AuthExceptions.TokenInvalidoException("firma o formato inválido");
        }
    }

    public String obtenerUsername(Claims claims) {
        return claims.getSubject();
    }

    public Long obtenerTenantId(Claims claims) {
        Object valor = claims.get("tenantId");
        if (valor == null) {
            return null;
        }
        return ((Number) valor).longValue();
    }

    public Long obtenerSucursalId(Claims claims) {
        Object valor = claims.get("sucursalId");
        if (valor == null) {
            return null;
        }
        return ((Number) valor).longValue();
    }

    public RolUsuario obtenerRol(Claims claims) {
        return RolUsuario.valueOf(claims.get("rol", String.class));
    }
}
