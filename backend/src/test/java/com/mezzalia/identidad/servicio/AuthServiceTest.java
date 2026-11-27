package com.mezzalia.identidad.servicio;

import com.mezzalia.identidad.AsignacionSucursal;
import com.mezzalia.identidad.AsignacionSucursalRepository;
import com.mezzalia.identidad.RefreshTokenRepository;
import com.mezzalia.identidad.RolUsuario;
import com.mezzalia.identidad.UsuarioInterno;
import com.mezzalia.identidad.UsuarioInternoRepository;
import com.mezzalia.identidad.dto.TokensRespuesta;
import com.mezzalia.identidad.excepcion.AuthExceptions;
import com.mezzalia.sucursal.Sucursal;
import com.mezzalia.sucursal.SucursalRepository;
import com.mezzalia.sucursal.excepcion.SucursalExceptions;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Auditoria 2026-09-08 §2.1: AuthService no tenia ningun test. Mismo patron
 * que el resto de tests de servicio del proyecto: @DataJpaTest con los
 * repositorios reales (H2) y el servicio instanciado a mano.
 *
 * Lo que se cubre es lo que, si se rompe, abre la puerta: credenciales,
 * cuentas desactivadas, bloqueo por fuerza bruta, rotacion del refresh token
 * con deteccion de reuso, logout, y que un MESERO solo pueda trabajar en
 * sucursales activas que le fueron asignadas.
 */
@DataJpaTest
class AuthServiceTest {

    private static final String CLAVE = "Correcta123!";
    // BCrypt con costo minimo: los tests no prueban la fuerza del hash, y el
    // costo por defecto (10) hace lento cada login.
    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder(4);

    @Autowired private TestEntityManager em;
    @Autowired private UsuarioInternoRepository usuarioRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private AsignacionSucursalRepository asignacionRepository;
    @Autowired private SucursalRepository sucursalRepository;

    private final JwtService jwt = new JwtService("secreto-de-test-con-al-menos-32-bytes!!", 900_000);
    private AuthService auth;

