package cl.duoc.pedidos360.kafkaadmin.exception;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import cl.duoc.pedidos360.kafkaadmin.dto.ApiErrorResponse;

/**
 * Traduce las excepciones del administrador a una respuesta JSON uniforme, con el mismo formato
 * que orders y catalog. El 401 lo resuelve el entry point de seguridad; el 403 de
 * {@code @PreAuthorize} se maneja aqui porque lo lanza el controlador.
 */
@Slf4j
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

    /** Peticion valida en formato pero imposible de cumplir en el cluster, o rechazada por el broker. */
    @ExceptionHandler(InvalidKafkaRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(InvalidKafkaRequestException ex,
            WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /** El detalle del broker se registra en el log y no se expone al cliente. */
    @ExceptionHandler(KafkaOperationException.class)
    public ResponseEntity<ApiErrorResponse> handleKafkaOperation(KafkaOperationException ex, WebRequest request) {
        log.error("Error inesperado de Kafka en {}: {}", request.getDescription(false), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Kafka devolvio un error inesperado al procesar la operacion",
                request);
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

    /**
     * Errores de validacion de los parametros del metodo. Cuando el endpoint valida un nombre de la
     * ruta y tambien un cuerpo {@code @Valid}, Spring informa ambos por esta via: los errores de un
     * cuerpo se rotulan con el nombre de su campo y los de un parametro simple con el del parametro.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleParameterValidation(HandlerMethodValidationException ex,
            WebRequest request) {
        String message = ex.getParameterValidationResults().stream()
                .flatMap(result -> {
                    if (result instanceof ParameterErrors errors) {
                        return Stream.concat(
                                errors.getFieldErrors().stream()
                                        .map(error -> error.getField() + ": " + error.getDefaultMessage()),
                                errors.getGlobalErrors().stream().map(error -> error.getDefaultMessage()));
                    }
                    return result.getResolvableErrors().stream()
                            .map(error -> result.getMethodParameter().getParameterName() + ": "
                                    + error.getDefaultMessage());
                })
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    /**
     * Cuerpo ilegible. Un valor fuera de un enum ({@code operation}) se informa con los valores
     * permitidos en vez del mensaje interno de Jackson.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
            WebRequest request) {
        String message = "El cuerpo de la peticion no es un JSON valido";
        if (ex.getCause() instanceof InvalidFormatException invalid && invalid.getTargetType().isEnum()) {
            String field = fieldPath(invalid.getPath());
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

    /** Ruta del campo con error, con los indices de lista entre corchetes: {@code changes[0].operation}. */
    private static String fieldPath(List<JsonMappingException.Reference> path) {
        StringBuilder field = new StringBuilder();
        for (JsonMappingException.Reference reference : path) {
            if (reference.getFieldName() != null) {
                if (!field.isEmpty()) {
                    field.append('.');
                }
                field.append(reference.getFieldName());
            } else if (reference.getIndex() >= 0) {
                field.append('[').append(reference.getIndex()).append(']');
            }
        }
        return field.toString();
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, WebRequest request) {
        String path = request.getDescription(false).replace("uri=", "");
        return ResponseEntity.status(status)
                .body(ApiErrorResponse.of(status.value(), status.getReasonPhrase(), message, path));
    }
}
