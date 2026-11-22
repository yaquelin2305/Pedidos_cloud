package cl.duoc.pedidos360.kafkaadmin.dto;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Un cambio de configuracion cuya clave esta en la lista permitida y cuyo valor, si se envia, es
 * valido para esa clave. Reporta cada error sobre el campo que lo causa.
 */
@Documented
@Constraint(validatedBy = ValidConfigChangeValidator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface ValidConfigChange {

    String message() default "cambio de configuracion invalido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
