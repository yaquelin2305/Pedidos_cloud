package cl.duoc.pedidos360.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cl.duoc.pedidos360.orders.dto.OrderItemRequestDTO;
import cl.duoc.pedidos360.orders.dto.OrderRequestDTO;
import cl.duoc.pedidos360.orders.dto.OrderResponseDTO;
import cl.duoc.pedidos360.orders.entity.Order;
import cl.duoc.pedidos360.orders.entity.OrderItem;
import cl.duoc.pedidos360.orders.entity.OrderStatus;
import cl.duoc.pedidos360.orders.exception.ForbiddenOrderAccessException;
import cl.duoc.pedidos360.orders.exception.InvalidOrderStatusTransitionException;
import cl.duoc.pedidos360.orders.exception.StockUnavailableException;
import cl.duoc.pedidos360.orders.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final OrderRequester CUSTOMER = new OrderRequester("customer-1", false);
    private static final OrderRequester OTHER_CUSTOMER = new OrderRequester("customer-2", false);
    private static final OrderRequester OPERATOR = new OrderRequester("operator-1", true);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private OrderEventPublisher eventPublisher;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderRepository, catalogClient, eventPublisher);
    }

    private Order order(Long id, String customerId, OrderStatus status) {
        Order order = Order.builder()
                .id(id)
                .customerId(customerId)
                .status(status)
                .totalAmount(BigDecimal.valueOf(100))
                .build();
        order.addItem(OrderItem.builder()
                .id(1L)
                .productId(10L)
                .productName("Teclado")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(50))
                .subtotal(BigDecimal.valueOf(100))
                .build());
        return order;
    }

    @Test
    void creaUnPedidoConsultandoPrecioYNombreEnCatalogo() {
        OrderRequestDTO request = new OrderRequestDTO(java.util.List.of(new OrderItemRequestDTO(10L, 2)));
        when(catalogClient.getProduct(10L))
                .thenReturn(new CatalogProductDTO(10L, "Teclado", BigDecimal.valueOf(50), 20, true));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        OrderResponseDTO response = orderService.create(request, CUSTOMER);

        assertThat(response.status()).isEqualTo(OrderStatus.CREADO);
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productName()).isEqualTo("Teclado");
    }

    @Test
    void aceptaUnPedidoYDescuentaStockEnCatalogo() {
        Order order = order(1L, CUSTOMER.customerId(), OrderStatus.CREADO);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponseDTO response = orderService.changeStatus(1L, OrderStatus.ACEPTADO, OPERATOR);

        assertThat(response.status()).isEqualTo(OrderStatus.ACEPTADO);
        verify(catalogClient).decreaseStock(10L, 2);
        verify(eventPublisher).publishStatusChanged(any(Order.class), eq(OrderStatus.CREADO));
    }

    @Test
    void rechazaAceptacionPorStockInsuficienteYNoCambiaElEstado() {
        Order order = order(1L, CUSTOMER.customerId(), OrderStatus.CREADO);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        doThrow(new StockUnavailableException(10L, "stock insuficiente"))
                .when(catalogClient).decreaseStock(10L, 2);

        assertThatThrownBy(() -> orderService.changeStatus(1L, OrderStatus.ACEPTADO, OPERATOR))
                .isInstanceOf(StockUnavailableException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREADO);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void rechazaTransicionInvalidaDeCreadoADespachado() {
        Order order = order(1L, CUSTOMER.customerId(), OrderStatus.CREADO);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.changeStatus(1L, OrderStatus.DESPACHADO, OPERATOR))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
        verify(catalogClient, never()).decreaseStock(any(), anyInt());
    }

    @Test
    void unClienteNoPuedeVerElPedidoDeOtroCliente() {
        Order order = order(1L, CUSTOMER.customerId(), OrderStatus.CREADO);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getById(1L, OTHER_CUSTOMER))
                .isInstanceOf(ForbiddenOrderAccessException.class);
    }
}
