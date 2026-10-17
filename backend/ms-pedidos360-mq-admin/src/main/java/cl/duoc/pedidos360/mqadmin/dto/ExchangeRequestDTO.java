package cl.duoc.pedidos360.mqadmin.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Datos para crear un exchange. {@code durable} es verdadero si no se envia.
 */
public record ExchangeRequestDTO(
        @ResourceName String name,
        @NotNull(message = "es obligatorio: DIRECT, TOPIC, FANOUT o HEADERS") ExchangeType type,
        Boolean durable) {

    public ExchangeRequestDTO {
        durable = durable == null || durable;
    }
}
