package cl.duoc.pedidos360.orders.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declara las dos rutas de pedidos y sus DLQ; todos los nombres proceden de propiedades. */
@Configuration
public class RabbitTopologyConfig {

    /** Todos los cambios de pedido se publican en este exchange topic durable. */
    @Bean
    TopicExchange ordersEventsExchange(MessagingProperties properties) {
        return ExchangeBuilder.topicExchange(properties.exchange()).durable(true).build();
    }

    /** Mismos argumentos que catalog: direct, durable y sin auto-delete. */
    @Bean
    DirectExchange deadLetterExchange(MessagingProperties properties) {
        return ExchangeBuilder.directExchange(properties.deadLetterExchange()).durable(true).build();
    }

    /** Todos los eventos de pedido llegan a notificaciones mediante order.#. Los rechazos y entregas agotadas van a su DLQ. */
    @Bean
    Queue notificationsQueue(MessagingProperties properties) {
        return mainQueue(properties.notifications(), properties);
    }

    /** DLQ quorum durable de notifications, consumida para registrar el fallo y confirmar. */
    @Bean
    Queue notificationsDeadLetterQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.notifications().deadLetterQueue()).quorum().build();
    }

    /** Ruta del exchange topic a notifications, con el patron configurado en YAML. */
    @Bean
    Binding notificationsBinding(@Qualifier("notificationsQueue") Queue queue,
                          TopicExchange ordersEventsExchange, MessagingProperties properties) {
        return BindingBuilder.bind(queue).to(ordersEventsExchange).with(properties.notifications().routingKey());
    }

    /** Ruta del DLX compartido a la DLQ de notifications, usando su nombre como clave. */
    @Bean
    Binding notificationsDeadLetterBinding(@Qualifier("notificationsDeadLetterQueue") Queue queue,
                                    DirectExchange deadLetterExchange, MessagingProperties properties) {
        return BindingBuilder.bind(queue).to(deadLetterExchange).with(properties.notifications().deadLetterQueue());
    }

    /** Los eventos order.accepted llegan a tickets; el consumidor distingue las correcciones. Los rechazos y entregas agotadas van a su DLQ. */
    @Bean
    Queue ticketsQueue(MessagingProperties properties) {
        return mainQueue(properties.tickets(), properties);
    }

    /** DLQ quorum durable de tickets, consumida para registrar el fallo y confirmar. */
    @Bean
    Queue ticketsDeadLetterQueue(MessagingProperties properties) {
        return QueueBuilder.durable(properties.tickets().deadLetterQueue()).quorum().build();
    }

    /** Ruta del exchange topic a tickets, con el patron configurado en YAML. */
    @Bean
    Binding ticketsBinding(@Qualifier("ticketsQueue") Queue queue,
                          TopicExchange ordersEventsExchange, MessagingProperties properties) {
        return BindingBuilder.bind(queue).to(ordersEventsExchange).with(properties.tickets().routingKey());
    }

    /** Ruta del DLX compartido a la DLQ de tickets, usando su nombre como clave. */
    @Bean
    Binding ticketsDeadLetterBinding(@Qualifier("ticketsDeadLetterQueue") Queue queue,
                                    DirectExchange deadLetterExchange, MessagingProperties properties) {
        return BindingBuilder.bind(queue).to(deadLetterExchange).with(properties.tickets().deadLetterQueue());
    }

    private Queue mainQueue(MessagingProperties.Route route, MessagingProperties properties) {
        return QueueBuilder.durable(route.queue()).quorum()
                .deadLetterExchange(properties.deadLetterExchange())
                .deadLetterRoutingKey(route.deadLetterQueue())
                .deliveryLimit(properties.deliveryLimit())
                .build();
    }
}
