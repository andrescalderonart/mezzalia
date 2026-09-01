package com.mezzalia.pedido.seguridad;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKey;

/**
 * Cifrado AES-256-GCM para datos de comensal (hoy solo `telefono`, ver
 * Comensal.java) -- documentacion-tecnica.md §10. Clase de utilidad, NO un
 * bean de Spring: TelefonoEncriptadoConverter (el unico llamador) tampoco
 * es un bean -- JPA lo instancia por reflexion, asi que no hay forma de
 * inyectarle nada via @Value/constructor. La clave sale de la variable de
 * entorno COMENSAL_ENCRYPTION_KEY directamente via System.getenv(), igual
 * mecanismo de origen que JWT_SECRET (que si llega por @Value porque
 * JwtService si es un bean) -- incluso si Spring resuelve JWT_SECRET desde
 * el mismo proceso, la variable de entorno subyacente es identica.
 *
 * Formato de columna: base64(iv[12 bytes] || cifrado+tag). Un IV nuevo y
 * aleatorio por cada cifrado (nunca reusado) es el requisito de seguridad
 * no negociable de GCM: reusar un IV con la misma clave rompe la
 * confidencialidad Y la autenticacion del modo, no es una degradacion menor.
 *
 * ADVERTENCIA operativa real (no cubierta por este archivo): esta clase NO
 * implementa rotacion de clave. Si COMENSAL_ENCRYPTION_KEY cambia alguna
 * vez, todo telefono cifrado con la clave anterior se vuelve indescifrable
 * (fallo en tiempo de lectura, ver descifrar()) -- no hay una migracion
 * automatica de "re-cifrar con la clave nueva". Rotar esta clave en
 * produccion exige un proceso explicito (descifrar con la vieja, re-cifrar
 * con la nueva, columna por columna) que todavia no existe en este proyecto.
 */
final class AesGcmComensalCipher {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANO_IV_BYTES = 12;
    private static final int TAMANO_TAG_BITS = 128;
    private static final String VARIABLE_ENTORNO = "COMENSAL_ENCRYPTION_KEY";

    // SecureRandom (no getInstanceStrong()) a proposito: getInstanceStrong()
    // puede bloquear largo rato en contenedores/VMs con poca entropia
    // disponible al arranque -- SecureRandom por defecto ya usa una fuente
    // criptograficamente segura (via /dev/urandom o equivalente en la JVM
    // moderna) sin ese riesgo de bloqueo, y es la recomendacion estandar
    // para generar IVs.
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private static volatile SecretKey claveCacheada;

    private AesGcmComensalCipher() {}

    /**
     * Resuelve y cachea la clave la primera vez que se necesita. Lanza
     * IllegalStateException (fallo ruidoso, no silencioso) si la variable de
     * entorno falta o tiene un formato invalido -- mismo criterio que
     * JwtService con JWT_SECRET.
     *
     * Double-checked locking porque este metodo puede ser llamado
     * concurrentemente desde multiples hilos de request; el arranque
     * (ComensalEncriptacionConfig) ya fuerza la primera resolucion de forma
     * temprana y de un solo hilo, asi que en la practica esto casi siempre
     * es solo una lectura del campo volatile ya poblado.
     */
    static SecretKey obtenerClave() {
        SecretKey clave = claveCacheada;
        if (clave == null) {
            synchronized (AesGcmComensalCipher.class) {
                clave = claveCacheada;
                if (clave == null) {
                    clave = cargarClaveDesdeEnv();
                    claveCacheada = clave;
                }
            }
        }
        return clave;
    }

    private static SecretKey cargarClaveDesdeEnv() {
        String base64 = System.getenv(VARIABLE_ENTORNO);
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException(
                    "Falta la variable de entorno " + VARIABLE_ENTORNO + " (requerida para cifrar el " +
                    "telefono del comensal -- documentacion-tecnica.md §10). Generar con: " +
                    "openssl rand -base64 32");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(VARIABLE_ENTORNO + " no es Base64 valido", ex);
        }
        if (bytes.length != 32) {
            throw new IllegalStateException(
                    VARIABLE_ENTORNO + " debe decodificar a exactamente 32 bytes (AES-256); se recibieron " +
                    bytes.length + ". Generar con: openssl rand -base64 32");
        }
        return new SecretKeySpec(bytes, "AES");
    }

    static String cifrar(String textoPlano) {
        try {
            byte[] iv = new byte[TAMANO_IV_BYTES];
            ALEATORIO.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, obtenerClave(), new GCMParameterSpec(TAMANO_TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));

            byte[] resultado = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, resultado, 0, iv.length);
            System.arraycopy(cifrado, 0, resultado, iv.length, cifrado.length);
            return Base64.getEncoder().encodeToString(resultado);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Error cifrando dato de comensal", ex);
        }
    }

    static String descifrar(String valorAlmacenado) {
        try {
            byte[] datos = Base64.getDecoder().decode(valorAlmacenado);
            if (datos.length <= TAMANO_IV_BYTES) {
                throw new IllegalStateException("Valor cifrado de comensal con formato invalido (muy corto)");
            }
            byte[] iv = Arrays.copyOfRange(datos, 0, TAMANO_IV_BYTES);
            byte[] cifrado = Arrays.copyOfRange(datos, TAMANO_IV_BYTES, datos.length);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, obtenerClave(), new GCMParameterSpec(TAMANO_TAG_BITS, iv));
            byte[] descifrado = cipher.doFinal(cifrado);
            return new String(descifrado, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(
                    "Error descifrando dato de comensal (¿cambio " + VARIABLE_ENTORNO + " desde que se guardo?)", ex);
        }
    }
}
