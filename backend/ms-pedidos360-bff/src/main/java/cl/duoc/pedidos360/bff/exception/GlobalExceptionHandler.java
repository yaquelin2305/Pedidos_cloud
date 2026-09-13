package cl.duoc.pedidos360.bff.exception;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import lombok.RequiredArgsConstructor;

/**
 * Traduce las excepciones a una respuesta JSON uniforme. El 401 lo resuelve el entry point
 * de seguridad; el 403 de {@code @PreAuthorize} se maneja aqui porque lo lanza el controlador
 * y no llega al {@code AccessDeniedHandler} del filtro.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(HttpStatus.FORBIDDEN.value(), "Forbidden",
                        "El rol del usuario no autoriza esta operacion"));
    }

    /**
     * Propaga el status y el mensaje que devolvio orders/catalog, en vez de aplanar todo a 500:
     * un 404 o 409 de dominio sigue siendo un 404 o 409 para el frontend.
     */
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleDownstreamError(WebClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String message = readDownstreamMessage(ex.getResponseBodyAsString())
                .orElse("El servicio de dominio no pudo procesar la solicitud.");
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status.value(), HttpStatus.valueOf(status.value()).getReasonPhrase(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Internal Server Error", ex.getMessage()));
    }

    private Optional<String> readDownstreamMessage(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode messageNode = node.get("message");
            return messageNode == null ? Optional.empty() : Optional.of(messageNode.asText());
        } catch (Exception parseError) {
            return Optional.empty();
        }
    }
}
