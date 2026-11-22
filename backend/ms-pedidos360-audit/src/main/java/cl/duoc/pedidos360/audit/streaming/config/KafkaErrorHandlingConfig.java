package cl.duoc.pedidos360.audit.streaming.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import cl.duoc.pedidos360.audit.streaming.support.InvalidEventException;

/**
 * Politica de errores de los consumidores:
 * <ul>
 *   <li><b>Procesado bien:</b> se confirma el offset ({@code ack-mode: record}).</li>
 *   <li><b>JSON ilegible</b> ({@code DeserializationException} via {@code ErrorHandlingDeserializer}):
 *       no es reintentable por defecto en {@link DefaultErrorHandler}; va a la DLT de inmediato.</li>
 *   <li><b>Payload invalido</b> ({@link InvalidEventException}): sin reintentos, DLT de inmediato.</li>
 *   <li><b>Error transitorio</b> (p. ej. base de datos caida): reintentos con {@link FixedBackOff}
 *       ({@code pedidos360.streaming.retry}); si persiste, DLT.</li>
 * </ul>
 * La DLT es {@code <topico>.DLT}, en la <b>misma particion</b> de origen. Tras publicar en ella se
 * confirma el offset: un mensaje defectuoso nunca bloquea la particion. Cada reintento queda en WARN y
 * cada envio a la DLT en ERROR con grupo, topico, particion, offset, key y causa.
 */
@Slf4j
@Configuration
public class KafkaErrorHandlingConfig {

    /**
     * Productor de las DLT. Reenvia tal cual los bytes de un mensaje que no se pudo deserializar y
     * serializa como JSON los que si se leyeron.
     */
    @Bean
    KafkaTemplate<Object, Object> deadLetterKafkaTemplate(KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
        Map<Class<?>, Serializer<?>> keySerializers = new LinkedHashMap<>();
        keySerializers.put(byte[].class, new ByteArraySerializer());
        keySerializers.put(String.class, new StringSerializer());
        Map<Class<?>, Serializer<?>> valueSerializers = new LinkedHashMap<>();
        valueSerializers.put(byte[].class, new ByteArraySerializer());
        valueSerializers.put(Object.class, new JsonSerializer<>(objectMapper).noTypeInfo());
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties(),
                new DelegatingByTypeSerializer(keySerializers, true),
                new DelegatingByTypeSerializer(valueSerializers, true)));
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> deadLetterKafkaTemplate,
            StreamingProperties streamingProperties, KafkaProperties kafkaProperties) {
        String groupId = kafkaProperties.getConsumer().getGroupId();
        String suffix = streamingProperties.deadLetterSuffix();
        DeadLetterPublishingRecoverer deadLetterRecoverer = new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + suffix, record.partition()));

        ConsumerRecordRecoverer loggingRecoverer = (record, ex) -> {
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(ex);
            log.error("Mensaje enviado a {}{} [grupo={} topico={} particion={} offset={} key={}]: {}: {}",
                    record.topic(), suffix, groupId, record.topic(), record.partition(), record.offset(),
                    record.key(), cause.getClass().getSimpleName(), cause.getMessage());
            deadLetterRecoverer.accept(record, ex);
        };

        StreamingProperties.Retry retry = streamingProperties.retry();
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(loggingRecoverer,
                new FixedBackOff(retry.intervalMs(), retry.maxRetries()));
        errorHandler.addNotRetryableExceptions(InvalidEventException.class);
        errorHandler.setRetryListeners((ConsumerRecord<?, ?> record, Exception ex, int attempt) -> {
            if (attempt <= retry.maxRetries() && isRetryable(ex)) {
                log.warn("Fallo transitorio, reintento {} de {} [grupo={} topico={} particion={} offset={} key={}]: {}",
                        attempt, retry.maxRetries(), groupId, record.topic(), record.partition(), record.offset(),
                        record.key(), NestedExceptionUtils.getMostSpecificCause(ex).getMessage());
            }
        });
        return errorHandler;
    }

    /** Mismo criterio que el error handler: estos fallos van directo a la DLT y no se anuncian como reintento. */
    private static boolean isRetryable(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidEventException || cause instanceof DeserializationException) {
                return false;
            }
        }
        return true;
    }
}
