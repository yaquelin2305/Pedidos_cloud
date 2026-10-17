package cl.duoc.pedidos360.mqadmin.dto;

/**
 * Tipos de exchange de AMQP 0-9-1 que el administrador permite crear.
 */
public enum ExchangeType {
    DIRECT,
    TOPIC,
    FANOUT,
    HEADERS
}
