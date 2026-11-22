package cl.duoc.pedidos360.report.service;

/**
 * Posicion de un evento en el log de origen: se registra junto a la proyeccion para poder ubicarlo en Kafka UI.
 */
public record EventOrigin(String topic, int partition, long offset) {
}
