package com.mezzalia.identidad.config;

import com.mezzalia.identidad.RolUsuario;
import com.mezzalia.identidad.UsuarioInterno;
import com.mezzalia.identidad.UsuarioInternoRepository;
import com.mezzalia.tenant.Tenant;
import com.mezzalia.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Datos de ejemplo SOLO para perfil `dev`, pensados para poder probar a mano
 * (sin tocar la base con SQL manual) las correcciones de esta rama:
 * - Un tenant ACTIVO y uno ya SUSPENDIDO desde el arranque, para probar RF-11
 *   (TenantSuspendidoException) sin tener que suspender nada manualmente.
 * - Un admin.demo (ADMIN_RESTAURANTE) y un mesero.demo (MESERO), ambos
 *   colgados del tenant activo, para probar login con roles distintos a
 *   SUPER_ADMIN y confirmar que el JWT trae el tenantId correcto.
 *
 * Igual que SeedSuperAdminConfig: a propósito NO es una migración de Flyway
 * (no debe correr en producción), y es idempotente (se puede reiniciar la
 * app las veces que haga falta sin duplicar nada).
 */
@Configuration
@Profile("dev")
public class SeedDatosDemoConfig {

    private static final Logger log = LoggerFactory.getLogger(SeedDatosDemoConfig.class);
    private static final String PASSWORD_DEMO = "Demo1234!";

    @Bean
    public CommandLineRunner sembrarTenantsYUsuariosDeDemo(
            TenantRepository tenantRepository,
            UsuarioInternoRepository usuarioInternoRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {
            Tenant activo = sembrarTenantSiNoExiste(tenantRepository, "Restaurante Demo Activo", Tenant.ESTADO_ACTIVO);
            sembrarTenantSiNoExiste(tenantRepository, "Restaurante Demo Suspendido", Tenant.ESTADO_SUSPENDIDO);

            sembrarUsuarioSiNoExiste(usuarioInternoRepository, passwordEncoder,
                    "admin.demo", RolUsuario.ADMIN_RESTAURANTE, activo.getId());
            sembrarUsuarioSiNoExiste(usuarioInternoRepository, passwordEncoder,
                    "mesero.demo", RolUsuario.MESERO, activo.getId());

            log.info("Datos de demo listos. Password de admin.demo y mesero.demo: {}", PASSWORD_DEMO);
        };
    }

    private Tenant sembrarTenantSiNoExiste(TenantRepository repo, String nombre, String estado) {
        return repo.findByNombre(nombre).orElseGet(() -> {
            Tenant tenant = new Tenant();
            tenant.setNombre(nombre);
            tenant.setPlan("STARTER");
            tenant.setMoneda("COP");
            tenant.setZonaHoraria("America/Bogota");
            tenant.setEstado(estado);
            Tenant guardado = repo.save(tenant);
            log.info("Tenant de demo sembrado: id={} nombre='{}' estado={}", guardado.getId(), nombre, estado);
            return guardado;
        });
    }

    private void sembrarUsuarioSiNoExiste(UsuarioInternoRepository repo, PasswordEncoder encoder,
            String username, RolUsuario rol, Long tenantId) {
        if (repo.existsByUsername(username)) {
            return;
        }
        UsuarioInterno usuario = new UsuarioInterno();
        usuario.setUsername(username);
        usuario.setPasswordHash(encoder.encode(PASSWORD_DEMO));
        usuario.setRol(rol);
        usuario.setTenantId(tenantId);
        usuario.setActivo(true);
        repo.save(usuario);
        log.info("Usuario de demo sembrado: username={} rol={} tenantId={}", username, rol, tenantId);
    }
}
