package cl.duoc.pedidos360.catalog.messaging.consumer.stock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;

import cl.duoc.pedidos360.catalog.event.StockLowEvent;
import cl.duoc.pedidos360.catalog.messaging.support.ManualAckHandler;
import cl.duoc.pedidos360.catalog.messaging.support.MessagePayloadReader;
import cl.duoc.pedidos360.catalog.messaging.support.RecoverableProcessingException;

/**
 * Verifica la politica de ACK manual con el canal mockeado: el listener nunca toca el broker real,
 * solo se comprueba que respuesta le envia.
 */
@ExtendWith(MockitoExtension.class)
class StockAlertListenerTest {

    private static final long DELIVERY_TAG = 7L;
    private static final String VALID_EVENT = """
            {"eventId":"0b6f6c1e-8a43-4d5e-9f41-2f8f3f7a1c11","productId":1,"sku":"SKU-1",
             "name":"Teclado","stock":4,"threshold":5,"occurredAt":"2026-10-16T20:00:00Z"}
            """;

    @Mock
    private Channel channel;

    @Mock
    private RestockAlertNotifier restockAlertNotifier;

    private StockAlertListener listener;

    @BeforeEach
    void setUp() {
        MessagePayloadReader payloadReader = new MessagePayloadReader(new ObjectMapper().findAndRegisterModules());
        listener = new StockAlertListener(new ManualAckHandler(), payloadReader, restockAlertNotifier);
    }

    private Message message(String body) {
        MessageProperties props = new MessageProperties();
        props.setConsumerQueue("pedidos360.catalog.stock-alerts");
        return new Message(body.getBytes(StandardCharsets.UTF_8), props);
    }

    @Test
    void confirmaElMensajeCuandoLaAlertaSeProcesa() throws Exception {
        listener.onStockLow(message(VALID_EVENT), channel, DELIVERY_TAG);

        verify(restockAlertNotifier).notifyRestock(any(StockLowEvent.class));
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void mandaALaDlqSinRequeueCuandoElJsonEsInvalido() throws Exception {
        listener.onStockLow(message("{esto no es json"), channel, DELIVERY_TAG);

        verify(restockAlertNotifier, never()).notifyRestock(any());
        verify(channel).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void mandaALaDlqSinRequeueCuandoFaltaUnCampoObligatorio() throws Exception {
        listener.onStockLow(message("{\"productId\":1,\"sku\":\"SKU-1\"}"), channel, DELIVERY_TAG);

        verify(restockAlertNotifier, never()).notifyRestock(any());
        verify(channel).basicNack(DELIVERY_TAG, false, false);
    }

    @Test
    void reencolaCuandoElErrorEsRecuperable() throws Exception {
        doThrow(new RecoverableProcessingException("servicio de avisos no disponible", null))
                .when(restockAlertNotifier).notifyRestock(any());

        listener.onStockLow(message(VALID_EVENT), channel, DELIVERY_TAG);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
        verify(channel, never()).basicAck(DELIVERY_TAG, false);
    }
}
