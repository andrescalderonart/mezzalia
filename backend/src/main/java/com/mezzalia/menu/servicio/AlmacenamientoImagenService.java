package com.mezzalia.menu.servicio;

import com.mezzalia.menu.excepcion.MenuExceptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * 2026-09-18: tope EXPLICITO, no solo el limite global de Spring
     * (application.yml) -- ese limite global tuvo que subirse a 65MB/130MB
     * para acomodar la subida de modelos 3D (ver
     * AlmacenamientoModelo3dService), asi que ya no alcanza por si solo para
     * seguir protegiendo esta subida de imagenes: sin este chequeo propio,
     * una foto de 60MB pasaria el limite global sin problema.
     */
    private static final long TAMANO_MAXIMO_BYTES = 5L * 1024 * 1024;

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
        if (archivo.getSize() > TAMANO_MAXIMO_BYTES) {
            throw new MenuExceptions.ArchivoInvalidoException(
                    "La imagen supera el tamaño máximo permitido (" + (TAMANO_MAXIMO_BYTES / (1024 * 1024)) + "MB)");
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
}
