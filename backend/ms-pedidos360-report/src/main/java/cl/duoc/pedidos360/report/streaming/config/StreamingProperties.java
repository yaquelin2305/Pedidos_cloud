package cl.duoc.pedidos360.report.streaming.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros de consumo de Kafka del servicio, leidos desde {@code pedidos360.streaming.*}. Los
 * listeners toman los nombres de topicos de las mismas propiedades; el grupo sale de
 * {@code spring.kafka.consumer.group-id}.
 *
 * @param topics           topicos que consume report
 * @param deadLetterSuffix sufijo de la DLT de cada topico
 * @param concurrency      consumidores por factory dentro del grupo
 * @param retry            reintentos ante errores transitorios
 */
@ConfigurationProperties("pedidos360.streaming")
public record StreamingProperties(Topics topics, String deadLetterSuffix, int concurrency, Retry retry) {

    public record Topics(String orderEvents, String stock) {
    }

    public record Retry(long intervalMs, long maxRetries) {
    }
}
