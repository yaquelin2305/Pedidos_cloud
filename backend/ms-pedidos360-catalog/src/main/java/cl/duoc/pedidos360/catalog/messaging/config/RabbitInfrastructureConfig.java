package cl.duoc.pedidos360.catalog.messaging.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Infraestructura del {@code RabbitTemplate}: serializacion JSON y callbacks del broker. Ninguna
 * clase de negocio conoce estos detalles.
 */
@Slf4j
@Configuration
public class RabbitInfrastructureConfig {

    /**
     * El {@code RabbitTemplate} publica los eventos como JSON ({@code contentType=application/json})
     * con el ObjectMapper de Spring, que serializa {@code Instant} en ISO-8601. El convertidor se
     * aplica solo al template y no se expone como bean: si los listeners lo usaran, un JSON invalido
     * fallaria antes de llegar a {@code ManualAckHandler} y se saltaria la politica de NACK y DLQ.
     *
     * <p>Con {@code publisher-confirm-type: correlated} el broker confirma cada envio: un NACK significa
     * que no pudo persistir el mensaje. Con {@code mandatory: true}, un mensaje que ningun binding
     * acepta vuelve por el ReturnsCallback en vez de descartarse en silencio.
     */
    @Bean
    RabbitTemplateCustomizer publisherCustomizer(ObjectMapper objectMapper) {
        return template -> {
            template.setMessageConverter(new Jackson2JsonMessageConverter(objectMapper));
            template.setConfirmCallback((correlation, ack, cause) -> {
                if (!ack) {
                    log.error("El broker rechazo el mensaje {}: {}",
                            correlation != null ? correlation.getId() : "sin-id", cause);
                }
            });
            template.setReturnsCallback(returned -> log.error(
                    "Mensaje no enrutable: exchange={} routingKey={} replyCode={} replyText={} messageId={}",
                    returned.getExchange(), returned.getRoutingKey(), returned.getReplyCode(),
                    returned.getReplyText(), returned.getMessage().getMessageProperties().getMessageId()));
        };
    }
}