    @BeforeEach
    void crearServicio() {
        auth = new AuthService(usuarioRepository, refreshTokenRepository, asignacionRepository, sucursalRepository,
                ENCODER, jwt, new LoginAttemptService(), 900_000, 604_800_000);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    // --- login ------------------------------------------------------------

    @Test
    void loginCorrectoDevuelveAccessConClaimsYUnRefreshQueNoSeGuardaEnClaro() {
        UsuarioInterno admin = usuario(1L, "admin.uno", RolUsuario.ADMIN_RESTAURANTE);

        TokensRespuesta r = auth.login("admin.uno", CLAVE);

        Claims claims = jwt.validarYObtenerClaims(r.accessToken());
        assertThat(jwt.obtenerRol(claims)).isEqualTo(RolUsuario.ADMIN_RESTAURANTE);
        assertThat(jwt.obtenerTenantId(claims)).isEqualTo(1L);
        assertThat(r.refreshToken()).isNotBlank();
        assertThat(r.requiereSeleccionSucursal()).isFalse();
        // En la BD vive solo el hash: un volcado de la tabla no sirve para robar sesiones.
        assertThat(refreshTokenRepository.findAll())
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.getUsuarioId()).isEqualTo(admin.getId());
                    assertThat(t.getTokenHash()).isNotEqualTo(r.refreshToken());
                });
    }

    @Test
    void unMeseroDebeElegirSucursalDespuesDelLogin() {
        usuario(1L, "mesero.uno", RolUsuario.MESERO);
        assertThat(auth.login("mesero.uno", CLAVE).requiereSeleccionSucursal()).isTrue();
    }

    @Test
    void usuarioInexistenteYContrasenaIncorrectaDanElMismoError() {
        usuario(1L, "admin.uno", RolUsuario.ADMIN_RESTAURANTE);
        // Mismo tipo de excepcion: no se le confirma a nadie que el usuario existe.
        assertThatThrownBy(() -> auth.login("no.existe", CLAVE))
                .isInstanceOf(AuthExceptions.CredencialesInvalidasException.class);
        assertThatThrownBy(() -> auth.login("admin.uno", "incorrecta"))
                .isInstanceOf(AuthExceptions.CredencialesInvalidasException.class);
    }

    @Test
    void cuentaDesactivadaNoEntraNiConLaClaveCorrecta() {
        UsuarioInterno u = usuario(1L, "mesero.ido", RolUsuario.MESERO);
        u.setActivo(false);
        em.persistAndFlush(u);

        assertThatThrownBy(() -> auth.login("mesero.ido", CLAVE))
                .isInstanceOf(AuthExceptions.CuentaInactivaException.class);
        // ...y con la clave incorrecta no revela que la cuenta existe y esta inactiva.
        assertThatThrownBy(() -> auth.login("mesero.ido", "incorrecta"))
                .isInstanceOf(AuthExceptions.CredencialesInvalidasException.class);
    }

    @Test
    void cincoFallosBloqueanAunqueElSextoIntentoTengaLaClaveCorrecta() {
        usuario(1L, "admin.uno", RolUsuario.ADMIN_RESTAURANTE);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> auth.login("admin.uno", "incorrecta"))
                    .isInstanceOf(AuthExceptions.CredencialesInvalidasException.class);
        }
        assertThatThrownBy(() -> auth.login("admin.uno", CLAVE))
                .isInstanceOf(AuthExceptions.DemasiadosIntentosException.class);
    }

    // --- refresh y logout -------------------------------------------------

    @Test
    void refrescarRotaElTokenElViejoDejaDeServir() {
        usuario(1L, "admin.uno", RolUsuario.ADMIN_RESTAURANTE);
        String primero = auth.login("admin.uno", CLAVE).refreshToken();

        TokensRespuesta segundo = auth.refrescar(primero);
        sincronizar();

        assertThat(segundo.refreshToken()).isNotEqualTo(primero);
        assertThatThrownBy(() -> auth.refrescar(primero))
                .isInstanceOf(AuthExceptions.RefreshTokenInvalidoException.class);
    }

    @Test
    void reusarUnRefreshYaRotadoRevocaTodasLasSesionesDelUsuario() {
        // Escenario de robo: el atacante y la victima tienen el mismo refresh.
        // Uno lo rota primero; cuando el otro lo reusa, se corta todo.
        usuario(1L, "admin.uno", RolUsuario.ADMIN_RESTAURANTE);
        String robado = auth.login("admin.uno", CLAVE).refreshToken();
        String delAtacante = auth.refrescar(robado).refreshToken();
        sincronizar();

        assertThatThrownBy(() -> auth.refrescar(robado))
                .isInstanceOf(AuthExceptions.RefreshTokenInvalidoException.class);
        sincronizar();

        assertThatThrownBy(() -> auth.refrescar(delAtacante))
                .isInstanceOf(AuthExceptions.RefreshTokenInvalidoException.class);
    }

    @Test
    void unRefreshInventadoSeRechaza() {
        assertThatThrownBy(() -> auth.refrescar("inventado-" + UUID.randomUUID()))
                .isInstanceOf(AuthExceptions.RefreshTokenInvalidoException.class);
    }

    @Test
    void siDesactivanLaCuentaElRefreshYaNoSirve() {
        UsuarioInterno u = usuario(1L, "mesero.uno", RolUsuario.MESERO);
        String refresh = auth.login("mesero.uno", CLAVE).refreshToken();
        u.setActivo(false);
        em.persistAndFlush(u);

        assertThatThrownBy(() -> auth.refrescar(refresh))
                .isInstanceOf(AuthExceptions.CuentaInactivaException.class);
    }

    @Test
    void logoutRevocaElRefreshYEsIdempotente() {
        usuario(1L, "admin.uno", RolUsuario.ADMIN_RESTAURANTE);
        String refresh = auth.login("admin.uno", CLAVE).refreshToken();

        auth.logout(refresh);
        auth.logout(refresh);
        auth.logout("no-existe");
        sincronizar();

        assertThatThrownBy(() -> auth.refrescar(refresh))
                .isInstanceOf(AuthExceptions.RefreshTokenInvalidoException.class);
    }

    // --- sucursales -------------------------------------------------------

    @Test
    void unMeseroSoloVeYPuedeElegirSucursalesActivasAsignadas() {
        UsuarioInterno mesero = usuario(1L, "mesero.uno", RolUsuario.MESERO);
        Sucursal asignada = sucursal(1L, true);
        Sucursal noAsignada = sucursal(1L, true);
        Sucursal asignadaPeroInactiva = sucursal(1L, false);
        asignar(mesero, asignada);
        asignar(mesero, asignadaPeroInactiva);
        autenticarComo("mesero.uno");

        assertThat(auth.misSucursalesDisponibles()).extracting(Sucursal::getId).containsExactly(asignada.getId());

        Claims claims = jwt.validarYObtenerClaims(auth.seleccionarSucursal(asignada.getId()).accessToken());
        assertThat(jwt.obtenerSucursalId(claims)).isEqualTo(asignada.getId());

        assertThatThrownBy(() -> auth.seleccionarSucursal(noAsignada.getId()))
                .isInstanceOf(SucursalExceptions.SucursalNoEncontradaException.class);
        assertThatThrownBy(() -> auth.seleccionarSucursal(asignadaPeroInactiva.getId()))
                .isInstanceOf(SucursalExceptions.SucursalNoEncontradaException.class);
    }

    @Test
    void elAdminPuedeElegirCualquierSucursalActivaDeSuTenantPeroNoDeOtro() {
        usuario(1L, "admin.uno", RolUsuario.ADMIN_RESTAURANTE);
        Sucursal propia = sucursal(1L, true);
        Sucursal deOtroRestaurante = sucursal(2L, true);
        autenticarComo("admin.uno");

        assertThat(auth.misSucursalesDisponibles()).extracting(Sucursal::getId).containsExactly(propia.getId());
        assertThat(auth.seleccionarSucursal(propia.getId()).accessToken()).isNotBlank();
        assertThatThrownBy(() -> auth.seleccionarSucursal(deOtroRestaurante.getId()))
                .isInstanceOf(SucursalExceptions.SucursalNoEncontradaException.class);
    }

    @Test
    void superAdminNoTieneSucursales() {
        usuario(null, "super", RolUsuario.SUPER_ADMIN);
        autenticarComo("super");
        assertThatThrownBy(() -> auth.misSucursalesDisponibles())
                .isInstanceOf(SucursalExceptions.SinTenantEnContextoException.class);
        assertThatThrownBy(() -> auth.seleccionarSucursal(1L))
                .isInstanceOf(SucursalExceptions.SinTenantEnContextoException.class);
    }

    // --- ayudantes --------------------------------------------------------

    /** Baja los cambios a la BD y vacia la cache de JPA, como entre dos requests reales. */
    private void sincronizar() {
        em.flush();
        em.clear();
    }

    private UsuarioInterno usuario(Long tenantId, String username, RolUsuario rol) {
        UsuarioInterno u = new UsuarioInterno();
        u.setTenantId(tenantId);
        u.setUsername(username);
        u.setPasswordHash(ENCODER.encode(CLAVE));
        u.setRol(rol);
        u.setActivo(true);
        return em.persistAndFlush(u);
    }

    private Sucursal sucursal(Long tenantId, boolean activa) {
        Sucursal s = new Sucursal();
        s.setTenantId(tenantId);
        s.setNombre("Sede " + UUID.randomUUID());
        s.setActiva(activa);
        return em.persistAndFlush(s);
    }

    private void asignar(UsuarioInterno usuario, Sucursal sucursal) {
        AsignacionSucursal a = new AsignacionSucursal();
        a.setTenantId(usuario.getTenantId());
        a.setUsuarioInternoId(usuario.getId());
        a.setSucursalId(sucursal.getId());
        em.persistAndFlush(a);
    }

    private void autenticarComo(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }
}
