package com.mezzalia.identidad.servicio;

import com.mezzalia.identidad.RolUsuario;
import com.mezzalia.identidad.UsuarioInterno;
import com.mezzalia.identidad.excepcion.AuthExceptions;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Auditoria 2026-09-08 §2.1: JwtService no tenia ningun test. Es la pieza
 * que decide tenant, rol y sucursal de cada request (JwtAuthenticationFilter),
 * asi que lo importante es: los claims viajan bien, y un token alterado,
 * vencido, firmado con otra clave o sin firma se rechaza SIEMPRE con
 * TokenInvalidoException (el filtro solo atrapa esa, ver su catch).
 */
class JwtServiceTest {

    private static final String SECRETO = "secreto-de-test-con-al-menos-32-bytes!!";
    private final JwtService jwt = new JwtService(SECRETO, 900_000);

    private static UsuarioInterno usuario(Long tenantId, RolUsuario rol) {
        UsuarioInterno u = new UsuarioInterno();
        u.setUsername("ana");
        u.setTenantId(tenantId);
        u.setRol(rol);
        return u;
    }

    @Test
    void losClaimsDeUnMeseroConSucursalViajanCompletos() {
        Claims claims = jwt.validarYObtenerClaims(jwt.generarAccessToken(usuario(7L, RolUsuario.MESERO), 3L));
        assertThat(jwt.obtenerUsername(claims)).isEqualTo("ana");
        assertThat(jwt.obtenerRol(claims)).isEqualTo(RolUsuario.MESERO);
        assertThat(jwt.obtenerTenantId(claims)).isEqualTo(7L);
        assertThat(jwt.obtenerSucursalId(claims)).isEqualTo(3L);
    }

    @Test
    void sinSucursalElegidaElClaimNoExiste() {
        Claims claims = jwt.validarYObtenerClaims(jwt.generarAccessToken(usuario(7L, RolUsuario.ADMIN_RESTAURANTE)));
        assertThat(jwt.obtenerSucursalId(claims)).isNull();
        assertThat(claims).doesNotContainKey("sucursalId");
    }

    @Test
    void superAdminNoLlevaTenant() {
        Claims claims = jwt.validarYObtenerClaims(jwt.generarAccessToken(usuario(null, RolUsuario.SUPER_ADMIN)));
        assertThat(jwt.obtenerTenantId(claims)).isNull();
        assertThat(claims).doesNotContainKey("tenantId");
    }

    @Test
    void unTokenVencidoSeRechaza() {
        JwtService yaVencido = new JwtService(SECRETO, -1_000);
        String token = yaVencido.generarAccessToken(usuario(7L, RolUsuario.MESERO));
        assertThatThrownBy(() -> jwt.validarYObtenerClaims(token))
                .isInstanceOf(AuthExceptions.TokenInvalidoException.class);
    }

    @Test
    void cambiarElPayloadSinReFirmarSeRechaza() {
        // Un mesero que se edita el token para decir ADMIN_RESTAURANTE o
        // cambiarse de tenant: la firma ya no coincide.
        String token = jwt.generarAccessToken(usuario(7L, RolUsuario.MESERO));
        String[] partes = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8)
                .replace("MESERO", "ADMIN_RESTAURANTE").replace("\"tenantId\":7", "\"tenantId\":8");
        String alterado = partes[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + partes[2];

        assertThatThrownBy(() -> jwt.validarYObtenerClaims(alterado))
                .isInstanceOf(AuthExceptions.TokenInvalidoException.class);
    }

    @Test
    void unTokenFirmadoConOtraClaveSeRechaza() {
        String ajeno = Jwts.builder().subject("ana").claim("rol", "ADMIN_RESTAURANTE").claim("tenantId", 7)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("otra-clave-distinta-de-al-menos-32-bytes".getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThatThrownBy(() -> jwt.validarYObtenerClaims(ajeno))
                .isInstanceOf(AuthExceptions.TokenInvalidoException.class);
    }

    @Test
    void unTokenSinFirmaAlgNoneSeRechaza() {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"ana\",\"rol\":\"SUPER_ADMIN\"}".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> jwt.validarYObtenerClaims(header + "." + payload + "."))
                .isInstanceOf(AuthExceptions.TokenInvalidoException.class);
    }

    @Test
    void basuraYVacioSeRechazanConLaMismaExcepcion() {
        for (String token : new String[] {"", "no-es-un-jwt", "a.b.c"}) {
            assertThatThrownBy(() -> jwt.validarYObtenerClaims(token))
                    .isInstanceOf(AuthExceptions.TokenInvalidoException.class);
        }
    }
}
