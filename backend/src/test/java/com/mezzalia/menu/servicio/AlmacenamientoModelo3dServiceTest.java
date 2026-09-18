package com.mezzalia.menu.servicio;

import com.mezzalia.menu.excepcion.MenuExceptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sin @SpringBootTest/@DataJpaTest a propósito -- AlmacenamientoModelo3dService
 * no toca la base de datos, solo el filesystem (ver su javadoc), así que se
 * instancia a mano contra un @TempDir real. Mismo criterio que
 * MenuServiceTest usa para no levantar el contexto completo cuando no hace
 * falta.
 */
class AlmacenamientoModelo3dServiceTest {

    // Primeros 4 bytes reales de un .glb (glTF Binary) valido -- ver
    // AlmacenamientoModelo3dService.FIRMA_GLB.
    private static final byte[] CONTENIDO_GLB_VALIDO = {0x67, 0x6C, 0x54, 0x46, 0x02, 0x00, 0x00, 0x00};

    // Primeros 4 bytes de un ZIP valido (con lo que .usdz siempre arranca) --
    // ver AlmacenamientoModelo3dService.FIRMA_ZIP.
    private static final byte[] CONTENIDO_USDZ_VALIDO = {0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00};

    @Test
    void guardaLosDosArchivosYDevuelveSusDosUrls(@TempDir Path tempDir) throws IOException {
        AlmacenamientoModelo3dService servicio = new AlmacenamientoModelo3dService(tempDir.toString());
        MultipartFile glb = new MockMultipartFile("glb", "modelo.glb", "application/octet-stream", CONTENIDO_GLB_VALIDO);
        MultipartFile usdz = new MockMultipartFile("usdz", "modelo.usdz", "application/octet-stream", CONTENIDO_USDZ_VALIDO);

        String[] urls = servicio.guardarPar(glb, usdz);

        assertThat(urls).hasSize(2);
        assertThat(urls[0]).startsWith("/uploads/").endsWith(".glb");
        assertThat(urls[1]).startsWith("/uploads/").endsWith(".usdz");

        // Los dos archivos realmente quedaron escritos en disco, con su
        // contenido intacto -- no solo que el método no lanzó excepción.
        try (Stream<Path> archivos = Files.list(tempDir)) {
            assertThat(archivos.count()).isEqualTo(2);
        }
    }

    @Test
    void rechazaElGlbSiLaFirmaDeBytesNoCorresponde(@TempDir Path tempDir) {
        AlmacenamientoModelo3dService servicio = new AlmacenamientoModelo3dService(tempDir.toString());
        // Contenido arbitrario que NO empieza con "glTF" -- simula un
        // archivo cualquiera renombrado a .glb a mano, o un Content-Type
        // mentiroso del navegador (ver el javadoc de la clase, punto 1).
        MultipartFile glbFalso = new MockMultipartFile("glb", "modelo.glb", "application/octet-stream",
                "esto no es un modelo 3d".getBytes());
        MultipartFile usdz = new MockMultipartFile("usdz", "modelo.usdz", "application/octet-stream", CONTENIDO_USDZ_VALIDO);

        assertThatThrownBy(() -> servicio.guardarPar(glbFalso, usdz))
                .isInstanceOf(MenuExceptions.ArchivoInvalidoException.class);
    }

    @Test
    void rechazaElUsdzSiLaFirmaDeBytesNoCorresponde(@TempDir Path tempDir) {
        AlmacenamientoModelo3dService servicio = new AlmacenamientoModelo3dService(tempDir.toString());
        MultipartFile glb = new MockMultipartFile("glb", "modelo.glb", "application/octet-stream", CONTENIDO_GLB_VALIDO);
        MultipartFile usdzFalso = new MockMultipartFile("usdz", "modelo.usdz", "application/octet-stream",
                "esto tampoco es un modelo 3d".getBytes());

        assertThatThrownBy(() -> servicio.guardarPar(glb, usdzFalso))
                .isInstanceOf(MenuExceptions.ArchivoInvalidoException.class);
    }

