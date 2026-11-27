package com.mezzalia.identidad.servicio;

import com.mezzalia.common.seguridad.EventosSeguridad;

import com.mezzalia.identidad.AsignacionSucursal;
import com.mezzalia.identidad.AsignacionSucursalRepository;
import com.mezzalia.identidad.RefreshToken;
import com.mezzalia.identidad.RefreshTokenRepository;
import com.mezzalia.identidad.RolUsuario;
import com.mezzalia.identidad.UsuarioInterno;
import com.mezzalia.identidad.UsuarioInternoRepository;
import com.mezzalia.identidad.dto.AccessTokenRespuesta;
import com.mezzalia.identidad.dto.TokensRespuesta;
import com.mezzalia.identidad.excepcion.AuthExceptions;
import com.mezzalia.sucursal.Sucursal;
import com.mezzalia.sucursal.SucursalRepository;
import com.mezzalia.sucursal.excepcion.SucursalExceptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
import java.util.List;

@Service
public class AuthService {

    private static final SecureRandom GENERADOR_ALEATORIO = new SecureRandom();

    private final UsuarioInternoRepository usuarioInternoRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AsignacionSucursalRepository asignacionSucursalRepository;
    private final SucursalRepository sucursalRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final long expiracionAccesoMs;
    private final long expiracionRefreshMs;

    public AuthService(
            UsuarioInternoRepository usuarioInternoRepository,
            RefreshTokenRepository refreshTokenRepository,
            AsignacionSucursalRepository asignacionSucursalRepository,
            SucursalRepository sucursalRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginAttemptService loginAttemptService,
            @Value("${app.jwt.expiracion-acceso-ms}") long expiracionAccesoMs,
            @Value("${app.jwt.expiracion-refresh-ms}") long expiracionRefreshMs) {
        this.usuarioInternoRepository = usuarioInternoRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.asignacionSucursalRepository = asignacionSucursalRepository;
        this.sucursalRepository = sucursalRepository;
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
            EventosSeguridad.loginFallido(username);
            throw new AuthExceptions.CredencialesInvalidasException();
        }
        if (!usuario.isActivo()) {
            // No cuenta como intento fallido de fuerza bruta (la contraseña
            // era correcta); pero tampoco se emite token.
            EventosSeguridad.loginCuentaInactiva(username);
            throw new AuthExceptions.CuentaInactivaException();
        }

