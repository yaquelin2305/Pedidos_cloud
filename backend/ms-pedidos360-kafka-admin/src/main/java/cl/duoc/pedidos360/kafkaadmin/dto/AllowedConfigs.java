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

/**
 * Mapa de configuraciones de topico cuyas claves estan en {@link TopicConfigRules#ALLOWED_KEYS} y
 * cuyos valores son validos para su clave. Un mapa nulo o vacio es valido.
 */
@Documented
@Constraint(validatedBy = AllowedConfigsValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface AllowedConfigs {

    String message() default "configuraciones invalidas";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
