package com.mezzalia.migraciones;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las migraciones de Flyway contra un MySQL REAL (2026-11-27, preparacion de
 * pilotos). El resto de los tests usa H2 con el esquema generado desde las
 * entidades, asi que hasta ahora ninguna migracion se habia ejecutado en
 * ningun lado. Este test arranca la app completa con Flyway encendido y
 * Hibernate en `validate`: si una migracion falla, o el esquema que dejan no
 * coincide con las entidades, el contexto no arranca y el test falla.
 *
 * Corre solo si existe MEZZALIA_TEST_MYSQL_URL (el job "migraciones-mysql" del
 * CI levanta un MySQL 8.4 de servicio). En local sin MySQL se omite.
 */
@EnabledIfEnvironmentVariable(named = "MEZZALIA_TEST_MYSQL_URL", matches = ".+")
@SpringBootTest
class MigracionesMySqlTest {

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", () -> System.getenv("MEZZALIA_TEST_MYSQL_URL"));
        registro.add("spring.datasource.username", () -> System.getenv("MEZZALIA_TEST_MYSQL_USER"));
        registro.add("spring.datasource.password", () -> System.getenv("MEZZALIA_TEST_MYSQL_PASSWORD"));
        registro.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registro.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
        registro.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registro.add("spring.flyway.enabled", () -> "true");
        registro.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    Flyway flyway;

    @Test
    void todasLasMigracionesCorrenYElEsquemaCoincideConLasEntidades() throws IOException {
        long archivos;
        try (Stream<Path> migraciones = Files.list(Path.of("src/main/resources/db/migration"))) {
            archivos = migraciones.filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql")).count();
        }
        assertThat(flyway.info().applied()).hasSize((int) archivos);
        assertThat(flyway.info().pending()).isEmpty();
    }
}
