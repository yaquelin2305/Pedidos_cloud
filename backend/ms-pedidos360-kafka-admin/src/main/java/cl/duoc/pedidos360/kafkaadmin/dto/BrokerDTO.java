package cl.duoc.pedidos360.kafkaadmin.dto;

/**
 * Un broker del cluster tal como lo anuncia a los clientes.
 */
public record BrokerDTO(int id, String host, int port) {
}
