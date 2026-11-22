package cl.duoc.pedidos360.report.streaming.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cl.duoc.pedidos360.report.service.ReportProjectionService;
import cl.duoc.pedidos360.report.service.EventOrigin;
import cl.duoc.pedidos360.report.streaming.message.OrderEventMessage;
import cl.duoc.pedidos360.report.streaming.message.OrderLineMessage;
import cl.duoc.pedidos360.report.streaming.support.EventValidator;
import cl.duoc.pedidos360.report.streaming.support.InvalidEventException;

@ExtendWith(MockitoExtension.class)
class OrderEventsListenerTest {

    private static final String TOPIC = "pedidos360.orders.events";

    @Mock
    private ReportProjectionService reportProjectionService;

    private OrderEventsListener listener;

    @BeforeEach
    void setUp() {
        EventValidator validator = new EventValidator(Validation.buildDefaultValidatorFactory().getValidator());
        listener = new OrderEventsListener(validator, reportProjectionService);
    }

    private OrderEventMessage event(UUID eventId, BigDecimal totalAmount, int quantity) {
        return new OrderEventMessage(eventId, "ORDER_CREATED", 3, "ms-pedidos360-orders", Instant.now(), 1L,
                "cust-1", "Cliente", "cust-1", "Cliente", null, "CREADO", totalAmount,
                List.of(new OrderLineMessage(3L, "Teclado", quantity, BigDecimal.TEN, BigDecimal.TEN)));
    }

    @Test
    void delegaUnEventoValidoConSuOrigen() {
        OrderEventMessage message = event(UUID.randomUUID(), BigDecimal.TEN, 1);

        listener.onOrderEvent(message, TOPIC, 1, 15L);

        verify(reportProjectionService).recordOrderEvent(message, new EventOrigin(TOPIC, 1, 15L));
    }

    @Test
    void rechazaUnEventoSinEventId() {
        OrderEventMessage message = event(null, BigDecimal.TEN, 1);

        assertThatThrownBy(() -> listener.onOrderEvent(message, TOPIC, 0, 0L))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("eventId");
        verifyNoInteractions(reportProjectionService);
    }

    @Test
    void rechazaMontosNegativosEnElPedidoOEnSusLineas() {
        assertThatThrownBy(() -> listener.onOrderEvent(event(UUID.randomUUID(), BigDecimal.valueOf(-1), 1),
                TOPIC, 0, 0L)).isInstanceOf(InvalidEventException.class).hasMessageContaining("totalAmount");
        assertThatThrownBy(() -> listener.onOrderEvent(event(UUID.randomUUID(), BigDecimal.TEN, -2),
                TOPIC, 0, 0L)).isInstanceOf(InvalidEventException.class).hasMessageContaining("quantity");
        verifyNoInteractions(reportProjectionService);
    }

    @Test
    void rechazaUnMensajeVacio() {
        assertThatThrownBy(() -> listener.onOrderEvent(null, TOPIC, 0, 0L))
                .isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(reportProjectionService);
    }
}
