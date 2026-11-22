package cl.duoc.pedidos360.kafkaadmin.dto;

import java.util.List;

/**
 * Reglas de las configuraciones de topico que el administrador permite leer y modificar. Es la
 * lista cerrada del contrato: cualquier otra clave se rechaza con 400, y cada valor se valida
 * segun su tipo antes de llegar al broker.
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

    public static final String MIN_INSYNC_REPLICAS = "min.insync.replicas";

    private static final String CLEANUP_POLICY = "cleanup.policy";
    private static final String DIRTY_RATIO = "min.cleanable.dirty.ratio";
    private static final List<String> CLEANUP_POLICIES = List.of("delete", "compact", "compact,delete");

    private TopicConfigRules() {
    }

    /** Mensaje de error si la clave no esta en la lista permitida; nulo si es valida. */
    public static String validateKey(String key) {
        if (key == null || !ALLOWED_KEYS.contains(key)) {
            return "la configuracion '" + key + "' no esta permitida. Permitidas: " + String.join(", ", ALLOWED_KEYS);
        }
        return null;
    }

    /**
     * Mensaje de error si el valor no sirve para esa clave; nulo si es valido. La clave debe haberse
     * validado antes con {@link #validateKey}.
     */
    public static String validateValue(String key, String value) {
        if (value == null || value.isBlank()) {
            return "'" + key + "' requiere un valor";
        }
        if (CLEANUP_POLICY.equals(key)) {
            return CLEANUP_POLICIES.contains(value)
                    ? null
                    : "'" + CLEANUP_POLICY + "' solo admite: " + String.join(", ", CLEANUP_POLICIES);
        }
        if (DIRTY_RATIO.equals(key)) {
            return isRatio(value) ? null : "'" + DIRTY_RATIO + "' debe ser un decimal entre 0 y 1";
        }
        return isPositiveInteger(value) ? null : "'" + key + "' debe ser un entero positivo";
    }

    private static boolean isPositiveInteger(String value) {
        try {
            return Long.parseLong(value) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean isRatio(String value) {
        try {
            double ratio = Double.parseDouble(value);
            return ratio >= 0 && ratio <= 1;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
