package com.mezzalia.pedido.seguridad;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Fuerza la validacion de COMENSAL_ENCRYPTION_KEY al ARRANCAR la
 * aplicacion, no en el primer INSERT/SELECT real de un Comensal con
 * telefono (que podria ser horas o dias despues de un despliegue con la
 * variable mal puesta, y fallaria en produccion frente a un comensal real
 * en vez de en el arranque frente a quien despliega).
 *
 * TelefonoEncriptadoConverter no puede hacer esto por si solo: no es un
 * bean de Spring (ver su comentario), asi que no tiene un punto de arranque
 * propio como si lo tiene JwtService (que valida JWT_SECRET en su
 * constructor, ejecutado al construir ese bean). Este ApplicationRunner es
 * el equivalente para la clave de cifrado de comensales.
 *
 * @Order temprano (no LOWEST_PRECEDENCE) para fallar lo antes posible si
 * hay varios ApplicationRunner en el contexto -- aunque hoy es el unico.
 */
@Component
@Order(0)
public class ComensalEncriptacionConfig implements ApplicationRunner {

    @Override
    public void run(ApplicationArguments args) {
        AesGcmComensalCipher.obtenerClave();
    }
}
