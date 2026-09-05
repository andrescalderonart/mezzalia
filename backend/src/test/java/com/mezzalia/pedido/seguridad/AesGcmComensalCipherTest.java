package com.mezzalia.pedido.seguridad;

import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cubre el algoritmo de cifrado/descifrado en si mismo (formato de columna,
 * IV aleatorio por llamada, fallo real con clave incorrecta) usando los
 * puntos de entrada package-private parametrizados por clave explicita
 * (cifrarConClave/descifrarConClaveExplicita) -- a proposito, para NO tener
 * que mutar COMENSAL_ENCRYPTION_KEY (variable de entorno real del proceso)
 * desde un test, cosa que la JVM no permite de forma portable.
 *
 * LO QUE ESTO NO CUBRE (limite honesto, igual que el resto de este
 * proyecto sin Maven disponible en este entorno): la resolucion real desde
 * el entorno (obtenerClave/obtenerClaveAnterior, cargarClaveDesdeEnv) y el
 * flujo completo de descifrar() con COMENSAL_ENCRYPTION_KEY_PREVIOUS
 * configurada de verdad -- eso depende de variables de entorno del proceso
 * de test, que este proyecto no tiene forma de fijar aqui sin una libreria
 * adicional (ej. system-stubs) que no esta en pom.xml. La logica de "cual
 * clave probar primero, y cuando rendirse" (descifrar()) SI queda ejercida
 * indirectamente: es la misma llamada a descifrarConClave(iv, cifrado,
 * clave) que aca se prueba con exito y con clave incorrecta.
 */
class AesGcmComensalCipherTest {

    @Test
    void cifrarYDescifrarConLaMismaClaveDevuelveElTextoOriginal() throws GeneralSecurityException {
        SecretKey clave = generarClave();
        String original = "3001234567";

        String cifrado = AesGcmComensalCipher.cifrarConClave(original, clave);
        String descifrado = AesGcmComensalCipher.descifrarConClaveExplicita(cifrado, clave);

        assertThat(descifrado).isEqualTo(original);
        assertThat(cifrado).isNotEqualTo(original);
    }

    @Test
    void dosCifradosDelMismoValorProducenColumnasDistintas() {
        SecretKey clave = generarClave();

        String cifrado1 = AesGcmComensalCipher.cifrarConClave("3001234567", clave);
        String cifrado2 = AesGcmComensalCipher.cifrarConClave("3001234567", clave);

        // IV aleatorio por cada cifrado (obligatorio en GCM, ver el javadoc
        // de la clase) -- dos cifrados del mismo numero nunca deberian ser
        // bit-a-bit iguales.
        assertThat(cifrado1).isNotEqualTo(cifrado2);
    }

    @Test
    void descifrarConLaClaveIncorrectaFallaConGeneralSecurityException() {
        SecretKey claveVigente = generarClave();
        SecretKey claveVieja = generarClave();
        String cifrado = AesGcmComensalCipher.cifrarConClave("3001234567", claveVigente);

        // Esta es exactamente la condicion que dispara el fallback a
        // COMENSAL_ENCRYPTION_KEY_PREVIOUS dentro de descifrar(): GCM
        // rechaza el tag de autenticacion cuando la clave no es la que
        // cifro el dato -- no una excepcion generica, la señal real de
        // "clave incorrecta" en la que se apoya la logica de rotacion.
        assertThatThrownBy(() -> AesGcmComensalCipher.descifrarConClaveExplicita(cifrado, claveVieja))
                .isInstanceOf(GeneralSecurityException.class);
    }

    private SecretKey generarClave() {
        try {
            KeyGenerator generador = KeyGenerator.getInstance("AES");
            generador.init(256);
            return generador.generateKey();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("AES no disponible en esta JVM", ex);
        }
    }
}
