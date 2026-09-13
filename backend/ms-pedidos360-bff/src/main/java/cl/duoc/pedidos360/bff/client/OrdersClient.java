package cl.duoc.pedidos360.bff.client;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.PatchExchange;
import org.springframework.web.service.annotation.PostExchange;

import cl.duoc.pedidos360.bff.dto.OrderRequestDTO;
import cl.duoc.pedidos360.bff.dto.OrderResponseDTO;
import cl.duoc.pedidos360.bff.dto.OrderStatusUpdateDTO;

/**
 * Espejo declarativo de /api/orders en ms-pedidos360-orders. El Bearer del llamador se
 * reenvia tal cual: orders vuelve a validar el mismo token de forma independiente.
 */
public interface OrdersClient {

    @GetExchange("/api/orders")
    List<OrderResponseDTO> list(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @GetExchange("/api/orders/{id}")
    OrderResponseDTO getById(@PathVariable Long id, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @PostExchange("/api/orders")
    OrderResponseDTO create(@RequestBody OrderRequestDTO request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @PatchExchange("/api/orders/{id}/status")
    OrderResponseDTO changeStatus(@PathVariable Long id, @RequestBody OrderStatusUpdateDTO update,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

    @DeleteExchange("/api/orders/{id}")
    OrderResponseDTO cancel(@PathVariable Long id, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);
}
