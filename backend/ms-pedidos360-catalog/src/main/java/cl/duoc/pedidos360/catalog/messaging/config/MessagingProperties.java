package cl.duoc.pedidos360.catalog.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Unica fuente de nombres de mensajeria del servicio, leida desde {@code pedidos360.messaging.*}.
 * Ningun listener, publisher ni servicio escribe nombres de colas, exchanges o routing keys
 * como texto: todos se resuelven desde aqui.
 *
 * @param deadLetterExchange exchange direct compartido con orders al que van los mensajes muertos
 * @param deliveryLimit      entregas maximas antes de que RabbitMQ mande el mensaje a su DLQ
 * @param stock              ruta de las alertas de stock bajo
 */
@ConfigurationProperties("pedidos360.messaging")
public record MessagingProperties(String deadLetterExchange, int deliveryLimit, Route stock) {

    /**
     * Exchange, cola, DLQ y routing key de un caso de uso. La DLQ se liga al DLX usando su propio
     * nombre como routing key.
     */
    public record Route(String exchange, String queue, String deadLetterQueue, String routingKey) {
    }
}
