package cl.duoc.pedidos360.orders.service;

import java.util.List;

import cl.duoc.pedidos360.orders.dto.OrderRequestDTO;
import cl.duoc.pedidos360.orders.dto.OrderResponseDTO;
import cl.duoc.pedidos360.orders.entity.OrderStatus;

public interface OrderService {

    List<OrderResponseDTO> list(OrderRequester requester);

    OrderResponseDTO getById(Long id, OrderRequester requester);

    OrderResponseDTO create(OrderRequestDTO request, OrderRequester requester);

    OrderResponseDTO changeStatus(Long id, OrderStatus newStatus, OrderRequester requester);

    OrderResponseDTO cancel(Long id, OrderRequester requester);
}
