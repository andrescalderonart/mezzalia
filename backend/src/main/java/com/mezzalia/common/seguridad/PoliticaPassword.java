package com.mezzalia.common.seguridad;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Política de contraseñas según OWASP ASVS 5.0, capítulo V6.2:
 * - 6.2.1: mínimo 8 caracteres (el @Size de los DTO ya lo exige; se repite
 *   acá para que la regla no dependa de que el endpoint valide).
 * - 6.2.4: rechazar contraseñas comunes o adivinables.
 * - 6.2.9: permitir al menos 64 caracteres. BCrypt ignora todo lo que pasa
 *   de 72 BYTES, así que ese es el tope real: más largo daría una falsa
 *   sensación de seguridad (dos claves distintas valdrían igual).
 * - 6.2.5 / 6.2.6: SIN reglas de composición ("una mayúscula, un número…")
 *   a propósito: ASVS las desaconseja porque empujan a claves predecibles.
 *
 * La lista de comunes es corta y local (sin descargar nada en producción);
 * cubre lo que de verdad se usa en Colombia y en restaurantes. Ampliarla con
 * una lista pública (p. ej. las 10.000 más usadas) es un pendiente anotado
 * en docs/calidad.
 */
public final class PoliticaPassword {

    public static final int MINIMO = 8;
    public static final int MAXIMO_BYTES = 72;

    private static final Set<String> COMUNES = Set.of(
            "12345678", "123456789", "1234567890", "12345678910", "87654321", "11111111", "00000000",
            "123123123", "11223344", "12341234", "qwertyui", "qwertyuiop", "asdfghjk", "asdfghjkl",
            "password", "password1", "password123", "passw0rd", "contrasena", "contraseña", "contrasena1",
            "contraseña1", "clave123", "clave1234", "iloveyou", "teamo123", "teamo1234", "colombia",
            "colombia1", "colombia123", "bogota123", "medellin", "medellin1", "millonarios", "nacional1",
            "america1", "admin123", "admin1234", "administrador", "administrator", "welcome1",
            "bienvenido", "bienvenido1", "restaurante", "restaurante1", "restaurante123", "mesero123",
            "cocina123", "mezzalia", "mezzalia1", "mezzalia123", "carta123", "abc12345", "abcd1234",
            "a1234567", "1q2w3e4r", "1qaz2wsx", "zaq12wsx", "superman", "princesa", "dragon123",
            "football", "futbol123", "sunshine", "monkey123", "letmein1", "changeme", "cambiame",
            "demo1234", "test1234", "prueba123", "usuario1", "usuario123");

    private PoliticaPassword() {
    }

    /**
     * @return null si la contraseña es aceptable, o el motivo (en español,
     *     para mostrarlo tal cual al dueño del restaurante) si no lo es.
     */
    public static String problema(String password, String username) {
        if (password == null || password.length() < MINIMO) {
            return "La contraseña debe tener al menos " + MINIMO + " caracteres";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES) {
            return "La contraseña es demasiado larga (máximo " + MAXIMO_BYTES + " bytes)";
        }
        String normal = password.toLowerCase(Locale.ROOT);
        if (COMUNES.contains(normal) || caracterRepetido(normal)) {
            return "Esa contraseña es muy común y fácil de adivinar. Usa una frase o combinación propia";
        }
        if (username != null && username.length() >= 3 && normal.contains(username.toLowerCase(Locale.ROOT))) {
            return "La contraseña no puede contener el nombre de usuario";
        }
        return null;
    }

    private static boolean caracterRepetido(String s) {
        return s.chars().distinct().count() <= 2;
    }
}
