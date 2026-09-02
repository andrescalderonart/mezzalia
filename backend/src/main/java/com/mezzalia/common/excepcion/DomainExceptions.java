package com.mezzalia.common.excepcion;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public final class DomainExceptions {

    private DomainExceptions() {}

    // ── Tenant ──────────────────────────────────────────────────────────────

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class TenantNoEncontradoException extends RuntimeException {
        public TenantNoEncontradoException(Long id) {
            super("Tenant no encontrado con id: " + id);
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class TenantYaExisteException extends RuntimeException {
        public TenantYaExisteException(String nombre) {
            super("Ya existe un tenant con el nombre: " + nombre);
        }
    }

    // Sin @ResponseStatus a proposito: GlobalExceptionHandler tiene un
    // @ExceptionHandler explicito para esta excepcion, y ese SIEMPRE gana
    // sobre la anotacion de la clase -- tenerla aca (aunque coincidiera)
    // seria un status "documentado" que Spring nunca mira, la misma trampa
    // que hizo que esto estuviera en 403 real vs 409 anotado hasta ahora.
    // La fuente de verdad del status es el handler, no esta clase.
    //
    // El mensaje SI importa aca, a diferencia de las otras excepciones de
    // esta clase: TenantService.obtenerPorId documenta que esta excepcion es
    // de uso EXCLUSIVO de caminos publicos/de cliente final (el comensal
    // escaneando un QR, via MenuPublicoController/TenantPublicoController) --
    // getMessage() de una RuntimeException es literalmente lo que
    // GlobalExceptionHandler.respuesta() manda como "mensaje" en el body al
    // cliente. El mensaje anterior ("El tenant con id N esta suspendido")
    // era para un desarrollador leyendo un log, no para un comensal: llegaba
    // TAL CUAL a la pantalla de CartaPage.jsx (frontend/app-cliente) bajo
    // "No pudimos abrir la carta". Se cambia al mismo texto ya establecido
    // para el mismo tipo de situacion en el modulo mesa (ver
    // mesa.excepcion.MesaExceptions.RestauranteNoDisponibleException) --
    // mismo criterio, no un texto nuevo inventado aca. El id ya NO va en el
    // mensaje a proposito (no es informacion que un comensal deba ver, y
    // menos sin autenticacion), se mantiene como parametro del constructor
    // por si en el futuro hace falta para logging del lado del servidor.
    public static class TenantSuspendidoException extends RuntimeException {
        public TenantSuspendidoException(Long id) {
            super("Este restaurante no está disponible en este momento.");
        }
    }

    // ── Validación de dominio ────────────────────────────────────────────────

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public static class ColorHexInvalidoException extends RuntimeException {
        public ColorHexInvalidoException(String valor) {
            super("El color '" + valor + "' no es un valor hexadecimal válido (#RRGGBB)");
        }
    }
}