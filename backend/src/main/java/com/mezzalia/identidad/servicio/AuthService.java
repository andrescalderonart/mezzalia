package com.mezzalia.identidad.servicio;

import com.mezzalia.identidad.RefreshToken;
import com.mezzalia.identidad.RefreshTokenRepository;
import com.mezzalia.identidad.UsuarioInterno;
import com.mezzalia.identidad.UsuarioInternoRepository;
import com.mezzalia.identidad.dto.TokensRespuesta;
import com.mezzalia.identidad.excepcion.AuthExceptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class AuthService {

    private static final SecureRandom GENERADOR_ALEATORIO = new SecureRandom();

    private final UsuarioInternoRepository usuarioInternoRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final long expiracionAccesoMs;
    private final long expiracionRefreshMs;

    public AuthService(
            UsuarioInternoRepository usuarioInternoRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginAttemptService loginAttemptService,
            @Value("${app.jwt.expiracion-acceso-ms}") long expiracionAccesoMs,
            @Value("${app.jwt.expiracion-refresh-ms}") long expiracionRefreshMs) {
        this.usuarioInternoRepository = usuarioInternoRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.expiracionAccesoMs = expiracionAccesoMs;
        this.expiracionRefreshMs = expiracionRefreshMs;
    }

    // Hash BCrypt de un valor que nunca se usa como contraseña real. Se compara
    // contra esto cuando el usuario no existe, para que passwordEncoder.matches()
    // haga el mismo trabajo computacional en ambos casos y el tiempo de
    // respuesta no delate "usuario no existe" vs "contraseña incorrecta".
    private static final String HASH_FICTICIO_PARA_TIMING =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                    .encode("valor-que-nunca-sera-una-contrasena-real");

    @Transactional
    public TokensRespuesta login(String username, String password) {
        loginAttemptService.verificarNoBloqueado(username);

        UsuarioInterno usuario = usuarioInternoRepository.findByUsername(username).orElse(null);
        String hashParaComparar = usuario != null ? usuario.getPasswordHash() : HASH_FICTICIO_PARA_TIMING;
        boolean contrasenaCoincide = passwordEncoder.matches(password, hashParaComparar);

        if (usuario == null || !contrasenaCoincide) {
            loginAttemptService.registrarFallo(username);
            throw new AuthExceptions.CredencialesInvalidasException();
        }
        if (!usuario.isActivo()) {
            // No cuenta como intento fallido de fuerza bruta (la contraseña
            // era correcta); pero tampoco se emite token.
            throw new AuthExceptions.CuentaInactivaException();
        }

        loginAttemptService.registrarExito(username);
        return emitirPar(usuario);
    }

    // noRollbackFor a propósito: si se detecta reuso, la revocación masiva
    // (revocarTodosDelUsuario) DEBE quedar guardada aunque el método termine
    // lanzando RefreshTokenInvalidoException para reportarle el error al
    // cliente — sin esto, el rollback automático de Spring en cualquier
    // RuntimeException deshace la revocación, dejando la "defensa ante robo"
    // como un no-op silencioso.
    @Transactional(noRollbackFor = AuthExceptions.RefreshTokenInvalidoException.class)
    public TokensRespuesta refrescar(String refreshTokenPlano) {
        String hash = hashear(refreshTokenPlano);
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(AuthExceptions.RefreshTokenInvalidoException::new);

        if (!token.estaVigente()) {
            if (token.isRevocado()) {
                // Reuso de un token ya rotado/revocado = indicio de robo:
                // se revocan TODAS las sesiones del usuario, no solo esta,
                // para forzar un login limpio en todos sus dispositivos.
                refreshTokenRepository.revocarTodosDelUsuario(token.getUsuarioId());
            }
            throw new AuthExceptions.RefreshTokenInvalidoException();
        }

        token.revocar();
        refreshTokenRepository.save(token);

        UsuarioInterno usuario = usuarioInternoRepository.findById(token.getUsuarioId())
                .orElseThrow(AuthExceptions.RefreshTokenInvalidoException::new);
        if (!usuario.isActivo()) {
            throw new AuthExceptions.CuentaInactivaException();
        }

        return emitirPar(usuario);
    }

    @Transactional
    public void logout(String refreshTokenPlano) {
        refreshTokenRepository.findByTokenHash(hashear(refreshTokenPlano))
                .ifPresent(token -> {
                    token.revocar();
                    refreshTokenRepository.save(token);
                });
    }

    private TokensRespuesta emitirPar(UsuarioInterno usuario) {
        String accessToken = jwtService.generarAccessToken(usuario);
        String refreshTokenPlano = generarRefreshTokenOpaco();

        RefreshToken entidad = new RefreshToken(
                usuario.getId(),
                hashear(refreshTokenPlano),
                LocalDateTime.now().plus(Duration.ofMillis(expiracionRefreshMs)));
        refreshTokenRepository.save(entidad);

        return new TokensRespuesta(accessToken, refreshTokenPlano, expiracionAccesoMs / 1000);
    }

    private String generarRefreshTokenOpaco() {
        byte[] bytes = new byte[32];
        GENERADOR_ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashear(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }
}
