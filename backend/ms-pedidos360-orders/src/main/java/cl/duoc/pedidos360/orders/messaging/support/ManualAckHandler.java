package cl.duoc.pedidos360.orders.messaging.support;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import lombok.extern.slf4j.Slf4j;

/**
 * Politica unica de ACK manual para todos los consumidores del servicio
 * ({@code acknowledge-mode: manual}). Cada listener entrega aqui el procesamiento de un mensaje y
 * este helper decide la respuesta al broker:
 *
 * <table>
 *   <caption>Respuesta segun el resultado</caption>
 *   <tr><th>Resultado</th><th>Respuesta</th><th>Destino del mensaje</th></tr>
 *   <tr><td>Exito</td><td>{@code basicAck}</td><td>Se elimina de la cola</td></tr>
 *   <tr><td>{@link InvalidMessageException} o {@link IllegalArgumentException}: JSON invalido o
 *       campos obligatorios nulos</td>
 *       <td>{@code basicNack(requeue=false)} + log WARN</td><td>DLQ de inmediato</td></tr>
 *   <tr><td>{@link RecoverableProcessingException} u otra {@link RuntimeException}</td>
 *       <td>{@code basicNack(requeue=true)} + log WARN</td>
 *       <td>Reintento; al superar {@code x-delivery-limit} RabbitMQ lo manda a la DLQ</td></tr>
 * </table>
 *
 * <p>Nunca hay requeue infinito (lo corta el delivery limit de la cola quorum), nunca hay ACK
 * automatico y ninguna excepcion se traga sin responder al broker.
 */
@Slf4j
@Component
public class ManualAckHandler {

    private static final String DELIVERY_COUNT_HEADER = "x-delivery-count";

    /** Procesamiento de un mensaje; cualquier excepcion se traduce en NACK. */
    @FunctionalInterface
    public interface MessageProcessor {
        void process();
    }

    public void handle(Message message, Channel channel, long deliveryTag, MessageProcessor processor) {
        MessageProperties props = message.getMessageProperties();
        try {
            processor.process();
        } catch (IllegalArgumentException ex) {
            log.warn("NACK sin requeue: mensaje {} de {} va a la DLQ. Motivo: {}",
                    props.getMessageId(), props.getConsumerQueue(), ex.getMessage());
            reply(channel, deliveryTag, props, () -> channel.basicNack(deliveryTag, false, false));
            return;
        } catch (RuntimeException ex) {
            log.warn("NACK con requeue: mensaje {} de {} se reintentara (entregas previas: {}). Motivo: {}",
                    props.getMessageId(), props.getConsumerQueue(), props.getHeader(DELIVERY_COUNT_HEADER),
                    ex.getMessage());
            reply(channel, deliveryTag, props, () -> channel.basicNack(deliveryTag, false, true));
            return;
        }
        reply(channel, deliveryTag, props, () -> channel.basicAck(deliveryTag, false));
    }

    @FunctionalInterface
    private interface BrokerReply {
        void send() throws IOException;
    }

    /**
     * Si el canal se cerro, la respuesta no llega; el broker reentregara el mensaje al reconectar,
     * por eso solo se registra.
     */
    private void reply(Channel channel, long deliveryTag, MessageProperties props, BrokerReply reply) {
        try {
            reply.send();
        } catch (IOException ex) {
            log.error("No se pudo responder al broker por el mensaje {} (deliveryTag {}) de {}: {}",
                    props.getMessageId(), deliveryTag, props.getConsumerQueue(), ex.getMessage());
        }
    }
}
