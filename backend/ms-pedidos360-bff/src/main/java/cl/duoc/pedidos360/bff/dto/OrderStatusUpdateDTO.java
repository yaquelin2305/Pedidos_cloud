package cl.duoc.pedidos360.bff.dto;

import jakarta.validation.constraints.NotBlank;

public record OrderStatusUpdateDTO(@NotBlank String status) {
}