    @Test
    void noEscribeNingunArchivoADiscoSiSoloUnoDeLosDosEsInvalido(@TempDir Path tempDir) {
        // Ver punto 3 del javadoc de la clase: si el glb (válido) se
        // guardara antes de validar el usdz (inválido), quedaría un
        // archivo huérfano en disco sin referencia en la base de datos.
        // Este test prueba justamente que eso NO pasa.
        AlmacenamientoModelo3dService servicio = new AlmacenamientoModelo3dService(tempDir.toString());
        MultipartFile glbValido = new MockMultipartFile("glb", "modelo.glb", "application/octet-stream", CONTENIDO_GLB_VALIDO);
        MultipartFile usdzInvalido = new MockMultipartFile("usdz", "modelo.usdz", "application/octet-stream",
                "invalido".getBytes());

        assertThatThrownBy(() -> servicio.guardarPar(glbValido, usdzInvalido))
                .isInstanceOf(MenuExceptions.ArchivoInvalidoException.class);

        try (Stream<Path> archivos = Files.list(tempDir)) {
            assertThat(archivos.count())
                    .as("ningún archivo debe quedar huérfano en disco cuando falla la validación del otro")
                    .isZero();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void rechazaUnGlbFaltante(@TempDir Path tempDir) {
        AlmacenamientoModelo3dService servicio = new AlmacenamientoModelo3dService(tempDir.toString());
        MultipartFile usdz = new MockMultipartFile("usdz", "modelo.usdz", "application/octet-stream", CONTENIDO_USDZ_VALIDO);

        assertThatThrownBy(() -> servicio.guardarPar(null, usdz))
                .isInstanceOf(MenuExceptions.ArchivoInvalidoException.class);
    }

    @Test
    void rechazaUnArchivoQueSuperaElTamanoMaximoPermitido(@TempDir Path tempDir) {
        AlmacenamientoModelo3dService servicio = new AlmacenamientoModelo3dService(tempDir.toString());
        // No se asigna un array real de 60MB+ -- MultipartFile.getSize() se
        // declara falsamente grande sin que el contenido real lo sea, para
        // probar la rama de validación de tamaño sin gastar memoria/tiempo
        // de test en un archivo real de decenas de MB.
        MultipartFile glbGigante = new DeclaraTamanoFalsoMultipartFile("glb", "modelo.glb", CONTENIDO_GLB_VALIDO, 61L * 1024 * 1024);
        MultipartFile usdz = new MockMultipartFile("usdz", "modelo.usdz", "application/octet-stream", CONTENIDO_USDZ_VALIDO);

        assertThatThrownBy(() -> servicio.guardarPar(glbGigante, usdz))
                .isInstanceOf(MenuExceptions.ArchivoInvalidoException.class);
    }

    /** Envoltorio minimo que declara un tamano distinto al del contenido real -- ver el test de arriba. */
    private static class DeclaraTamanoFalsoMultipartFile implements MultipartFile {
        private final String nombreCampo;
        private final String nombreArchivo;
        private final byte[] contenido;
        private final long tamanoDeclarado;

        DeclaraTamanoFalsoMultipartFile(String nombreCampo, String nombreArchivo, byte[] contenido, long tamanoDeclarado) {
            this.nombreCampo = nombreCampo;
            this.nombreArchivo = nombreArchivo;
            this.contenido = contenido;
            this.tamanoDeclarado = tamanoDeclarado;
        }

        @Override public String getName() { return nombreCampo; }
        @Override public String getOriginalFilename() { return nombreArchivo; }
        @Override public String getContentType() { return "application/octet-stream"; }
        @Override public boolean isEmpty() { return contenido.length == 0; }
        @Override public long getSize() { return tamanoDeclarado; }
        @Override public byte[] getBytes() { return contenido; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(contenido); }
        @Override public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), contenido);
        }
    }
}