        loginAttemptService.registrarExito(username);
        EventosSeguridad.loginExitoso(username);
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
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash).orElse(null);
        if (token == null) {
            EventosSeguridad.refreshInvalido();
            throw new AuthExceptions.RefreshTokenInvalidoException();
        }

        if (!token.estaVigente()) {
            if (token.isRevocado()) {
                // Reuso de un token ya rotado/revocado = indicio de robo:
                // se revocan TODAS las sesiones del usuario, no solo esta,
                // para forzar un login limpio en todos sus dispositivos.
                refreshTokenRepository.revocarTodosDelUsuario(token.getUsuarioId());
            }
            EventosSeguridad.refreshInvalido();
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

    /**
     * RF-10 PR4: sucursales entre las que el usuario autenticado puede
     * elegir para trabajar (ver SesionController). Para MESERO son
     * EXACTAMENTE las que un ADMIN_RESTAURANTE le asignó (ver
     * AsignacionSucursalService) -- "solo ve las que estén agregadas a su
     * nombre", tal cual la decisión de negocio. Para ADMIN_RESTAURANTE son
     * TODAS las sucursales activas de su tenant: seleccionar acá es una
     * conveniencia opcional para enfocar su propia vista de
     * Mesas/Pedidos en una sola sucursal, nunca una restricción -- si no
     * selecciona ninguna, sigue viendo todas, comportamiento idéntico al
     * que existía antes de esta rama. SUPER_ADMIN no tiene tenant ni
     * sucursales que elegir (reutiliza SinTenantEnContextoException, mismo
     * significado que en el resto del sistema: esta operación requiere un
     * usuario de restaurante).
     */
    @Transactional(readOnly = true)
    public List<Sucursal> misSucursalesDisponibles() {
        UsuarioInterno usuario = usuarioAutenticadoActual();
        if (usuario.getRol() == RolUsuario.SUPER_ADMIN) {
            throw new SucursalExceptions.SinTenantEnContextoException();
        }
        if (usuario.getRol() == RolUsuario.MESERO) {
            List<Long> idsAsignados = asignacionSucursalRepository
                    .findByUsuarioInternoIdOrderBySucursalId(usuario.getId()).stream()
                    .map(AsignacionSucursal::getSucursalId)
                    .toList();
            if (idsAsignados.isEmpty()) {
                return List.of();
            }
            return sucursalRepository.findByTenantIdAndActivaTrueAndIdInOrderByNombreAsc(
                    usuario.getTenantId(), idsAsignados);
        }
        return sucursalRepository.findByTenantIdAndActivaTrueOrderByNombreAsc(usuario.getTenantId());
    }

    /**
     * RF-10 PR4: emite un access token NUEVO con el claim sucursalId ya
     * puesto -- el mecanismo real detrás de "el mesero elige en qué
     * sucursal va a trabajar" (ver JwtService.generarAccessToken,
     * sucursal.SucursalContext, y "sucursalActual()" en
     * MesaService/PedidoService, que exigen este claim para MESERO).
     *
     * Valida explícitamente que sucursalId esté dentro de lo que
     * misSucursalesDisponibles() devolvería para este mismo usuario --
     * NUNCA confía en que el frontend solo vaya a mandar ids de esa lista.
     * "No disponible" (id inexistente, de otro tenant, inactiva, o -- para
     * MESERO -- no asignada a este usuario) se modela como una sola
     * excepción genérica, mismo criterio de "no encontrado" en vez de 403
     * que el resto del sistema usa para no confirmarle a un atacante cuál
     * de esos casos es el real.
     *
     * A propósito NO toca el refresh token (ver AccessTokenRespuesta): re-
     * mintear solo el access token es suficiente, y reusar la rotación de
     * refresh acá invalidaría sin necesidad un token que el cliente sigue
     * necesitando.
     *
     * LIMITACIÓN CONOCIDA, no resuelta en este PR: esta selección vive
     * solo en el access token (stateless). Cuando ese token expira y el
     * frontend llama a /api/auth/refresh, emitirPar() de abajo SIEMPRE
     * devuelve un access token sin sucursalId (no hay forma de reconstruir
     * cuál era a partir del refresh token, que no la registra) -- el
     * frontend debe guardar el último sucursalId elegido y volver a llamar
     * a este método inmediatamente después de cada refresh exitoso para un
     * MESERO, o GET /api/admin/mesas y /pedidos le van a devolver 409
     * (SucursalNoSeleccionadaException) hasta que lo haga. Persistir la
     * selección del lado del servidor (ej. una columna en RefreshToken)
     * queda como el siguiente paso explícito si esta fricción resulta
     * real en el uso diario -- no se implementó ahora porque acoplar
     * "sucursal elegida" a UN refresh token específico, cuando un mismo
     * usuario puede tener varios vigentes a la vez (varios dispositivos),
     * es una decisión de diseño de sesión nueva que este entorno no tiene
     * forma de verificar compilando ni corriendo la suite real.
     */
    @Transactional
    public AccessTokenRespuesta seleccionarSucursal(Long sucursalId) {
        UsuarioInterno usuario = usuarioAutenticadoActual();
        if (usuario.getRol() == RolUsuario.SUPER_ADMIN) {
            throw new SucursalExceptions.SinTenantEnContextoException();
        }

        boolean activaDelTenant = sucursalRepository.existsByIdAndTenantIdAndActivaTrue(
                sucursalId, usuario.getTenantId());
        boolean disponible = activaDelTenant && (usuario.getRol() != RolUsuario.MESERO
                || asignacionSucursalRepository.existsByUsuarioInternoIdAndSucursalId(usuario.getId(), sucursalId));
        if (!disponible) {
            throw new SucursalExceptions.SucursalNoEncontradaException(sucursalId);
        }

        String accessToken = jwtService.generarAccessToken(usuario, sucursalId);
        return new AccessTokenRespuesta(accessToken, expiracionAccesoMs / 1000);
    }

    private UsuarioInterno usuarioAutenticadoActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // No debería pasar nunca en la práctica: SesionController vive bajo
        // /api/admin/**, que SecurityConfig ya exige autenticado a nivel de
        // filtro (ver el javadoc de esa clase) -- este chequeo es la misma
        // defensa-en-profundidad que el resto del proyecto aplica en vez de
        // confiar en una sola capa.
        String username = auth != null ? auth.getName() : null;
        return usuarioInternoRepository.findByUsername(username)
                .orElseThrow(AuthExceptions.UsuarioAutenticadoNoEncontradoException::new);
    }

    private TokensRespuesta emitirPar(UsuarioInterno usuario) {
        String accessToken = jwtService.generarAccessToken(usuario);
        String refreshTokenPlano = generarRefreshTokenOpaco();

        RefreshToken entidad = new RefreshToken(
                usuario.getId(),
                hashear(refreshTokenPlano),
                LocalDateTime.now().plus(Duration.ofMillis(expiracionRefreshMs)));
        refreshTokenRepository.save(entidad);

        // RF-10 PR4: ver el javadoc largo de seleccionarSucursal() para la
        // limitación conocida de por qué esto SIEMPRE ignora cualquier
        // sucursalId que el token anterior pudiera haber tenido.
        boolean requiereSeleccionSucursal = usuario.getRol() == RolUsuario.MESERO;
        return new TokensRespuesta(accessToken, refreshTokenPlano, expiracionAccesoMs / 1000, requiereSeleccionSucursal);
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
