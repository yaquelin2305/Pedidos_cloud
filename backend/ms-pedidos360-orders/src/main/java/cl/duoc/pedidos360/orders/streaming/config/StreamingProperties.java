package cl.duoc.pedidos360.orders.streaming.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Unica fuente de nombres y parametros de Kafka del servicio, leida desde {@code pedidos360.streaming.*}.
 * Ningun publisher ni configuracion escribe nombres de topicos como texto.
 *
 * @param topics topicos que declara orders como dueno
 */
@ConfigurationProperties("pedidos360.streaming")
public record StreamingProperties(Topics topics) {

    /**
     * @param orderEvents           historial de cambios de pedidos
     * @param orderEventsDeadLetter mensajes de pedidos que un consumidor no pudo procesar
     */
    public record Topics(Topic orderEvents, Topic orderEventsDeadLetter) {
    }

    /**
     * @param configs configuraciones a nivel de topico (retencion, cleanup policy, etc.)
     */
    public record Topic(String name, int partitions, int replicas, Map<String, String> configs) {
    }
}
