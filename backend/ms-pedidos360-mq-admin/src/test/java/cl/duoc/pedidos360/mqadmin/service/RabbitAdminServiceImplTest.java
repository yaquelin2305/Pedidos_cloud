package cl.duoc.pedidos360.mqadmin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.ConnectException;
import java.util.Set;

import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.client.impl.AMQImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import cl.duoc.pedidos360.mqadmin.config.MqAdminProperties;
import cl.duoc.pedidos360.mqadmin.dto.BindingRequestDTO;
import cl.duoc.pedidos360.mqadmin.dto.ExchangeRequestDTO;
import cl.duoc.pedidos360.mqadmin.dto.ExchangeType;
import cl.duoc.pedidos360.mqadmin.dto.QueueRequestDTO;
import cl.duoc.pedidos360.mqadmin.dto.QueueResponseDTO;
import cl.duoc.pedidos360.mqadmin.dto.QueueType;
import cl.duoc.pedidos360.mqadmin.exception.BrokerUnavailableException;
import cl.duoc.pedidos360.mqadmin.exception.ProtectedResourceException;
import cl.duoc.pedidos360.mqadmin.exception.ResourceConflictException;
import cl.duoc.pedidos360.mqadmin.exception.ResourceNotFoundException;

/**
 * Prueba el servicio con {@link AmqpAdmin} y {@link RabbitTemplate} mockeados. Los errores del
 * broker se simulan como los entrega Spring AMQP: un cierre de canal con su codigo AMQP envuelto en
 * una {@link AmqpIOException}.
 */
@ExtendWith(MockitoExtension.class)
class RabbitAdminServiceImplTest {

    private static final String PROTECTED_EXCHANGE = "pedidos360.dlx";
    private static final String PROTECTED_QUEUE = "pedidos360.orders.tickets";

