package cl.duoc.pedidos360.mqadmin.config;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del administrador, leida desde {@code pedidos360.mq-admin}.
 *
 * @param protectedNames colas y exchanges de la topologia del sistema que no se pueden eliminar
 */
@ConfigurationProperties("pedidos360.mq-admin")
public record MqAdminProperties(Set<String> protectedNames) {

    public MqAdminProperties {
        protectedNames = protectedNames == null ? Set.of() : Set.copyOf(protectedNames);
    }

    public boolean isProtected(String name) {
        return protectedNames.contains(name);
    }
}
