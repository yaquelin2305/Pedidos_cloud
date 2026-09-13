package cl.duoc.pedidos360.bff.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Espejo del contrato de ms-pedidos360-orders. El status viaja como String: el BFF no
 * necesita el enum (CREADO/ACEPTADO/...) para enrutar ni autorizar, solo lo relaya.
 */
public record OrderResponseDTO(
        Long id,
        String customerId,
        String customerName,
        String status,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant acceptedAt,
        Instant dispatchedAt,
        Instant deliveredAt,
        List<OrderItemDTO> items) {
}
