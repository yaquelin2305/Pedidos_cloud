package cl.duoc.pedidos360.audit.streaming.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cl.duoc.pedidos360.audit.service.AuditService;
import cl.duoc.pedidos360.audit.service.EventOrigin;
import cl.duoc.pedidos360.audit.streaming.message.StockChangedMessage;
import cl.duoc.pedidos360.audit.streaming.support.EventValidator;
import cl.duoc.pedidos360.audit.streaming.support.InvalidEventException;

@ExtendWith(MockitoExtension.class)
class StockListenerTest {

    private static final String TOPIC = "pedidos360.catalog.stock";

    @Mock
    private AuditService auditService;

    private StockListener listener;

    @BeforeEach
    void setUp() {
        EventValidator validator = new EventValidator(Validation.buildDefaultValidatorFactory().getValidator());
        listener = new StockListener(validator, auditService);
    }

    private StockChangedMessage event(Integer stock, Boolean active) {
        return new StockChangedMessage(UUID.randomUUID(), "STOCK_ADJUSTED", 1, "ms-pedidos360-catalog",
                Instant.now(), 7L, "SKU-7", "Mouse", 10, stock, 5, false, active);
    }

    @Test
    void delegaUnEventoValidoConSuOrigen() {
        StockChangedMessage message = event(8, true);

        listener.onStockChanged(message, TOPIC, 2, 3L);

        verify(auditService).recordStockEvent(message, new EventOrigin(TOPIC, 2, 3L));
    }

    @Test
    void rechazaStockNegativoOFaltante() {
        assertThatThrownBy(() -> listener.onStockChanged(event(-1, true), TOPIC, 0, 0L))
                .isInstanceOf(InvalidEventException.class).hasMessageContaining("stock");
        assertThatThrownBy(() -> listener.onStockChanged(event(null, true), TOPIC, 0, 0L))
                .isInstanceOf(InvalidEventException.class).hasMessageContaining("stock");
        verifyNoInteractions(auditService);
    }

    @Test
    void rechazaUnEventoSinActive() {
        assertThatThrownBy(() -> listener.onStockChanged(event(8, null), TOPIC, 0, 0L))
                .isInstanceOf(InvalidEventException.class).hasMessageContaining("active");
        verifyNoInteractions(auditService);
    }

    @Test
    void rechazaUnTombstone() {
        assertThatThrownBy(() -> listener.onStockChanged(null, TOPIC, 0, 0L))
                .isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(auditService);
    }
}
