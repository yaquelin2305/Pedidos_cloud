package cl.duoc.pedidos360.kafkaadmin.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Datos para crear un topico. {@code configs} es opcional: los topicos sin configuracion propia
 * heredan los valores por defecto de los brokers.
 */
public record TopicCreateRequestDTO(
        @TopicName String name,
        @NotNull(message = "es obligatorio")
        @Min(value = 1, message = "debe estar entre 1 y 12")
        @Max(value = 12, message = "debe estar entre 1 y 12")
        Integer partitions,
        @NotNull(message = "es obligatorio")
        @Min(value = 1, message = "debe estar entre 1 y 3")
        @Max(value = 3, message = "debe estar entre 1 y 3")
        Integer replicationFactor,
        @AllowedConfigs Map<String, String> configs) {

    public TopicCreateRequestDTO {
        configs = configs == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(configs));
    }
}