    @Mock
    private AmqpAdmin amqpAdmin;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RabbitAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RabbitAdminServiceImpl(amqpAdmin, rabbitTemplate,
                new MqAdminProperties(Set.of(PROTECTED_EXCHANGE, PROTECTED_QUEUE)));
    }

    private static AmqpIOException channelClosed(int replyCode, String replyText) {
        var close = new AMQImpl.Channel.Close(replyCode, replyText, 40, 10);
        return new AmqpIOException(new IOException(new ShutdownSignalException(false, false, close, null)));
    }

    private void exchangeMissing() {
        when(rabbitTemplate.execute(any(ChannelCallback.class)))
                .thenThrow(channelClosed(404, "NOT_FOUND - no exchange"));
    }

    @Test
    void createsQuorumQueueWithDeadLetterArguments() {
        when(amqpAdmin.getQueueInfo("demo.alerts"))
                .thenReturn(null, new QueueInformation("demo.alerts", 0, 0));

        QueueResponseDTO response = service.createQueue(
                new QueueRequestDTO("demo.alerts", true, QueueType.QUORUM, "demo.dlx", "demo.alerts.dlq"));

        ArgumentCaptor<Queue> declared = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin).declareQueue(declared.capture());
        Queue queue = declared.getValue();
        assertThat(queue.getName()).isEqualTo("demo.alerts");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-queue-type", "quorum")
                .containsEntry("x-dead-letter-exchange", "demo.dlx")
                .containsEntry("x-dead-letter-routing-key", "demo.alerts.dlq");
        assertThat(response).isEqualTo(new QueueResponseDTO("demo.alerts", 0, 0));
    }

    @Test
    void createsClassicQueueWithoutExtraArguments() {
        when(amqpAdmin.getQueueInfo("demo.classic"))
                .thenReturn(null, new QueueInformation("demo.classic", 0, 0));

        service.createQueue(new QueueRequestDTO("demo.classic", false, QueueType.CLASSIC, null, null));

        ArgumentCaptor<Queue> declared = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin).declareQueue(declared.capture());
        assertThat(declared.getValue().isDurable()).isFalse();
        assertThat(declared.getValue().getArguments()).isEmpty();
    }

    @Test
    void rejectsCreatingExistingQueue() {
        when(amqpAdmin.getQueueInfo("demo.alerts")).thenReturn(new QueueInformation("demo.alerts", 3, 1));

        assertThatThrownBy(() -> service.createQueue(
                new QueueRequestDTO("demo.alerts", true, QueueType.QUORUM, null, null)))
                .isInstanceOf(ResourceConflictException.class);
        verify(amqpAdmin, never()).declareQueue(any());
    }

    @Test
    void translatesPreconditionFailedToConflict() {
        when(amqpAdmin.getQueueInfo("demo.alerts")).thenReturn(null);
        when(amqpAdmin.declareQueue(any())).thenThrow(
                channelClosed(406, "PRECONDITION_FAILED - inequivalent arg 'x-queue-type'"));

        assertThatThrownBy(() -> service.createQueue(
                new QueueRequestDTO("demo.alerts", true, QueueType.QUORUM, null, null)))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("PRECONDITION_FAILED");
    }

    @Test
    void translatesConnectionFailureToBrokerUnavailable() {
        when(amqpAdmin.getQueueInfo("demo.alerts"))
                .thenThrow(new AmqpConnectException(new ConnectException("Connection refused")));

        assertThatThrownBy(() -> service.getQueue("demo.alerts"))
                .isInstanceOf(BrokerUnavailableException.class);
    }

    @Test
    void returnsQueueState() {
        when(amqpAdmin.getQueueInfo("demo.alerts")).thenReturn(new QueueInformation("demo.alerts", 7, 2));

        assertThat(service.getQueue("demo.alerts")).isEqualTo(new QueueResponseDTO("demo.alerts", 7, 2));
    }

    @Test
    void reportsMissingQueueAsNotFound() {
        when(amqpAdmin.getQueueInfo("demo.missing")).thenReturn(null);

        assertThatThrownBy(() -> service.deleteQueue("demo.missing"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(amqpAdmin, never()).deleteQueue(any());
    }

    @Test
    void deletesExistingQueue() {
        when(amqpAdmin.getQueueInfo("demo.alerts")).thenReturn(new QueueInformation("demo.alerts", 0, 0));

        service.deleteQueue("demo.alerts");

        verify(amqpAdmin).deleteQueue("demo.alerts");
    }

    @Test
    void rejectsDeletingProtectedQueueWithoutTouchingBroker() {
        assertThatThrownBy(() -> service.deleteQueue(PROTECTED_QUEUE))
                .isInstanceOf(ProtectedResourceException.class);
        verifyNoInteractions(amqpAdmin, rabbitTemplate);
    }

    @Test
    void rejectsDeletingProtectedExchangeWithoutTouchingBroker() {
        assertThatThrownBy(() -> service.deleteExchange(PROTECTED_EXCHANGE))
                .isInstanceOf(ProtectedResourceException.class);
        verifyNoInteractions(amqpAdmin, rabbitTemplate);
    }

    @Test
    void createsExchangeOfRequestedType() {
        exchangeMissing();

        service.createExchange(new ExchangeRequestDTO("demo.events", ExchangeType.TOPIC, true));

        ArgumentCaptor<Exchange> declared = ArgumentCaptor.forClass(Exchange.class);
        verify(amqpAdmin).declareExchange(declared.capture());
        assertThat(declared.getValue().getName()).isEqualTo("demo.events");
        assertThat(declared.getValue().getType()).isEqualTo("topic");
        assertThat(declared.getValue().isDurable()).isTrue();
    }

    @Test
    void rejectsCreatingExistingExchange() {
        assertThatThrownBy(() -> service.createExchange(
                new ExchangeRequestDTO("demo.events", ExchangeType.DIRECT, true)))
                .isInstanceOf(ResourceConflictException.class);
        verify(amqpAdmin, never()).declareExchange(any());
    }

    @Test
    void reportsMissingExchangeAsNotFound() {
        exchangeMissing();

        assertThatThrownBy(() -> service.deleteExchange("demo.missing"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(amqpAdmin, never()).deleteExchange(any());
    }

    @Test
    void createsBindingBetweenExistingResources() {
        when(amqpAdmin.getQueueInfo("demo.alerts")).thenReturn(new QueueInformation("demo.alerts", 0, 0));

        service.createBinding(new BindingRequestDTO("demo.events", "demo.alerts", "demo.#"));

        ArgumentCaptor<Binding> declared = ArgumentCaptor.forClass(Binding.class);
        verify(amqpAdmin).declareBinding(declared.capture());
        assertThat(declared.getValue().getExchange()).isEqualTo("demo.events");
        assertThat(declared.getValue().getDestination()).isEqualTo("demo.alerts");
        assertThat(declared.getValue().getRoutingKey()).isEqualTo("demo.#");
    }

    @Test
    void rejectsBindingToMissingExchange() {
        exchangeMissing();

        assertThatThrownBy(() -> service.createBinding(new BindingRequestDTO("demo.missing", "demo.alerts", "")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(amqpAdmin, never()).declareBinding(any());
    }

    @Test
    void rejectsRemovingSystemBinding() {
        assertThatThrownBy(() -> service.deleteBinding(PROTECTED_EXCHANGE, PROTECTED_QUEUE, PROTECTED_QUEUE))
                .isInstanceOf(ProtectedResourceException.class);
        verifyNoInteractions(amqpAdmin, rabbitTemplate);
    }

    @Test
    void allowsRemovingTestBindingFromProtectedExchange() {
        when(amqpAdmin.getQueueInfo("demo.alerts")).thenReturn(new QueueInformation("demo.alerts", 0, 0));

        service.deleteBinding(PROTECTED_EXCHANGE, "demo.alerts", "demo.alerts");

        verify(amqpAdmin).removeBinding(any());
    }
}
