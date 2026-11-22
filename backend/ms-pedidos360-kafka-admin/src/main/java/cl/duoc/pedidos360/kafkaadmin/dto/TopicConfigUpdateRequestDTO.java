package cl.duoc.pedidos360.kafkaadmin.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Cambios a aplicar de una vez sobre las configuraciones de un topico.
 */
public record TopicConfigUpdateRequestDTO(
        @NotEmpty(message = "debe incluir al menos un cambio")
        List<@NotNull(message = "no puede ser nulo") @Valid ConfigChangeDTO> changes) {

    /** Dos cambios sobre la misma clave serian ambiguos: Kafka rechaza la peticion completa. */
    @AssertTrue(message = "no puede haber dos cambios sobre la misma configuracion")
    public boolean isChangesWithoutDuplicates() {
        if (changes == null) {
            return true;
        }
        long distinct = changes.stream()
                .filter(change -> change != null && change.key() != null)
                .map(ConfigChangeDTO::key)
                .distinct()
                .count();
        long total = changes.stream().filter(change -> change != null && change.key() != null).count();
        return distinct == total;
    }
}
