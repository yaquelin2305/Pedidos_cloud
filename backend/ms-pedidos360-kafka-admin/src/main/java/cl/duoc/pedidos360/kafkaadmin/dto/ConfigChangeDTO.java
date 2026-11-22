package cl.duoc.pedidos360.kafkaadmin.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.util.StringUtils;

/**
 * Un cambio sobre una configuracion de topico. {@code value} es obligatorio solo en {@code SET}.
 */
@ValidConfigChange
public record ConfigChangeDTO(
        @NotBlank(message = "es obligatorio") String key,
        @NotNull(message = "es obligatorio: SET o DELETE") ConfigOperation operation,
        String value) {

    /** Un SET sin valor no dice a que fijar la configuracion. */
    @AssertTrue(message = "la operacion SET requiere un value")
    public boolean isValueProvidedForSet() {
        return operation != ConfigOperation.SET || StringUtils.hasText(value);
    }
}
