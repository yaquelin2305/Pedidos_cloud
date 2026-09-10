package cl.duoc.pedidos360.orders.dto;

import java.time.Instant;

/**
 * Cuerpo JSON uniforme para las respuestas de error de este microservicio.
 * Misma estructura que ms-pedidos360-catalog para que ambas APIs sean consistentes.
 */
public record ApiErrorResponse(Instant timestamp, int status, String error, String message, String path) {

    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, error, message, path);
    }
}
