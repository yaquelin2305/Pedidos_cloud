package cl.duoc.pedidos360.orders.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record OrderRequestDTO(
        @NotEmpty @Valid List<OrderItemRequestDTO> items) {
}
