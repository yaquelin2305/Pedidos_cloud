package cl.duoc.pedidos360.catalog.streaming.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Unica fuente de nombres y parametros de Kafka del servicio, leida desde {@code pedidos360.streaming.*}.
 * Ningun publisher ni configuracion escribe nombres de topicos como texto.
 *
 * @param topics topicos que declara catalog como dueno
 */
@ConfigurationProperties("pedidos360.streaming")
public record StreamingProperties(Topics topics) {

    /**
     * @param stock           snapshot vigente de cada producto
     * @param stockDeadLetter mensajes de stock que un consumidor no pudo procesar
     */
    public record Topics(Topic stock, Topic stockDeadLetter) {
    }

    /**
     * @param configs configuraciones a nivel de topico (retencion, cleanup policy, etc.)
     */
    public record Topic(String name, int partitions, int replicas, Map<String, String> configs) {
    }
}
