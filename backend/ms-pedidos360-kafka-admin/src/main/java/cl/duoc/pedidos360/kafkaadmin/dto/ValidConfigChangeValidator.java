package cl.duoc.pedidos360.kafkaadmin.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Valida la clave y el valor de un {@link ConfigChangeDTO}. La falta de clave, de operacion o de
 * valor en un {@code SET} la reportan las otras restricciones del DTO, por lo que aqui se omiten.
 */
public class ValidConfigChangeValidator implements ConstraintValidator<ValidConfigChange, ConfigChangeDTO> {

    @Override
    public boolean isValid(ConfigChangeDTO change, ConstraintValidatorContext context) {
        if (change == null || change.key() == null || change.key().isBlank()) {
            return true;
        }
        String keyError = TopicConfigRules.validateKey(change.key());
        if (keyError != null) {
            return report(context, "key", keyError);
        }
        if (change.operation() == ConfigOperation.SET && change.value() != null && !change.value().isBlank()) {
            String valueError = TopicConfigRules.validateValue(change.key(), change.value());
            if (valueError != null) {
                return report(context, "value", valueError);
            }
        }
        return true;
    }

    private static boolean report(ConstraintValidatorContext context, String field, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(ValidationMessages.escape(message))
                .addPropertyNode(field)
                .addConstraintViolation();
        return false;
    }
}
