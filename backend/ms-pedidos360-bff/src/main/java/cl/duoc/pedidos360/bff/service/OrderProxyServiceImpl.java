package cl.duoc.pedidos360.bff.service;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import cl.duoc.pedidos360.bff.client.OrdersClient;
import cl.duoc.pedidos360.bff.dto.OrderRequestDTO;
import cl.duoc.pedidos360.bff.dto.OrderResponseDTO;
import cl.duoc.pedidos360.bff.dto.OrderStatusUpdateDTO;

@Service
@RequiredArgsConstructor
public class OrderProxyServiceImpl implements OrderProxyService {

    private final OrdersClient ordersClient;

    @Override
    public List<OrderResponseDTO> list(String authorization) {
        return ordersClient.list(authorization);
    }

    @Override
    public OrderResponseDTO getById(Long id, String authorization) {
        return ordersClient.getById(id, authorization);
    }

    @Override
    public OrderResponseDTO create(OrderRequestDTO request, String authorization) {
        return ordersClient.create(request, authorization);
    }

    @Override
    public OrderResponseDTO changeStatus(Long id, OrderStatusUpdateDTO update, String authorization) {
        return ordersClient.changeStatus(id, update, authorization);
    }

    @Override
    public OrderResponseDTO cancel(Long id, String authorization) {
        return ordersClient.cancel(id, authorization);
    }
}
