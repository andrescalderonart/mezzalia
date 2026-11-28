package com.mezzalia.identidad.config;

import com.mezzalia.common.seguridad.EventosSeguridad;
import com.mezzalia.common.seguridad.PoliticaPassword;
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
 * Crea el PRIMER usuario SUPER_ADMIN de un servidor nuevo (2026-11-27,
 * preparacion de pilotos). Antes solo existia SeedSuperAdminConfig, que corre
 * en perfil `dev`: en produccion no habia forma de entrar la primera vez.
 *
 * Reglas para que no sea una puerta trasera:
 * - Solo actua si MEZZALIA_PRIMER_SUPERADMIN_USUARIO y _PASSWORD estan
 *   definidas Y todavia no existe NINGUN SUPER_ADMIN. Con uno creado, no hace
 *   nada aunque las variables sigan puestas (no pisa ni resetea a nadie).
 * - La contraseña pasa la misma politica que cualquier otra (ASVS V6.2).
 * - Queda en el log de SEGURIDAD. La guia de despliegue pide borrar las dos
 *   variables del .env apenas se entra por primera vez.
 * - No corre en `dev` (ahi ya siembra SeedSuperAdminConfig).
 */
@Configuration
@Profile("!dev")
public class PrimerSuperAdminConfig {

    private static final Logger log = LoggerFactory.getLogger(PrimerSuperAdminConfig.class);

    @Bean
    public CommandLineRunner crearPrimerSuperAdmin(
            UsuarioInternoRepository usuarios,
            PasswordEncoder passwordEncoder,
            @Value("${MEZZALIA_PRIMER_SUPERADMIN_USUARIO:}") String username,
            @Value("${MEZZALIA_PRIMER_SUPERADMIN_PASSWORD:}") String password) {
        return args -> crear(usuarios, passwordEncoder, username, password);
    }

    static void crear(UsuarioInternoRepository usuarios, PasswordEncoder passwordEncoder, String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return;
        }
        if (usuarios.existsByRol(RolUsuario.SUPER_ADMIN)) {
            log.info("Ya existe un SUPER_ADMIN: se ignoran MEZZALIA_PRIMER_SUPERADMIN_*. Borra esas variables del entorno.");
            return;
        }
        String problema = PoliticaPassword.problema(password, username);
        if (problema != null) {
            throw new IllegalStateException("MEZZALIA_PRIMER_SUPERADMIN_PASSWORD no es segura: " + problema);
        }
        UsuarioInterno superAdmin = new UsuarioInterno();
        superAdmin.setUsername(username.trim());
        superAdmin.setPasswordHash(passwordEncoder.encode(password));
        superAdmin.setRol(RolUsuario.SUPER_ADMIN);
        superAdmin.setTenantId(null);
        superAdmin.setActivo(true);
        usuarios.save(superAdmin);
        EventosSeguridad.primerSuperAdminCreado(username.trim());
    }
}
