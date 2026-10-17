package cl.duoc.pedidos360.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cl.duoc.pedidos360.orders.dto.OrderItemRequestDTO;
import cl.duoc.pedidos360.orders.dto.OrderRequestDTO;
import cl.duoc.pedidos360.orders.dto.OrderResponseDTO;
import cl.duoc.pedidos360.orders.entity.Order;
import cl.duoc.pedidos360.orders.entity.OrderItem;
import cl.duoc.pedidos360.orders.entity.OrderStatus;
import cl.duoc.pedidos360.orders.event.OrderEvent;
import cl.duoc.pedidos360.orders.exception.ForbiddenOrderAccessException;
import cl.duoc.pedidos360.orders.exception.InvalidOrderStatusTransitionException;
import cl.duoc.pedidos360.orders.exception.StockUnavailableException;
import cl.duoc.pedidos360.orders.repository.OrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    private static final OrderRequester CUSTOMER = new OrderRequester("customer-1", "Ana Torres", false);
    private static final OrderRequester OTHER_CUSTOMER = new OrderRequester("customer-2", "Luis Soto", false);
    private static final OrderRequester OPERATOR = new OrderRequester("operator-1", "Operador Uno", true);

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CatalogClient catalogClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderRepository, catalogClient, eventPublisher);
    }

    private Order order(Long id, String customerId, OrderStatus status) {
        Order order = Order.builder()
                .id(id)
                .customerId(customerId)
                .customerName("Ana Torres")
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

        assertThat(response.customerName()).isEqualTo("Ana Torres");
        assertThat(response.status()).isEqualTo(OrderStatus.CREADO);
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productName()).isEqualTo("Teclado");
        assertEvent(null, OrderStatus.CREADO);
    }

    @Test
    void aceptaUnPedidoYDescuentaStockEnCatalogo() {
        Order order = order(1L, CUSTOMER.customerId(), OrderStatus.CREADO);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponseDTO response = orderService.changeStatus(1L, OrderStatus.ACEPTADO, OPERATOR);

        assertThat(response.status()).isEqualTo(OrderStatus.ACEPTADO);
        verify(catalogClient).decreaseStock(10L, 2);
        assertEvent(OrderStatus.CREADO, OrderStatus.ACEPTADO);
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
        verifyNoInteractions(eventPublisher);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void rechazaTransicionInvalidaDeCreadoADespachado() {
        Order order = order(1L, CUSTOMER.customerId(), OrderStatus.CREADO);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.changeStatus(1L, OrderStatus.DESPACHADO, OPERATOR))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
        verify(catalogClient, never()).decreaseStock(any(), anyInt());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void unClienteNoPuedeVerElPedidoDeOtroCliente() {
        Order order = order(1L, CUSTOMER.customerId(), OrderStatus.CREADO);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getById(1L, OTHER_CUSTOMER))
                .isInstanceOf(ForbiddenOrderAccessException.class);
    }

    @Test
    void corregirEntregaConservaDespachoYEliminaFechaDeEntrega() {
        Order order = order(1L, CUSTOMER.customerId(), OrderStatus.ENTREGADO);
        Instant accepted = Instant.parse("2026-01-01T10:00:00Z");
        Instant dispatched = accepted.plusSeconds(3600);
        order.setAcceptedAt(accepted);
        order.setDispatchedAt(dispatched);
        order.setDeliveredAt(dispatched.plusSeconds(3600));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponseDTO response = orderService.changeStatus(1L, OrderStatus.DESPACHADO, OPERATOR);

        assertThat(response.status()).isEqualTo(OrderStatus.DESPACHADO);
        assertThat(order.getDeliveredAt()).isNull();
        assertThat(order.getDispatchedAt()).isEqualTo(dispatched);
        assertThat(order.getAcceptedAt()).isEqualTo(accepted);
        verifyNoInteractions(catalogClient);
    }

    @Test
    void corregirDespachoEliminaFechaDeDespachoYConservaAceptacion() {
        Order order = order(1L, CUSTOMER.customerId(), OrderStatus.DESPACHADO);
        Instant accepted = Instant.parse("2026-01-01T10:00:00Z");
        order.setAcceptedAt(accepted);
        order.setDispatchedAt(accepted.plusSeconds(3600));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.changeStatus(1L, OrderStatus.EN_PREPARACION, OPERATOR);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.EN_PREPARACION);
        assertThat(order.getDispatchedAt()).isNull();
        assertThat(order.getDeliveredAt()).isNull();
        assertThat(order.getAcceptedAt()).isEqualTo(accepted);
        verifyNoInteractions(catalogClient);
    }

    @Test
    void corregirPreparacionConservaFechaDeAceptacionYNoDescuentaStock() {
        Order order = order(1L, CUSTOMER.customerId(), OrderStatus.EN_PREPARACION);
        Instant accepted = Instant.parse("2026-01-01T10:00:00Z");
        order.setAcceptedAt(accepted);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.changeStatus(1L, OrderStatus.ACEPTADO, OPERATOR);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACEPTADO);
        assertThat(order.getAcceptedAt()).isEqualTo(accepted);
        assertEvent(OrderStatus.EN_PREPARACION, OrderStatus.ACEPTADO);
        verifyNoInteractions(catalogClient);
    }

    @Test
    void permiteCorregirYAvanzarDeNuevoSinDuplicarElDescuento() {
        Order order = order(1L, CUSTOMER.customerId(), OrderStatus.CREADO);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.changeStatus(1L, OrderStatus.ACEPTADO, OPERATOR);
        orderService.changeStatus(1L, OrderStatus.EN_PREPARACION, OPERATOR);
        orderService.changeStatus(1L, OrderStatus.DESPACHADO, OPERATOR);
        orderService.changeStatus(1L, OrderStatus.ENTREGADO, OPERATOR);
        orderService.changeStatus(1L, OrderStatus.DESPACHADO, OPERATOR);
        orderService.changeStatus(1L, OrderStatus.EN_PREPARACION, OPERATOR);
        orderService.changeStatus(1L, OrderStatus.ACEPTADO, OPERATOR);
        orderService.changeStatus(1L, OrderStatus.EN_PREPARACION, OPERATOR);
        orderService.changeStatus(1L, OrderStatus.DESPACHADO, OPERATOR);
        orderService.changeStatus(1L, OrderStatus.ENTREGADO, OPERATOR);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ENTREGADO);
        assertThat(order.getAcceptedAt()).isNotNull();
        assertThat(order.getDispatchedAt()).isNotNull();
        assertThat(order.getDeliveredAt()).isNotNull();
        verify(catalogClient, times(1)).decreaseStock(10L, 2);
    }

    @ParameterizedTest
    @CsvSource({"ENTREGADO,ACEPTADO", "DESPACHADO,ACEPTADO", "ACEPTADO,CREADO", "CANCELADO,DESPACHADO", "ENTREGADO,CANCELADO"})
    void rechazaSaltosHaciaAtrasYReactivacion(OrderStatus from, OrderStatus to) {
        Order order = order(1L, CUSTOMER.customerId(), from);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.changeStatus(1L, to, OPERATOR))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);

        assertThat(order.getStatus()).isEqualTo(from);
        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(catalogClient);
    }

    @Test
    void unClienteNoPuedeCorregirEstadosAunqueSeaSuPedido() {
        assertThatThrownBy(() -> orderService.changeStatus(1L, OrderStatus.DESPACHADO, CUSTOMER))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(orderRepository, catalogClient, eventPublisher);
    }

    @Test
    void publishesCancellationAfterSaving() {
        Order order = order(1L, CUSTOMER.customerId(), OrderStatus.CREADO);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(orderService.cancel(1L, CUSTOMER).status()).isEqualTo(OrderStatus.CANCELADO);

        assertEvent(OrderStatus.CREADO, OrderStatus.CANCELADO);
        var inOrder = org.mockito.Mockito.inOrder(orderRepository, eventPublisher);
        inOrder.verify(orderRepository).save(order);
        inOrder.verify(eventPublisher).publishEvent(any(OrderEvent.class));
        verifyNoInteractions(catalogClient);
    }

    @Test
    void doesNotPublishRejectedCancellation() {
        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(order(1L, CUSTOMER.customerId(), OrderStatus.ACEPTADO)));

        assertThatThrownBy(() -> orderService.cancel(1L, CUSTOMER))
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void doesNotPublishWhenSaveFails() {
        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(order(1L, CUSTOMER.customerId(), OrderStatus.CREADO)));
        when(orderRepository.save(any(Order.class))).thenThrow(new IllegalStateException("BD no disponible"));

        assertThatThrownBy(() -> orderService.cancel(1L, CUSTOMER)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(eventPublisher);
    }

    private void assertEvent(OrderStatus previousStatus, OrderStatus status) {
        ArgumentCaptor<OrderEvent> captured = ArgumentCaptor.forClass(OrderEvent.class);
        verify(eventPublisher).publishEvent(captured.capture());
        OrderEvent event = captured.getValue();
        assertThat(event.eventId()).isNotNull();
        assertThat(event.orderId()).isEqualTo(1L);
        assertThat(event.customerId()).isEqualTo(CUSTOMER.customerId());
        assertThat(event.customerName()).isEqualTo(CUSTOMER.customerName());
        assertThat(event.previousStatus()).isEqualTo(previousStatus);
        assertThat(event.status()).isEqualTo(status);
        assertThat(event.totalAmount()).isEqualByComparingTo("100");
        assertThat(event.occurredAt()).isNotNull();
    }
}
