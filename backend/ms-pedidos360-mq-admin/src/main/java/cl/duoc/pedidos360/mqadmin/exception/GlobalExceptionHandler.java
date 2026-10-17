package cl.duoc.pedidos360.mqadmin.exception;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import cl.duoc.pedidos360.mqadmin.dto.ApiErrorResponse;

/**
 * Traduce las excepciones del administrador a una respuesta JSON uniforme, con el mismo formato
 * que orders y catalog. El 401 lo resuelve el entry point de seguridad; el 403 de
 * {@code @PreAuthorize} se maneja aqui porque lo lanza el controlador.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex, WebRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ResourceConflictException ex, WebRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(ProtectedResourceException.class)
    public ResponseEntity<ApiErrorResponse> handleProtected(ProtectedResourceException ex, WebRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(BrokerUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleBrokerUnavailable(BrokerUnavailableException ex,
            WebRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    /** Errores del cuerpo: cada campo con su mensaje, incluidas las validaciones cruzadas del DTO. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex,
            WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    /** Errores de los nombres recibidos en la ruta o en la query. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleParameterValidation(HandlerMethodValidationException ex,
            WebRequest request) {
        String message = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> result.getMethodParameter().getParameterName() + ": "
                                + error.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    /**
     * Cuerpo ilegible. Un valor fuera de un enum ({@code type}) se informa con los valores
     * permitidos en vez del mensaje interno de Jackson.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
            WebRequest request) {
        String message = "El cuerpo de la peticion no es un JSON valido";
        if (ex.getCause() instanceof InvalidFormatException invalid && invalid.getTargetType().isEnum()) {
            String field = invalid.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .collect(Collectors.joining("."));
            String allowed = Arrays.stream(invalid.getTargetType().getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            message = field + ": valor '" + invalid.getValue() + "' invalido. Valores permitidos: " + allowed;
        }
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return build(HttpStatus.FORBIDDEN, "El rol del usuario no autoriza esta operacion", request);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, WebRequest request) {
        String path = request.getDescription(false).replace("uri=", "");
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(status.value(), status.getReasonPhrase(), message, path));
    }
}
