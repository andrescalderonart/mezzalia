package com.mezzalia.common.excepcion;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test unitario puro (sin contexto de Spring, GlobalExceptionHandler no
 * tiene dependencias) del status HTTP real que cada @ExceptionHandler
 * devuelve. No existia ANTES de este archivo, y esa ausencia es exactamente
 * como TenantSuspendidoException termino devolviendo 403 durante un tiempo
 * mientras su @ResponseStatus(HttpStatus.CONFLICT) decia 409 -- nada
 * ejercitaba el handler para notar la diferencia entre lo que la anotacion
 * de la excepcion "documentaba" y lo que el metodo realmente devolvia.
 * @ExceptionHandler explicito siempre gana sobre @ResponseStatus de la
 * clase, asi que la unica fuente de verdad del status es este archivo, y
 * es la unica que este test verifica.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void tenantNoEncontradoDevuelve404() {
        assertStatus(handler.handleTenantNoEncontrado(new DomainExceptions.TenantNoEncontradoException(1L)), HttpStatus.NOT_FOUND);
    }

    @Test
    void tenantYaExisteDevuelve409() {
        assertStatus(handler.handleTenantYaExiste(new DomainExceptions.TenantYaExisteException("X")), HttpStatus.CONFLICT);
    }

    /**
     * Este es el caso que motivo este archivo: hasta ahora este handler
     * devolvia FORBIDDEN (403) pese a que TenantSuspendidoException llego a
     * estar anotada @ResponseStatus(HttpStatus.CONFLICT) -- un cliente
     * publico y anonimo (carta/branding via QR) recibia 403 sin que ningun
     * test lo notara. Se corrigio a 409: no hay ninguna identidad que
     * "autorizar" en esos endpoints publicos, lo que bloquea la request es
     * el ESTADO del tenant (suspendido), un estado reversible por diseno.
     */
    @Test
    void tenantSuspendidoDevuelve409NoForbidden() {
        assertStatus(handler.handleTenantSuspendido(new DomainExceptions.TenantSuspendidoException(1L)), HttpStatus.CONFLICT);
    }

    @Test
    void colorHexInvalidoDevuelve400() {
        assertStatus(handler.handleColorHexInvalido(new DomainExceptions.ColorHexInvalidoException("azul")), HttpStatus.BAD_REQUEST);
    }

    /**
     * JSON malformado o un enum invalido en un @RequestBody (ej. "estado":
     * "COCINANDO" en el PATCH de pedidos) no es un fallo del servidor -- es
     * un dato invalido del cliente. Sin este handler caia en el catch-all de
     * abajo y devolvia 500.
     */
    @Test
    void mensajeNoLegibleDevuelve400() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("JSON invalido", (HttpInputMessage) null);

        assertStatus(handler.handleMensajeNoLegible(ex), HttpStatus.BAD_REQUEST);
    }

    @Test
    void excepcionGenericaDevuelve500SinFiltrarElMensajeInterno() {
        ResponseEntity<Map<String, Object>> respuesta = handler.handleGenerico(new RuntimeException("detalle interno sensible"));

        assertStatus(respuesta, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(respuesta.getBody().get("mensaje"))
                .as("el catch-all no deberia filtrar el mensaje de la excepcion original al cliente")
                .isEqualTo("Error interno del servidor");
    }

    private void assertStatus(ResponseEntity<Map<String, Object>> respuesta, HttpStatus esperado) {
        assertThat(respuesta.getStatusCode()).isEqualTo(esperado);
        assertThat(respuesta.getBody().get("status")).isEqualTo(esperado.value());
    }
}
