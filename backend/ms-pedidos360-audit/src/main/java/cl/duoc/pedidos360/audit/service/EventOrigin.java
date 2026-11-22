package cl.duoc.pedidos360.audit.service;

/**
 * Posicion de un evento en el log de origen: queda en la bitacora para poder ubicarlo en Kafka UI.
 */
public record EventOrigin(String topic, int partition, long offset) {
}
