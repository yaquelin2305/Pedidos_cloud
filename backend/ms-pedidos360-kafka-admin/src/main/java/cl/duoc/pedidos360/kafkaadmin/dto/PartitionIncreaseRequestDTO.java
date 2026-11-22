package cl.duoc.pedidos360.kafkaadmin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Cantidad total de particiones que debe tener el topico. Kafka solo permite aumentarlas: el
 * servicio rechaza un valor que no supere el actual.
 */
public record PartitionIncreaseRequestDTO(
        @NotNull(message = "es obligatorio")
        @Min(value = 2, message = "debe estar entre 2 y 12")
        @Max(value = 12, message = "debe estar entre 2 y 12")
        Integer totalCount) {
}
