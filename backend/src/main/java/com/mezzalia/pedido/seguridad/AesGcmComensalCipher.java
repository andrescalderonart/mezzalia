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
 * bean de Spring: TelefonoEncriptadoConverter (el unico llamador desde
 * cifrar()/descifrar()) tampoco es un bean -- JPA lo instancia por
 * reflexion, asi que no hay forma de inyectarle nada via @Value/constructor.
 * La clave sale de la variable de entorno COMENSAL_ENCRYPTION_KEY
 * directamente via System.getenv(), igual mecanismo de origen que
 * JWT_SECRET (que si llega por @Value porque JwtService si es un bean) --
 * incluso si Spring resuelve JWT_SECRET desde el mismo proceso, la variable
 * de entorno subyacente es identica.
 *
 * Formato de columna: base64(iv[12 bytes] || cifrado+tag). Un IV nuevo y
 * aleatorio por cada cifrado (nunca reusado) es el requisito de seguridad
 * no negociable de GCM: reusar un IV con la misma clave rompe la
 * confidencialidad Y la autenticacion del modo, no es una degradacion menor.
 *
 * ROTACION DE CLAVE (antes: "ADVERTENCIA operativa real: no implementada" --
 * ya lo esta, este es el mecanismo):
 *
 * 1. Ademas de COMENSAL_ENCRYPTION_KEY (la clave VIGENTE, con la que se
 *    cifra todo dato NUEVO), existe una variable opcional
 *    COMENSAL_ENCRYPTION_KEY_PREVIOUS -- la clave VIEJA, usada solo para
 *    poder seguir DESCIFRANDO filas que todavia no se re-cifraron.
 * 2. Para rotar: (a) generar una clave nueva (openssl rand -base64 32),
 *    (b) mover el valor actual de COMENSAL_ENCRYPTION_KEY a
 *    COMENSAL_ENCRYPTION_KEY_PREVIOUS, (c) poner la clave nueva en
 *    COMENSAL_ENCRYPTION_KEY, (d) reiniciar la aplicacion, (e) llamar UNA
 *    vez a POST /api/superadmin/comensales/reencriptar (SUPER_ADMIN, ver
 *    pedido.controlador.ComensalMantenimientoController /
 *    pedido.servicio.ComensalService.reencriptarTodosLosComensales) -- lee
 *    cada Comensal (descifrando con la clave vigente o, si falla, con la
 *    anterior) y lo vuelve a guardar, lo que lo re-cifra SIEMPRE con la
 *    clave vigente. (f) Una vez confirmado que terminó sin error, quitar
 *    COMENSAL_ENCRYPTION_KEY_PREVIOUS del entorno y reiniciar de nuevo.
 * 3. Mientras COMENSAL_ENCRYPTION_KEY_PREVIOUS siga configurada, descifrar()
 *    intenta primero con la clave vigente y, si esa falla (GCM detecta el
 *    tag de autenticacion invalido -- la señal real de "esta fila fue
 *    cifrada con otra clave", ver descifrar() abajo), reintenta con la
 *    anterior antes de rendirse. Si ninguna de las dos abre la fila, es un
 *    error real (ninguna clave configurada corresponde a este dato) y se
 *    lanza sin mas reintentos.
 *
 * Esto es un mecanismo MANUAL con dos claves activas a la vez, no rotacion
 * automatica ni versionado de clave por fila -- suficiente para el volumen y
 * el equipo de este proyecto (§1.2), pero exige la disciplina operativa de
 * los pasos de arriba: si se salta el paso (e) y luego se quita la clave
 * anterior del entorno, cualquier fila que no se haya re-cifrado a tiempo
 * queda indescifrable para siempre -- lo mismo que pasaba antes de esto,
 * ahora con una ventana para evitarlo en vez de ninguna.
 */
final class AesGcmComensalCipher {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANO_IV_BYTES = 12;
    private static final int TAMANO_TAG_BITS = 128;
    private static final String VARIABLE_ENTORNO = "COMENSAL_ENCRYPTION_KEY";
    private static final String VARIABLE_ENTORNO_ANTERIOR = "COMENSAL_ENCRYPTION_KEY_PREVIOUS";

    // SecureRandom (no getInstanceStrong()) a proposito: getInstanceStrong()
    // puede bloquear largo rato en contenedores/VMs con poca entropia
    // disponible al arranque -- SecureRandom por defecto ya usa una fuente
    // criptograficamente segura (via /dev/urandom o equivalente en la JVM
    // moderna) sin ese riesgo de bloqueo, y es la recomendacion estandar
    // para generar IVs.
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private static volatile SecretKey claveCacheada;
    private static volatile SecretKey claveAnteriorCacheada;
    private static volatile boolean claveAnteriorResuelta = false;

    private AesGcmComensalCipher() {}

    /**
     * Resuelve y cachea la clave VIGENTE la primera vez que se necesita.
     * Lanza IllegalStateException (fallo ruidoso, no silencioso) si la
     * variable de entorno falta o tiene un formato invalido -- mismo
     * criterio que JwtService con JWT_SECRET.
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
                    clave = cargarClaveDesdeEnv(VARIABLE_ENTORNO, true);
                    claveCacheada = clave;
                }
            }
        }
        return clave;
    }

    /**
     * Resuelve y cachea la clave ANTERIOR (rotacion, ver el javadoc de la
     * clase) -- a diferencia de obtenerClave(), es opcional: null es un
     * resultado valido y cacheable (significa "no hay rotacion en curso"),
     * por eso el double-checked locking usa un booleano de "ya resuelta"
     * en vez de comparar contra null.
     */
    static SecretKey obtenerClaveAnterior() {
        if (!claveAnteriorResuelta) {
            synchronized (AesGcmComensalCipher.class) {
                if (!claveAnteriorResuelta) {
                    claveAnteriorCacheada = cargarClaveDesdeEnv(VARIABLE_ENTORNO_ANTERIOR, false);
                    claveAnteriorResuelta = true;
                }
            }
        }
        return claveAnteriorCacheada;
    }

    private static SecretKey cargarClaveDesdeEnv(String variable, boolean obligatoria) {
        String base64 = System.getenv(variable);
        if (base64 == null || base64.isBlank()) {
            if (!obligatoria) {
                return null;
            }
            throw new IllegalStateException(
                    "Falta la variable de entorno " + variable + " (requerida para cifrar el " +
                    "telefono del comensal -- documentacion-tecnica.md §10). Generar con: " +
                    "openssl rand -base64 32");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(variable + " no es Base64 valido", ex);
        }
        if (bytes.length != 32) {
            throw new IllegalStateException(
                    variable + " debe decodificar a exactamente 32 bytes (AES-256); se recibieron " +
                    bytes.length + ". Generar con: openssl rand -base64 32");
        }
        return new SecretKeySpec(bytes, "AES");
    }

    static String cifrar(String textoPlano) {
        return cifrarConClave(textoPlano, obtenerClave());
    }

    /**
     * Descifra probando primero la clave VIGENTE y, si GCM rechaza el tag
     * de autenticacion (la señal criptografica real de "clave incorrecta",
     * no una suposicion), reintenta con la clave ANTERIOR si esta
     * configurada -- ver el javadoc de rotacion de la clase. Un dato
     * corrupto/truncado (formato invalido, Base64 invalido) NO dispara este
     * reintento -- ninguna clave lo va a arreglar, se lanza directo.
     */
    static String descifrar(String valorAlmacenado) {
        byte[] datos = decodificarBase64(valorAlmacenado);
        if (datos.length <= TAMANO_IV_BYTES) {
            throw new IllegalStateException("Valor cifrado de comensal con formato invalido (muy corto)");
        }
        byte[] iv = Arrays.copyOfRange(datos, 0, TAMANO_IV_BYTES);
        byte[] cifrado = Arrays.copyOfRange(datos, TAMANO_IV_BYTES, datos.length);

        try {
            return descifrarConClave(iv, cifrado, obtenerClave());
        } catch (GeneralSecurityException exConClaveVigente) {
            SecretKey anterior = obtenerClaveAnterior();
            if (anterior == null) {
                throw new IllegalStateException(
                        "Error descifrando dato de comensal (¿cambio " + VARIABLE_ENTORNO + " desde que se " +
                        "guardo? Configure " + VARIABLE_ENTORNO_ANTERIOR + " con la clave vieja durante una " +
                        "rotacion -- ver el javadoc de esta clase)", exConClaveVigente);
            }
            try {
                return descifrarConClave(iv, cifrado, anterior);
            } catch (GeneralSecurityException exConClaveAnterior) {
                throw new IllegalStateException(
                        "Error descifrando dato de comensal: no coincide ni con " + VARIABLE_ENTORNO +
                        " ni con " + VARIABLE_ENTORNO_ANTERIOR, exConClaveAnterior);
            }
        }
    }

    /**
     * Punto de entrada package-private parametrizado por clave explicita --
     * unico motivo de existir es hacer testeable el algoritmo de
     * cifrado/descifrado en si (formato de columna, IV aleatorio, fallo con
     * clave incorrecta) SIN tener que mutar la variable de entorno real del
     * proceso de test (ver AesGcmComensalCipherTest). cifrar()/descifrar()
     * -- los que usa TelefonoEncriptadoConverter -- son el unico camino real
     * de la aplicacion, siempre resolviendo la clave desde el entorno.
     */
    static String cifrarConClave(String textoPlano, SecretKey clave) {
        try {
            byte[] iv = new byte[TAMANO_IV_BYTES];
            ALEATORIO.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(TAMANO_TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));

            byte[] resultado = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, resultado, 0, iv.length);
            System.arraycopy(cifrado, 0, resultado, iv.length, cifrado.length);
            return Base64.getEncoder().encodeToString(resultado);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Error cifrando dato de comensal", ex);
        }
    }

    /** Mismo motivo que cifrarConClave(): testeable sin depender del entorno real. */
    static String descifrarConClaveExplicita(String valorAlmacenado, SecretKey clave) throws GeneralSecurityException {
        byte[] datos = decodificarBase64(valorAlmacenado);
        if (datos.length <= TAMANO_IV_BYTES) {
            throw new IllegalStateException("Valor cifrado de comensal con formato invalido (muy corto)");
        }
        byte[] iv = Arrays.copyOfRange(datos, 0, TAMANO_IV_BYTES);
        byte[] cifrado = Arrays.copyOfRange(datos, TAMANO_IV_BYTES, datos.length);
        return descifrarConClave(iv, cifrado, clave);
    }

    private static String descifrarConClave(byte[] iv, byte[] cifrado, SecretKey clave) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(ALGORITMO);
        cipher.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(TAMANO_TAG_BITS, iv));
        byte[] descifrado = cipher.doFinal(cifrado);
        return new String(descifrado, StandardCharsets.UTF_8);
    }

    private static byte[] decodificarBase64(String valor) {
        try {
            return Base64.getDecoder().decode(valor);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Valor cifrado de comensal no es Base64 valido", ex);
        }
    }
}
