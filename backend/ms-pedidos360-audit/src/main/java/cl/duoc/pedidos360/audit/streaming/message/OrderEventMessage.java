package cl.duoc.pedidos360.audit.streaming.message;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Mensaje del topico {@code pedidos360.orders.events} (key = {@code orderId}). Copia propia del
 * contrato que publica ms-pedidos360-orders; las anotaciones validan el payload al consumirlo.
 *
 * @param previousStatus {@code null} al crear el pedido
 */
public record OrderEventMessage(
        @NotNull UUID eventId,
        @NotNull String eventType,
        Integer schemaVersion,
        String source,
        @NotNull Instant occurredAt,
        @NotNull Long orderId,
        String customerId,
        String customerName,
        String actorId,
        String actorName,
        String previousStatus,
        @NotNull String status,
        @PositiveOrZero BigDecimal totalAmount,
        List<@Valid OrderLineMessage> items) {
}
