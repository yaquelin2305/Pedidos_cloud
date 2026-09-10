package cl.duoc.pedidos360.catalog.dto;

import java.time.Instant;

/**
 * Cuerpo JSON uniforme para las respuestas de error de este microservicio.
 */
public record ApiErrorResponse(Instant timestamp, int status, String error, String message, String path) {

    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path);
    }
}
