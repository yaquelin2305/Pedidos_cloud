package cl.duoc.pedidos360.mqadmin.service;

import java.util.Locale;
import java.util.function.Supplier;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ShutdownSignalException;

import org.springframework.amqp.AmqpAuthenticationException;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpTimeoutException;
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

import cl.duoc.pedidos360.mqadmin.config.MqAdminProperties;
import cl.duoc.pedidos360.mqadmin.dto.BindingRequestDTO;
import cl.duoc.pedidos360.mqadmin.dto.BindingResponseDTO;
import cl.duoc.pedidos360.mqadmin.dto.ExchangeRequestDTO;
import cl.duoc.pedidos360.mqadmin.dto.ExchangeResponseDTO;
import cl.duoc.pedidos360.mqadmin.dto.QueueRequestDTO;
import cl.duoc.pedidos360.mqadmin.dto.QueueResponseDTO;
import cl.duoc.pedidos360.mqadmin.dto.QueueType;
import cl.duoc.pedidos360.mqadmin.exception.BrokerUnavailableException;
import cl.duoc.pedidos360.mqadmin.exception.ProtectedResourceException;
import cl.duoc.pedidos360.mqadmin.exception.ResourceConflictException;
import cl.duoc.pedidos360.mqadmin.exception.ResourceNotFoundException;

