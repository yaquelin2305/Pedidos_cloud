package cl.duoc.pedidos360.audit.streaming.support;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Valida un mensaje recibido contra las anotaciones de su DTO. Un mensaje vacio (tombstone) o con
 * campos invalidos lanza {@link InvalidEventException} con todas las violaciones.
 */
@Component
@RequiredArgsConstructor
public class EventValidator {

    private final Validator validator;

    public <T> T validate(T message) {
        if (message == null) {
            throw new InvalidEventException("Mensaje sin contenido");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(message);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.joining("; "));
            throw new InvalidEventException("Payload invalido: " + detail);
        }
        return message;
    }
}
