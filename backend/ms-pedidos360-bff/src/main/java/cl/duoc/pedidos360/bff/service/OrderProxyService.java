package cl.duoc.pedidos360.bff.service;

import java.util.List;

import cl.duoc.pedidos360.bff.dto.OrderRequestDTO;
import cl.duoc.pedidos360.bff.dto.OrderResponseDTO;
import cl.duoc.pedidos360.bff.dto.OrderStatusUpdateDTO;

public interface OrderProxyService {

    List<OrderResponseDTO> list(String authorization);

    OrderResponseDTO getById(Long id, String authorization);

    OrderResponseDTO create(OrderRequestDTO request, String authorization);

    OrderResponseDTO changeStatus(Long id, OrderStatusUpdateDTO update, String authorization);

    OrderResponseDTO cancel(Long id, String authorization);
}
