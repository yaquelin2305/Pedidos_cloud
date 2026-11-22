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
 * Nombre de un topico recibido en la ruta para consultarlo o modificarlo: mismos caracteres y largo
 * que {@link TopicName}, pero sin rechazar el prefijo {@code _}. Asi un topico interno como
 * {@code __consumer_offsets} se puede describir, y al intentar eliminarlo la respuesta es el 409 de
 * recurso protegido en vez de un 400 de formato.
 */
@NotBlank(message = "es obligatorio")
@Size(max = 249, message = "admite hasta 249 caracteres")
@Pattern(regexp = TopicPath.PATTERN, message = TopicPath.PATTERN_MESSAGE)
@Documented
@Constraint(validatedBy = {})
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface TopicPath {

    String PATTERN = "^(?!\\.{1,2}$)[A-Za-z0-9._-]*$";

    String PATTERN_MESSAGE = "solo admite letras, digitos, punto, guion y guion bajo, y no puede ser '.' ni '..'";

    String message() default "nombre de topico invalido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
