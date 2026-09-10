package cl.duoc.pedidos360.orders.dto;

import jakarta.validation.constraints.NotNull;

import cl.duoc.pedidos360.orders.entity.OrderStatus;

public record OrderStatusUpdateDTO(@NotNull OrderStatus status) {
}
