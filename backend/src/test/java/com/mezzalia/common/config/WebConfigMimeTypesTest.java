package com.mezzalia.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @SpringBootTest con WebEnvironment.RANDOM_PORT a proposito -- primer test
 * del repo que levanta un contenedor servlet embebido REAL en vez de MockMvc.
 * Hace falta especificamente porque lo que se prueba (que Tomcat sirve
 * /uploads/**.usdz con el Content-Type correcto) depende de la resolucion de
 * tipos de contenido de la negociacion de contenido real de Spring MVC
 * (spring.mvc.contentnegotiation.media-types, ver application.yml) -- un
 * MockMvc corre DispatcherServlet directo sobre un MockServletContext y NO
 * pasa por el mismo mecanismo, asi que hubiera podido dar un falso verde (o
 * un falso rojo) sin decir nada real sobre como se comporta la app cuando de
 * verdad corre.
 *
 * Motivo del fix (2026-09-18): Apple exige exactamente
 * "model/vnd.usdz+zip" para que un enlace <a rel="ar">/<model-viewer
 * ios-src> abra AR Quick Look en Safari/iOS -- sin este Content-Type
 * explicito, Tomcat sirve la extension .usdz (que no conoce) como
 * application/octet-stream, y en vez de abrir la vista AR, Safari
 * simplemente descarga el archivo. Ver WebConfig (sirve /uploads/**) y el
 * comentario en application.yml.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebConfigMimeTypesTest {

    @LocalServerPort
    private int puerto;

    @Value("${app.uploads.dir}")
    private String directorioUploads;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    void sirveUnUsdzConElContentTypeQueExigeArQuickLookEnIos() throws IOException {
        escribirArchivoDePrueba("prueba-mime.usdz", new byte[]{0x50, 0x4B, 0x03, 0x04});

        ResponseEntity<byte[]> respuesta = restTemplate.getForEntity(
                "http://localhost:" + puerto + "/uploads/prueba-mime.usdz", byte[].class);

        assertThat(respuesta.getHeaders().getContentType())
                .as("Content-Type real que Tomcat manda para .usdz -- si esto es " +
                        "application/octet-stream en vez de model/vnd.usdz+zip, AR Quick " +
                        "Look en Safari/iOS abre una descarga en vez del visor AR")
                .isEqualTo(MediaType.parseMediaType("model/vnd.usdz+zip"));
    }

    @Test
    void sirveUnGlbConSuContentTypeEstandarDeKhronos() throws IOException {
        escribirArchivoDePrueba("prueba-mime.glb", new byte[]{0x67, 0x6C, 0x54, 0x46});

        ResponseEntity<byte[]> respuesta = restTemplate.getForEntity(
                "http://localhost:" + puerto + "/uploads/prueba-mime.glb", byte[].class);

        assertThat(respuesta.getHeaders().getContentType())
                .isEqualTo(MediaType.parseMediaType("model/gltf-binary"));
    }

    private void escribirArchivoDePrueba(String nombre, byte[] contenido) throws IOException {
        Path directorio = Path.of(directorioUploads);
        Files.createDirectories(directorio);
        Files.write(directorio.resolve(nombre), contenido);
    }
}
