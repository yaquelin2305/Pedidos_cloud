package cl.duoc.pedidos360.mqadmin.dto;

/**
 * Datos para crear un exchange. {@code durable} es verdadero si no se envia.
 */
public record ExchangeRequestDTO(String name, ExchangeType type, Boolean durable) {

    public ExchangeRequestDTO {
        durable = durable == null || durable;
    }
}
