package cl.duoc.pedidos360.kafkaadmin.dto;

/**
 * Cambio sobre una configuracion de topico: {@code SET} fija un valor y {@code DELETE} elimina el
 * valor propio del topico, que vuelve al valor por defecto del broker.
 */
public enum ConfigOperation {
    SET,
    DELETE
}
