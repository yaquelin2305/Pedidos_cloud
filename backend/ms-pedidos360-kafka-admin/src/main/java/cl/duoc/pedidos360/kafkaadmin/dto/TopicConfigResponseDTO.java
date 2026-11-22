package cl.duoc.pedidos360.kafkaadmin.dto;

import java.util.Map;

/**
 * Configuraciones de la lista permitida que tiene el topico despues de aplicar los cambios.
 */
public record TopicConfigResponseDTO(String name, Map<String, String> configs) {
}
