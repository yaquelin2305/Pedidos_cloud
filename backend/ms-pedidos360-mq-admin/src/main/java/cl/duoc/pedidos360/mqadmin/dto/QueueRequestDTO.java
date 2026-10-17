package cl.duoc.pedidos360.mqadmin.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.util.StringUtils;

/**
 * Datos para crear una cola. {@code durable} es verdadero si no se envia.
 */
public record QueueRequestDTO(
        @ResourceName String name,
        Boolean durable,
        @NotNull(message = "es obligatorio: CLASSIC o QUORUM") QueueType type,
        @Size(max = 255, message = "admite hasta 255 caracteres")
        @Pattern(regexp = ResourceName.PATTERN, message = ResourceName.PATTERN_MESSAGE)
        String deadLetterExchange,
        @RoutingKey String deadLetterRoutingKey) {

    public QueueRequestDTO {
        durable = durable == null || durable;
    }

    /** Una cola quorum se replica en disco en cada nodo, por eso RabbitMQ la exige durable. */
    @AssertTrue(message = "una cola QUORUM debe ser durable")
    public boolean isQuorumDurable() {
        return type != QueueType.QUORUM || durable;
    }

    /** La routing key de dead letter no tiene efecto si no hay exchange al que reenviar. */
    @AssertTrue(message = "deadLetterRoutingKey requiere deadLetterExchange")
    public boolean isDeadLetterRoutingKeyWithExchange() {
        return !StringUtils.hasText(deadLetterRoutingKey) || StringUtils.hasText(deadLetterExchange);
    }
}
