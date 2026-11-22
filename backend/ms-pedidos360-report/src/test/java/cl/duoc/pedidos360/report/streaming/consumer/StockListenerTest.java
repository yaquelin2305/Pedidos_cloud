package cl.duoc.pedidos360.report.streaming.consumer;

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

import cl.duoc.pedidos360.report.service.ReportProjectionService;
import cl.duoc.pedidos360.report.service.EventOrigin;
import cl.duoc.pedidos360.report.streaming.message.StockChangedMessage;
import cl.duoc.pedidos360.report.streaming.support.EventValidator;
import cl.duoc.pedidos360.report.streaming.support.InvalidEventException;

@ExtendWith(MockitoExtension.class)
class StockListenerTest {

    private static final String TOPIC = "pedidos360.catalog.stock";

    @Mock
    private ReportProjectionService reportProjectionService;

    private StockListener listener;

    @BeforeEach
    void setUp() {
        EventValidator validator = new EventValidator(Validation.buildDefaultValidatorFactory().getValidator());
        listener = new StockListener(validator, reportProjectionService);
    }

    private StockChangedMessage event(Integer stock, Boolean active) {
        return new StockChangedMessage(UUID.randomUUID(), "STOCK_ADJUSTED", 3, "ms-pedidos360-catalog",
                Instant.now(), 7L, "SKU-7", "Mouse", 10, stock, 5, false, active);
    }

    @Test
    void delegaUnEventoValidoConSuOrigen() {
        StockChangedMessage message = event(8, true);

        listener.onStockChanged(message, TOPIC, 2, 3L);

        verify(reportProjectionService).recordStockEvent(message, new EventOrigin(TOPIC, 2, 3L));
    }

    @Test
    void rechazaStockNegativoOFaltante() {
        assertThatThrownBy(() -> listener.onStockChanged(event(-1, true), TOPIC, 0, 0L))
                .isInstanceOf(InvalidEventException.class).hasMessageContaining("stock");
        assertThatThrownBy(() -> listener.onStockChanged(event(null, true), TOPIC, 0, 0L))
                .isInstanceOf(InvalidEventException.class).hasMessageContaining("stock");
        verifyNoInteractions(reportProjectionService);
    }

    @Test
    void rechazaUnEventoSinActive() {
        assertThatThrownBy(() -> listener.onStockChanged(event(8, null), TOPIC, 0, 0L))
                .isInstanceOf(InvalidEventException.class).hasMessageContaining("active");
        verifyNoInteractions(reportProjectionService);
    }

    @Test
    void rechazaUnTombstone() {
        assertThatThrownBy(() -> listener.onStockChanged(null, TOPIC, 0, 0L))
                .isInstanceOf(InvalidEventException.class);
        verifyNoInteractions(reportProjectionService);
    }
}
