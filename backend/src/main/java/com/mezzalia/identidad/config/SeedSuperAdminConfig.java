package com.mezzalia.identidad.config;

import com.mezzalia.identidad.RolUsuario;
import com.mezzalia.identidad.UsuarioInterno;
import com.mezzalia.identidad.UsuarioInternoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Siembra un único usuario SUPER_ADMIN al arrancar en perfil `dev`, para
 * poder hacer login la primera vez sin depender de un CRUD de usuarios que
 * todavía no existe (fuera de alcance de este PR — ver README/tareas).
 *
 * A propósito NO es una migración de Flyway: una migración versionada corre
 * en TODOS los ambientes, incluida producción el día que exista — sembrar un
 * usuario con contraseña conocida ahí sería un backdoor permanente en el
 * historial de esquema. Esto solo corre si `spring.profiles.active=dev`.
 */
@Configuration
@Profile("dev")
public class SeedSuperAdminConfig {

    private static final Logger log = LoggerFactory.getLogger(SeedSuperAdminConfig.class);

    @Bean
    public CommandLineRunner sembrarSuperAdminDeDesarrollo(
            UsuarioInternoRepository usuarioInternoRepository,
            PasswordEncoder passwordEncoder,
            @Value("${DEV_SUPERADMIN_USERNAME:superadmin}") String username,
            @Value("${DEV_SUPERADMIN_PASSWORD:}") String password) {

        return args -> {
            if (usuarioInternoRepository.existsByUsername(username)) {
                return;
            }
            if (password == null || password.isBlank()) {
                log.warn("Perfil 'dev' activo pero DEV_SUPERADMIN_PASSWORD no está seteado en el entorno: "
                        + "no se sembró ningún SUPER_ADMIN. No va a ser posible hacer login hasta setear "
                        + "esa variable (ver .env.example) y reiniciar.");
                return;
            }

            UsuarioInterno superAdmin = new UsuarioInterno();
            superAdmin.setUsername(username);
            superAdmin.setPasswordHash(passwordEncoder.encode(password));
            superAdmin.setRol(RolUsuario.SUPER_ADMIN);
            superAdmin.setTenantId(null);
            superAdmin.setActivo(true);
            usuarioInternoRepository.save(superAdmin);
            log.info("SUPER_ADMIN de desarrollo sembrado (username={})", username);
        };
    }
}
