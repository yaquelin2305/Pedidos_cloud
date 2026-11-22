package cl.duoc.pedidos360.kafkaadmin.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Valida cada entrada del mapa y reporta todos los problemas en un solo mensaje, para que el
 * cliente corrija la peticion de una vez.
 */
public class AllowedConfigsValidator implements ConstraintValidator<AllowedConfigs, Map<String, String>> {

    @Override
    public boolean isValid(Map<String, String> configs, ConstraintValidatorContext context) {
        if (configs == null || configs.isEmpty()) {
            return true;
        }
        List<String> problems = new ArrayList<>();
        configs.forEach((key, value) -> {
            String keyError = TopicConfigRules.validateKey(key);
            String error = keyError != null ? keyError : TopicConfigRules.validateValue(key, value);
            if (error != null) {
                problems.add(error);
            }
        });
        if (problems.isEmpty()) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(ValidationMessages.escape(String.join("; ", problems)))
                .addConstraintViolation();
        return false;
    }
}
