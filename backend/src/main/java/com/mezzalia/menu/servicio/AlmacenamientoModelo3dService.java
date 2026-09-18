package com.mezzalia.menu.servicio;

import com.mezzalia.menu.excepcion.MenuExceptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;

/**
 * Almacenamiento de archivos .glb/.usdz de modelos 3D, en DISCO LOCAL --
 * mismo mecanismo y misma limitación conocida que
 * AlmacenamientoImagenService (no funciona detrás de un load balancer con
 * más de una instancia; ver su javadoc). Clase separada en vez de
 * generalizar AlmacenamientoImagenService porque las reglas de validación
 * son distintas en tres formas que importan:
 *
 * 1. NO se confía en el Content-Type que manda el navegador. Para
 * image/jpeg|png|webp el Content-Type es confiable en la práctica -- para
 * .glb/.usdz NO: son formatos nuevos, sin un mime type universalmente
 * reconocido por los navegadores/SO, y en pruebas reales llegan como
 * "application/octet-stream" o vacío según el navegador. Se valida por
 * firma de bytes (magic number) en su lugar -- lo mismo que ya hace
 * cualquier parser de formato de archivo serio, y cierra la puerta a subir
 * un archivo arbitrario disfrazado de .glb con un Content-Type falso.
 *
 * 2. Límite de tamaño EXPLÍCITO en este servicio, no solo el límite global
 * de Spring (application.yml) -- un modelo 3D real pesa órdenes de magnitud
 * más que una foto (decenas de MB vs <1MB), así que el límite global de
 * multipart tuvo que subirse para acomodar esto. Ver
 * AlmacenamientoImagenService, que ahora valida su propio tope de 5MB de
 * forma explícita por la misma razón: sin eso, subir el límite global para
 * los modelos 3D relajaría en silencio el tope que protegía la subida de
 * imágenes.
 *
 * 3. Los dos archivos (.glb y .usdz) se validan ANTES de escribir
 * cualquiera de los dos a disco -- MenuService.actualizarModelo3d exige
 * ambas URLs juntas o ninguna (ver su javadoc); si se guardara el .glb y
 * recién después fallara la validación del .usdz, quedaría un archivo
 * huérfano en disco sin ninguna referencia en la base de datos. Por eso
 * guardarPar() es la única forma pública de guardar: valida los dos
 * completos primero, escribe los dos después.
 */
@Service
public class AlmacenamientoModelo3dService {

    /**
     * 60MB: generoso para un modelo de un solo plato con textura
     * razonablemente comprimida (Draco/KTX2, exportado por Polycam u
     * similar) -- más que eso suele ser una señal de que la exportación no
     * comprimió texturas, no una necesidad real. Ver el límite global en
     * application.yml, que tiene que acomodar los DOS archivos de una sola
     * subida (glb + usdz) a la vez.
     */
    private static final long TAMANO_MAXIMO_BYTES = 60L * 1024 * 1024;

    // "glTF" en ASCII -- primeros 4 bytes de todo archivo .glb válido
    // (glTF Binary, ver especificación Khronos §Binary Header).
    private static final byte[] FIRMA_GLB = {0x67, 0x6C, 0x54, 0x46};

    // Firma de un archivo ZIP estándar -- .usdz es, por especificación de
    // Pixar/Apple, un ZIP sin compresión (STORED) que contiene el USD real
    // adentro. No se valida el contenido del USD en sí (fuera de alcance:
    // requeriría un parser USD completo), solo que el contenedor sea
    // realmente un ZIP y no cualquier otra cosa con extensión .usdz pegada
    // a mano.
    private static final byte[] FIRMA_ZIP = {0x50, 0x4B, 0x03, 0x04};

    private final Path directorioBase;

    public AlmacenamientoModelo3dService(@Value("${app.uploads.dir}") String directorioBase) {
        this.directorioBase = Path.of(directorioBase);
        try {
            Files.createDirectories(this.directorioBase);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear el directorio de uploads: " + directorioBase, e);
        }
    }

    /**
     * @return las rutas públicas [glbUrl, usdzUrl], en ese orden, bajo las
     * cuales quedan servidos los dos archivos (ver WebConfig).
     */
    public String[] guardarPar(MultipartFile glb, MultipartFile usdz) {
        validarArchivo(glb, "glb", FIRMA_GLB, "el modelo 3D (.glb)");
        validarArchivo(usdz, "usdz", FIRMA_ZIP, "el modelo 3D para iOS (.usdz)");

        String urlGlb = escribirADisco(glb, "glb");
        String urlUsdz = escribirADisco(usdz, "usdz");
        return new String[] {urlGlb, urlUsdz};
    }

    private void validarArchivo(MultipartFile archivo, String extensionEsperada, byte[] firmaEsperada, String descripcion) {
        if (archivo == null || archivo.isEmpty()) {
            throw new MenuExceptions.ArchivoInvalidoException(
                    "Falta " + descripcion + " -- las dos URLs (glb y usdz) son obligatorias juntas");
        }
        if (archivo.getSize() > TAMANO_MAXIMO_BYTES) {
            throw new MenuExceptions.ArchivoInvalidoException(
                    descripcion + " supera el tamaño máximo permitido (" + (TAMANO_MAXIMO_BYTES / (1024 * 1024)) + "MB)");
        }
        byte[] cabecera = leerPrimerosBytes(archivo, firmaEsperada.length);
        if (!Arrays.equals(cabecera, firmaEsperada)) {
            throw new MenuExceptions.ArchivoInvalidoException(
                    "El archivo subido para " + descripcion + " no tiene el formato esperado (." + extensionEsperada + ")");
        }
    }

    private byte[] leerPrimerosBytes(MultipartFile archivo, int cantidad) {
        // Deliberadamente solo lee los primeros bytes (try-with-resources
        // cierra el stream sin consumir el archivo completo) -- el
        // MultipartFile de Spring guarda el contenido en un archivo
        // temporal propio, así que esto NO interfiere con el
        // transferTo(...) posterior en escribirADisco(): son lecturas
        // independientes del mismo respaldo en disco, no un stream
        // compartido de un solo uso.
        try (InputStream in = archivo.getInputStream()) {
            byte[] buffer = new byte[cantidad];
            int leidos = in.readNBytes(buffer, 0, cantidad);
            if (leidos < cantidad) {
                return new byte[0]; // archivo mas corto que la firma esperada -> nunca va a matchear, invalido
            }
            return buffer;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo subido", e);
        }
    }

    private String escribirADisco(MultipartFile archivo, String extension) {
        // Nombre aleatorio a proposito, mismo criterio que
        // AlmacenamientoImagenService: nunca confiar en el nombre original
        // del archivo del cliente.
        String nombreArchivo = UUID.randomUUID() + "." + extension;
        try {
            Path destino = directorioBase.resolve(nombreArchivo);
            archivo.transferTo(destino);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo subido", e);
        }
        return "/uploads/" + nombreArchivo;
    }
}