/**
 * Unica clase que habla con el broker. Traduce las peticiones del administrador a declaraciones
 * de Spring AMQP y verifica la existencia de cada recurso antes de modificarlo.
 *
 * <p>{@link AmqpAdmin} informa si una cola existe ({@code getQueueInfo} devuelve null), pero no
 * tiene un equivalente para exchanges: su existencia se comprueba con una declaracion pasiva, que
 * el broker responde con 404 si el exchange no existe.
 *
 * <p>Los nombres de {@link MqAdminProperties#protectedNames()} no se pueden eliminar, y un binding
 * entre dos recursos protegidos tampoco se puede quitar: son las rutas de las que dependen orders y
 * catalog. Si se pueden agregar bindings nuevos sobre ellos, porque no cambian las rutas existentes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitAdminServiceImpl implements RabbitAdminService {

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate rabbitTemplate;
    private final MqAdminProperties properties;

    @Override
    public QueueResponseDTO createQueue(QueueRequestDTO request) {
        return onBroker(() -> {
            if (amqpAdmin.getQueueInfo(request.name()) != null) {
                throw new ResourceConflictException("La cola ya existe: " + request.name());
            }
            amqpAdmin.declareQueue(toQueue(request));
            log.info("Cola creada: {} ({}, durable={})", request.name(), request.type(), request.durable());
            return toResponse(requireQueue(request.name()));
        });
    }

    @Override
    public QueueResponseDTO getQueue(String name) {
        return onBroker(() -> toResponse(requireQueue(name)));
    }

    @Override
    public void deleteQueue(String name) {
        rejectIfProtected(name, "La cola " + name + " es parte de la topologia del sistema");
        onBroker(() -> {
            requireQueue(name);
            amqpAdmin.deleteQueue(name);
            log.info("Cola eliminada: {}", name);
            return null;
        });
    }

    @Override
    public ExchangeResponseDTO createExchange(ExchangeRequestDTO request) {
        return onBroker(() -> {
            if (exchangeExists(request.name())) {
                throw new ResourceConflictException("El exchange ya existe: " + request.name());
            }
            Exchange exchange = new ExchangeBuilder(request.name(), request.type().name().toLowerCase(Locale.ROOT))
                    .durable(request.durable())
                    .build();
            amqpAdmin.declareExchange(exchange);
            log.info("Exchange creado: {} ({}, durable={})", request.name(), request.type(), request.durable());
            return new ExchangeResponseDTO(request.name(), request.type(), request.durable());
        });
    }

    @Override
    public void deleteExchange(String name) {
        rejectIfProtected(name, "El exchange " + name + " es parte de la topologia del sistema");
        onBroker(() -> {
            requireExchange(name);
            amqpAdmin.deleteExchange(name);
            log.info("Exchange eliminado: {}", name);
            return null;
        });
    }

    @Override
    public BindingResponseDTO createBinding(BindingRequestDTO request) {
        return onBroker(() -> {
            requireExchange(request.exchange());
            requireQueue(request.queue());
            amqpAdmin.declareBinding(toBinding(request.exchange(), request.queue(), request.routingKey()));
            log.info("Binding creado: {} -> {} [{}]", request.exchange(), request.queue(), request.routingKey());
            return new BindingResponseDTO(request.exchange(), request.queue(), request.routingKey());
        });
    }

    @Override
    public void deleteBinding(String exchange, String queue, String routingKey) {
        if (properties.isProtected(exchange) && properties.isProtected(queue)) {
            throw new ProtectedResourceException(
                    "El binding " + exchange + " -> " + queue + " es parte de la topologia del sistema");
        }
        onBroker(() -> {
            requireExchange(exchange);
            requireQueue(queue);
            amqpAdmin.removeBinding(toBinding(exchange, queue, routingKey));
            log.info("Binding eliminado: {} -> {} [{}]", exchange, queue, routingKey);
            return null;
        });
    }

    /**
     * Ejecuta una operacion contra el broker y traduce sus errores a excepciones del administrador:
     *
     * <table>
     *   <caption>Traduccion de errores del broker</caption>
     *   <tr><th>Error del broker</th><th>Excepcion</th></tr>
     *   <tr><td>Canal cerrado con 404 NOT_FOUND</td><td>{@link ResourceNotFoundException}</td></tr>
     *   <tr><td>Canal cerrado con 406 PRECONDITION_FAILED: el recurso ya existe con otros
     *       argumentos</td><td>{@link ResourceConflictException}</td></tr>
     *   <tr><td>Sin conexion, autenticacion rechazada o tiempo agotado</td>
     *       <td>{@link BrokerUnavailableException}</td></tr>
     * </table>
     *
     * <p>El cierre de canal se revisa primero porque Spring AMQP tambien lo envuelve en
     * {@link AmqpConnectException}, y no significa que el broker este caido.
     */
    private <T> T onBroker(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (AmqpException ex) {
            AMQP.Channel.Close close = channelClose(ex);
            if (close != null && close.getReplyCode() == AMQP.NOT_FOUND) {
                throw new ResourceNotFoundException(close.getReplyText());
            }
            if (close != null && close.getReplyCode() == AMQP.PRECONDITION_FAILED) {
                throw new ResourceConflictException(close.getReplyText());
            }
            if (ex instanceof AmqpConnectException || ex instanceof AmqpAuthenticationException
                    || ex instanceof AmqpTimeoutException) {
                throw new BrokerUnavailableException("RabbitMQ no esta disponible", ex);
            }
            throw ex;
        }
    }

    private QueueResponseDTO toResponse(QueueInformation info) {
        return new QueueResponseDTO(info.getName(), info.getMessageCount(), info.getConsumerCount());
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

    private void rejectIfProtected(String name, String message) {
        if (properties.isProtected(name)) {
            throw new ProtectedResourceException(message);
        }
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
            AMQP.Channel.Close close = channelClose(ex);
            if (close != null && close.getReplyCode() == AMQP.NOT_FOUND) {
                return false;
            }
            throw ex;
        }
    }

    /**
     * Cierre de canal con que el broker rechazo la operacion (404, 406...), o null si el error no
     * vino de un cierre de canal. Spring AMQP envuelve la {@link ShutdownSignalException} original
     * en otras excepciones.
     */
    private static AMQP.Channel.Close channelClose(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ShutdownSignalException signal
                    && signal.getReason() instanceof AMQP.Channel.Close close) {
                return close;
            }
        }
        return null;
    }
}
