package cl.duoc.pedidos360.gateway.exception;

import java.time.Instant;

/**
 * Cuerpo JSON uniforme para las respuestas de error del gateway.
 */
public record ErrorResponse(Instant timestamp, int status, String error, String message) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(Instant.now(), status, error, message);
    }
}
