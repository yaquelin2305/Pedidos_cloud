package cl.duoc.pedidos360.orders.messaging.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import cl.duoc.pedidos360.orders.entity.OrderStatus;

/** Unica fuente de nombres y rutas AMQP, leida desde pedidos360.messaging. */
@ConfigurationProperties("pedidos360.messaging")
public record MessagingProperties(String exchange, String deadLetterExchange, int deliveryLimit,
                                  Route notifications, Route tickets, Map<OrderStatus, String> routingKeys) {

    /** Cada DLQ usa su propio nombre como routing key del DLX compartido. */
    public record Route(String queue, String deadLetterQueue, String routingKey) {
    }
}
