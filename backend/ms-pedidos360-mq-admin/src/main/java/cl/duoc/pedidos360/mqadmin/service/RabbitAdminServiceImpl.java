package cl.duoc.pedidos360.mqadmin.service;

import java.util.Locale;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ShutdownSignalException;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import cl.duoc.pedidos360.mqadmin.dto.BindingRequestDTO;
import cl.duoc.pedidos360.mqadmin.dto.BindingResponseDTO;
import cl.duoc.pedidos360.mqadmin.dto.ExchangeRequestDTO;
import cl.duoc.pedidos360.mqadmin.dto.ExchangeResponseDTO;
import cl.duoc.pedidos360.mqadmin.dto.QueueRequestDTO;
import cl.duoc.pedidos360.mqadmin.dto.QueueResponseDTO;
import cl.duoc.pedidos360.mqadmin.dto.QueueType;
import cl.duoc.pedidos360.mqadmin.exception.ResourceConflictException;
import cl.duoc.pedidos360.mqadmin.exception.ResourceNotFoundException;

/**
 * Unica clase que habla con el broker. Traduce las peticiones del administrador a declaraciones
 * de Spring AMQP y verifica la existencia de cada recurso antes de modificarlo.
 *
 * <p>{@link AmqpAdmin} informa si una cola existe ({@code getQueueInfo} devuelve null), pero no
 * tiene un equivalente para exchanges: su existencia se comprueba con una declaracion pasiva, que
 * el broker responde con 404 si el exchange no existe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitAdminServiceImpl implements RabbitAdminService {

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate rabbitTemplate;

    @Override
    public QueueResponseDTO createQueue(QueueRequestDTO request) {
        if (amqpAdmin.getQueueInfo(request.name()) != null) {
            throw new ResourceConflictException("La cola ya existe: " + request.name());
        }
        amqpAdmin.declareQueue(toQueue(request));
        log.info("Cola creada: {} ({}, durable={})", request.name(), request.type(), request.durable());
        return getQueue(request.name());
    }

    @Override
    public QueueResponseDTO getQueue(String name) {
        QueueInformation info = requireQueue(name);
        return new QueueResponseDTO(info.getName(), info.getMessageCount(), info.getConsumerCount());
    }

    @Override
    public void deleteQueue(String name) {
        requireQueue(name);
        amqpAdmin.deleteQueue(name);
        log.info("Cola eliminada: {}", name);
    }

    @Override
    public ExchangeResponseDTO createExchange(ExchangeRequestDTO request) {
        if (exchangeExists(request.name())) {
            throw new ResourceConflictException("El exchange ya existe: " + request.name());
        }
        Exchange exchange = new ExchangeBuilder(request.name(), request.type().name().toLowerCase(Locale.ROOT))
                .durable(request.durable())
                .build();
        amqpAdmin.declareExchange(exchange);
        log.info("Exchange creado: {} ({}, durable={})", request.name(), request.type(), request.durable());
        return new ExchangeResponseDTO(request.name(), request.type(), request.durable());
    }

    @Override
    public void deleteExchange(String name) {
        requireExchange(name);
        amqpAdmin.deleteExchange(name);
        log.info("Exchange eliminado: {}", name);
    }

    @Override
    public BindingResponseDTO createBinding(BindingRequestDTO request) {
        requireExchange(request.exchange());
        requireQueue(request.queue());
        amqpAdmin.declareBinding(toBinding(request.exchange(), request.queue(), request.routingKey()));
        log.info("Binding creado: {} -> {} [{}]", request.exchange(), request.queue(), request.routingKey());
        return new BindingResponseDTO(request.exchange(), request.queue(), request.routingKey());
    }

    @Override
    public void deleteBinding(String exchange, String queue, String routingKey) {
        requireExchange(exchange);
        requireQueue(queue);
        amqpAdmin.removeBinding(toBinding(exchange, queue, routingKey));
        log.info("Binding eliminado: {} -> {} [{}]", exchange, queue, routingKey);
    }

    private Queue toQueue(QueueRequestDTO request) {
        QueueBuilder builder = request.durable()
                ? QueueBuilder.durable(request.name())
                : QueueBuilder.nonDurable(request.name());
        if (request.type() == QueueType.QUORUM) {
            builder.quorum();
        }
        if (StringUtils.hasText(request.deadLetterExchange())) {
            builder.deadLetterExchange(request.deadLetterExchange());
        }
        if (StringUtils.hasText(request.deadLetterRoutingKey())) {
            builder.deadLetterRoutingKey(request.deadLetterRoutingKey());
        }
        return builder.build();
    }

    private Binding toBinding(String exchange, String queue, String routingKey) {
        return new Binding(queue, Binding.DestinationType.QUEUE, exchange, routingKey, null);
    }

    private QueueInformation requireQueue(String name) {
        QueueInformation info = amqpAdmin.getQueueInfo(name);
        if (info == null) {
            throw new ResourceNotFoundException("La cola no existe: " + name);
        }
        return info;
    }

    private void requireExchange(String name) {
        if (!exchangeExists(name)) {
            throw new ResourceNotFoundException("El exchange no existe: " + name);
        }
    }

    private boolean exchangeExists(String name) {
        try {
            rabbitTemplate.execute(channel -> channel.exchangeDeclarePassive(name));
            return true;
        } catch (AmqpException ex) {
            if (channelReplyCode(ex) == AMQP.NOT_FOUND) {
                return false;
            }
            throw ex;
        }
    }

    /**
     * Codigo con que el broker cerro el canal (404, 406...), o -1 si el error no vino de un cierre
     * de canal. Spring AMQP envuelve la {@link ShutdownSignalException} original en otras excepciones.
     */
    private static int channelReplyCode(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ShutdownSignalException signal
                    && signal.getReason() instanceof AMQP.Channel.Close close) {
                return close.getReplyCode();
            }
        }
        return -1;
    }
}
