package cl.duoc.pedidos360.report.exception;

import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import cl.duoc.pedidos360.report.dto.ApiErrorResponse;

/**
 * Traduce las excepciones de la API de reportoria a una respuesta JSON uniforme, con el mismo formato
 * que el resto de los servicios. El 401 lo resuelve el entry point de seguridad; el 403 de
 * {@code @PreAuthorize} se maneja aqui porque lo lanza el controlador.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Errores de rango en los parametros de la query ({@code limit}). */
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

    /** Un parametro con tipo invalido; si es un enum ({@code aggregateType}) se informan los valores permitidos. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
            WebRequest request) {
        String message = ex.getName() + ": valor '" + ex.getValue() + "' invalido";
        Class<?> type = ex.getRequiredType();
        if (type != null && type.isEnum()) {
            String allowed = Arrays.stream(type.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            message += ". Valores permitidos: " + allowed;
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
