package cl.duoc.pedidos360.kafkaadmin.dto;

import java.util.List;

/**
 * Reglas de las configuraciones de topico que el administrador permite leer y modificar. Es la
 * lista cerrada del contrato: cualquier otra clave se rechaza con 400.
 */
public final class TopicConfigRules {

    public static final List<String> ALLOWED_KEYS = List.of(
            "retention.ms",
            "retention.bytes",
            "cleanup.policy",
            "min.insync.replicas",
            "segment.ms",
            "min.cleanable.dirty.ratio",
            "delete.retention.ms",
            "max.message.bytes");

    private TopicConfigRules() {
    }
}
