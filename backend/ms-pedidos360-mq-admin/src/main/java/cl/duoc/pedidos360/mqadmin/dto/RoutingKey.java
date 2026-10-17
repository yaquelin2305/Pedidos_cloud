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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Routing key valida: hasta 255 caracteres, con palabras de letras, digitos, guion y guion bajo
 * separadas por punto. Los unicos comodines aceptados son {@code *} y {@code #}, los de los
 * exchanges topic. Puede ser vacia, como en los exchanges fanout.
 */
@Size(max = 255, message = "admite hasta 255 caracteres")
@Pattern(regexp = "^[A-Za-z0-9._*#-]*$",
        message = "solo admite letras, digitos, punto, guion, guion bajo y los comodines * y #")
@Documented
@Constraint(validatedBy = {})
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface RoutingKey {

    String message() default "routing key invalida";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
