package cl.duoc.pedidos360.mqadmin.dto;

/**
 * Datos para ligar una cola a un exchange. Sin {@code routingKey} se usa la clave vacia, la que
 * esperan los exchanges fanout.
 */
public record BindingRequestDTO(String exchange, String queue, String routingKey) {

    public BindingRequestDTO {
        routingKey = routingKey == null ? "" : routingKey;
    }
}
