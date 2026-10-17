package cl.duoc.pedidos360.mqadmin.dto;

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
 * Nombre valido de cola o exchange: obligatorio, hasta 255 caracteres (limite de AMQP), solo
 * letras, digitos, punto, guion y guion bajo, y sin el prefijo {@code amq.}, que RabbitMQ reserva
 * para sus propios recursos.
 */
@NotBlank(message = "es obligatorio")
@Size(max = 255, message = "admite hasta 255 caracteres")
@Pattern(regexp = ResourceName.PATTERN, message = ResourceName.PATTERN_MESSAGE)
@Documented
@Constraint(validatedBy = {})
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface ResourceName {

    String PATTERN = "^(?!amq\\.)[A-Za-z0-9._-]*$";

    String PATTERN_MESSAGE = "solo admite letras, digitos, punto, guion y guion bajo, y no puede empezar con 'amq.'";

    String message() default "nombre invalido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
