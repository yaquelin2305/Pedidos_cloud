package cl.duoc.pedidos360.orders.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import cl.duoc.pedidos360.orders.dto.OrderRequestDTO;
import cl.duoc.pedidos360.orders.dto.OrderResponseDTO;
import cl.duoc.pedidos360.orders.entity.Order;
import cl.duoc.pedidos360.orders.entity.OrderItem;
import cl.duoc.pedidos360.orders.entity.OrderStatus;
import cl.duoc.pedidos360.orders.event.OrderEvent;
import cl.duoc.pedidos360.orders.exception.ForbiddenOrderAccessException;
import cl.duoc.pedidos360.orders.exception.InvalidOrderStatusTransitionException;
import cl.duoc.pedidos360.orders.exception.OrderNotFoundException;
import cl.duoc.pedidos360.orders.repository.OrderRepository;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    /** Estados desde los que un Cliente (no privilegiado) puede cancelar su propio pedido. */
    private static final Set<OrderStatus> CUSTOMER_CANCELABLE_FROM = EnumSet.of(OrderStatus.CREADO);

    /** Estados desde los que Operator/Admin pueden cancelar cualquier pedido. */
    private static final Set<OrderStatus> PRIVILEGED_CANCELABLE_FROM = EnumSet.of(OrderStatus.CREADO,
            OrderStatus.ACEPTADO);

    private final OrderRepository orderRepository;
    private final CatalogClient catalogClient;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponseDTO> list(OrderRequester requester) {
        List<Order> orders = requester.privileged()
                ? orderRepository.findAll()
                : orderRepository.findByCustomerId(requester.customerId());
        return orders.stream().map(OrderResponseDTO::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponseDTO getById(Long id, OrderRequester requester) {
        return OrderResponseDTO.from(findWithOwnershipCheck(id, requester));
    }

    @Override
    @Transactional
    public OrderResponseDTO create(OrderRequestDTO request, OrderRequester requester) {
        Order order = Order.builder()
                .customerId(requester.customerId())
                .customerName(requester.customerName())
                .status(OrderStatus.CREADO)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (var itemRequest : request.items()) {
            CatalogProductDTO product = catalogClient.getProduct(itemRequest.productId());
            BigDecimal subtotal = product.price().multiply(BigDecimal.valueOf(itemRequest.quantity()));
            order.addItem(OrderItem.builder()
                    .productId(product.id())
                    .productName(product.name())
                    .quantity(itemRequest.quantity())
                    .unitPrice(product.price())
                    .subtotal(subtotal)
                    .build());
            total = total.add(subtotal);
        }
        order.setTotalAmount(total);

        Order saved = orderRepository.save(order);
        publishOrderEvent(saved, null);
        return OrderResponseDTO.from(saved);
    }

    /**
     * Cambia el estado del pedido validando la tabla de transiciones. Al aceptar desde CREADO
     * se descuenta stock en catalog por cada linea antes de confirmar el cambio: si
     * alguna linea falla, se lanza StockUnavailableException y el pedido permanece en su estado
     * anterior (la excepcion revierte la transaccion, por lo que ningun cambio de estado ni de
     * timestamps queda persistido).
     *
     * Limitacion conocida: el descuento se hace linea por linea contra un servicio externo. Si
     * la linea 2 falla despues de que la linea 1 ya descarto stock en catalog, ese descuento
     * previo no se revierte aqui (no existe un endpoint de reversion en catalog ni una saga que
     * lo coordine).
     */
    @Override
    @Transactional
    public OrderResponseDTO changeStatus(Long id, OrderStatus newStatus, OrderRequester requester) {
        if (!requester.privileged()) {
            throw new AccessDeniedException("Solo Admin y Operator pueden cambiar el estado");
        }
        Order order = findWithOwnershipCheck(id, requester);
        OrderStatus previousStatus = order.getStatus();
        OrderStatusValidator.validateTransition(previousStatus, newStatus);

        if (previousStatus == OrderStatus.CREADO && newStatus == OrderStatus.ACEPTADO) {
            for (OrderItem item : order.getItems()) {
                catalogClient.decreaseStock(item.getProductId(), item.getQuantity());
            }
            order.setAcceptedAt(Instant.now());
        } else if (previousStatus == OrderStatus.EN_PREPARACION && newStatus == OrderStatus.DESPACHADO) {
            order.setDispatchedAt(Instant.now());
        } else if (newStatus == OrderStatus.ENTREGADO) {
            order.setDeliveredAt(Instant.now());
        }

        if (previousStatus == OrderStatus.ENTREGADO && newStatus == OrderStatus.DESPACHADO) {
            order.setDeliveredAt(null);
        } else if (previousStatus == OrderStatus.DESPACHADO && newStatus == OrderStatus.EN_PREPARACION) {
            order.setDispatchedAt(null);
            order.setDeliveredAt(null);
        }

        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);
        publishOrderEvent(saved, previousStatus);
        return OrderResponseDTO.from(saved);
    }

    @Override
    @Transactional
    public OrderResponseDTO cancel(Long id, OrderRequester requester) {
        Order order = findWithOwnershipCheck(id, requester);
        OrderStatus previousStatus = order.getStatus();
        Set<OrderStatus> allowedFrom = requester.privileged() ? PRIVILEGED_CANCELABLE_FROM : CUSTOMER_CANCELABLE_FROM;
        if (!allowedFrom.contains(previousStatus)) {
            throw new InvalidOrderStatusTransitionException(previousStatus, OrderStatus.CANCELADO);
        }
        order.setStatus(OrderStatus.CANCELADO);
        Order saved = orderRepository.save(order);
        publishOrderEvent(saved, previousStatus);
        return OrderResponseDTO.from(saved);
    }

    private void publishOrderEvent(Order order, OrderStatus previousStatus) {
        eventPublisher.publishEvent(new OrderEvent(UUID.randomUUID(), order.getId(), order.getCustomerId(),
                order.getCustomerName(), previousStatus, order.getStatus(), order.getTotalAmount(), Instant.now()));
    }

    private Order findWithOwnershipCheck(Long id, OrderRequester requester) {
        Order order = orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
        if (!requester.privileged() && !order.getCustomerId().equals(requester.customerId())) {
            throw new ForbiddenOrderAccessException(id);
        }
        return order;
    }
}
