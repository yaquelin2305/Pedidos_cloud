package cl.duoc.pedidos360.catalog.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia de RabbitMQ del catalogo. {@code RabbitAdmin} declara estos beans en el broker al
 * abrir la primera conexion; si ya existen con los mismos argumentos, la declaracion no cambia nada.
 *
 * <p>Ruta de una alerta de stock bajo:
 * <pre>
 * ProductServiceImpl --StockLowEvent--> StockEventPublisher
 *   --[stock.low]--> pedidos360.catalog.events (direct)
 *   --> pedidos360.catalog.stock-alerts (quorum) --> StockAlertListener
 * </pre>
 *
 * <p>Ruta de un mensaje muerto (NACK sin requeue o mas de {@code delivery-limit} entregas):
 * <pre>
 * pedidos360.catalog.stock-alerts
 *   --[pedidos360.catalog.stock-alerts.dlq]--> pedidos360.dlx (direct)
 *   --> pedidos360.catalog.stock-alerts.dlq (quorum) --> DeadLetterListener
 * </pre>
 *
 * <p>Todas las colas son quorum y durables para quedar replicadas en los dos nodos del cluster.
 */
@Configuration
public class RabbitTopologyConfig {

    /** Exchange direct donde catalog publica sus eventos; enruta por coincidencia exacta de routing key. */
    @Bean
    DirectExchange catalogEventsExchange(MessagingProperties properties) {
        return ExchangeBuilder.directExchange(properties.stock().exchange()).durable(true).build();
    }

    /**
     * Dead letter exchange compartido con orders. Ambos servicios lo declaran con los mismos
     * argumentos (direct, durable, sin auto-delete) para que ninguno falle con PRECONDITION_FAILED.
     */
    @Bean
    DirectExchange deadLetterExchange(MessagingProperties properties) {
        return ExchangeBuilder.directExchange(properties.deadLetterExchange()).durable(true).build();
    }

    /**
     * Cola de alertas de reposicion. Los mensajes rechazados sin requeue, o que superan el limite
     * de entregas, se reenvian al DLX con el nombre de la DLQ como routing key.
     */
    @Bean
    Queue stockAlertsQueue(MessagingProperties properties) {
        MessagingProperties.Route stock = properties.stock();
        return QueueBuilder.durable(stock.queue())
                .quorum()
                .deadLetterExchange(properties.deadLetterExchange())
                .deadLetterRoutingKey(stock.deadLetterQueue())
                .deliveryLimit(properties.deliveryLimit())
                .build();
    }

    /** DLQ de las alertas de stock: guarda los mensajes que no se pudieron procesar. */
    @Bean
    Queue stockAlertsDeadLetterQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.stock().deadLetterQueue()).quorum().build();
    }

    /** Liga la cola de alertas al exchange direct con la routing key {@code stock.low}. */
    @Bean
    Binding stockAlertsBinding(Queue stockAlertsQueue, DirectExchange catalogEventsExchange,
                               MessagingProperties properties) {
        return BindingBuilder.bind(stockAlertsQueue).to(catalogEventsExchange)
                .with(properties.stock().routingKey());
    }

    /** Liga la DLQ al DLX usando el nombre de la DLQ como routing key. */
    @Bean
    Binding stockAlertsDeadLetterBinding(Queue stockAlertsDeadLetterQueue, DirectExchange deadLetterExchange,
                                         MessagingProperties properties) {
        return BindingBuilder.bind(stockAlertsDeadLetterQueue).to(deadLetterExchange)
                .with(properties.stock().deadLetterQueue());
    }
}
