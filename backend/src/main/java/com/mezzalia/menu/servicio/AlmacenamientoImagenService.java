package com.mezzalia.menu.servicio;

import com.mezzalia.menu.excepcion.MenuExceptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.mezzalia.common.seguridad.EventosSeguridad;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

/**
 * Almacenamiento de imágenes de platos en DISCO LOCAL, a propósito y con una
 * limitación conocida y documentada (no silenciosa): solo funciona mientras
 * la aplicación corre en UNA sola instancia. documentacion-tecnica.md §1.2
 * pide explícitamente no sobre-ingenieriar para "3 clientes piloto" -- no se
 * justifica hoy montar S3/Cloudinary. El día que haya más de una instancia
 * detrás de un load balancer, cada instancia tendría su propio disco y una
 * imagen subida a través de una instancia no se vería desde otra -- en ese
 * momento esto DEBE migrar a almacenamiento de objetos compartido ANTES de
 * escalar horizontalmente, no después de que un restaurante reporte "mi foto
 * no aparece".
 */
@Service
public class AlmacenamientoImagenService {

    private static final Set<String> TIPOS_PERMITIDOS = Set.of("image/jpeg", "image/png", "image/webp");

    private final Path directorioBase;

    public AlmacenamientoImagenService(@Value("${app.uploads.dir}") String directorioBase) {
        this.directorioBase = Path.of(directorioBase);
        try {
            Files.createDirectories(this.directorioBase);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear el directorio de uploads: " + directorioBase, e);
        }
    }

    /** @return la ruta pública bajo la cual queda servida la imagen (ver WebConfig). */
    public String guardar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new MenuExceptions.ArchivoInvalidoException("El archivo está vacío");
        }
        String tipo = archivo.getContentType();
        if (tipo == null || !TIPOS_PERMITIDOS.contains(tipo)) {
            throw new MenuExceptions.ArchivoInvalidoException(
                    "Tipo de archivo no permitido (" + tipo + "). Solo se aceptan JPEG, PNG o WEBP");
        }

        // OWASP ASVS 5.0 V5.2.2: el Content-Type lo declara el cliente y se
        // puede falsificar. Se verifica la firma real del archivo (sus
        // primeros bytes): un HTML o un ejecutable renombrado a .png no pasa.
        if (!firmaCoincide(archivo, tipo)) {
            EventosSeguridad.archivoRechazado("firma no coincide con " + tipo);
            throw new MenuExceptions.ArchivoInvalidoException(
                    "El archivo no es una imagen " + tipo.substring("image/".length()).toUpperCase() + " válida");
        }

        String extension = switch (tipo) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
        // Nombre aleatorio a propósito: nunca confiar en el nombre original
        // del archivo del cliente (path traversal, colisiones, caracteres
        // raros en el filesystem del servidor).
        String nombreArchivo = UUID.randomUUID() + extension;

        try {
            Path destino = directorioBase.resolve(nombreArchivo);
            archivo.transferTo(destino);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo subido", e);
        }

        return "/uploads/" + nombreArchivo;
    }

    /** Firmas ("magic numbers") de JPEG, PNG y WEBP. */
    static boolean firmaCoincide(MultipartFile archivo, String tipo) {
        byte[] cabecera = new byte[12];
        int leidos;
        try (var entrada = archivo.getInputStream()) {
            leidos = entrada.readNBytes(cabecera, 0, cabecera.length);
        } catch (IOException e) {
            return false;
        }
        return switch (tipo) {
            case "image/jpeg" -> leidos >= 3 && (cabecera[0] & 0xFF) == 0xFF && (cabecera[1] & 0xFF) == 0xD8
                    && (cabecera[2] & 0xFF) == 0xFF;
            case "image/png" -> leidos >= 8 && (cabecera[0] & 0xFF) == 0x89 && cabecera[1] == 'P' && cabecera[2] == 'N'
                    && cabecera[3] == 'G' && cabecera[4] == 0x0D && cabecera[5] == 0x0A && cabecera[6] == 0x1A
                    && cabecera[7] == 0x0A;
            case "image/webp" -> leidos >= 12 && cabecera[0] == 'R' && cabecera[1] == 'I' && cabecera[2] == 'F'
                    && cabecera[3] == 'F' && cabecera[8] == 'W' && cabecera[9] == 'E' && cabecera[10] == 'B'
                    && cabecera[11] == 'P';
            default -> false;
        };
    }
}
