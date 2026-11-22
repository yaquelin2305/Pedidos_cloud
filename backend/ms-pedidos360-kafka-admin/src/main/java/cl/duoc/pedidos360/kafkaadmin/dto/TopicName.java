package cl.duoc.pedidos360.kafkaadmin.dto;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Nombre valido para crear un topico: obligatorio, hasta 249 caracteres (limite de Kafka), solo
 * letras, digitos, punto, guion y guion bajo, distinto de {@code .} y {@code ..}, y sin el prefijo
 * {@code _}, que Kafka reserva para sus topicos internos.
 */
@NotBlank(message = "es obligatorio")
@Size(max = 249, message = "admite hasta 249 caracteres")
@Pattern(regexp = TopicName.PATTERN, message = TopicName.PATTERN_MESSAGE)
@Documented
@Constraint(validatedBy = {})
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface TopicName {

    String PATTERN = "^(?!_)(?!\\.{1,2}$)[A-Za-z0-9._-]*$";

    String PATTERN_MESSAGE = "solo admite letras, digitos, punto, guion y guion bajo, no puede ser '.' ni '..' "
            + "y no puede empezar con '_'";

    String message() default "nombre de topico invalido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
