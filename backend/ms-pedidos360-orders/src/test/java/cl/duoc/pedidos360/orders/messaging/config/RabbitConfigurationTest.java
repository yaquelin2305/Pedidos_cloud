package cl.duoc.pedidos360.orders.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import cl.duoc.pedidos360.orders.entity.OrderStatus;

class RabbitConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> {
                try {
                    new YamlPropertySourceLoader().load("orders", new ClassPathResource("application.yml"))
                            .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            })
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsAllStatusRoutesAndDeclaresCompatibleDurableTopology() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            MessagingProperties properties = context.getBean(MessagingProperties.class);
            assertThat(properties.routingKeys()).containsExactlyInAnyOrderEntriesOf(Map.of(
                    OrderStatus.CREADO, "order.created", OrderStatus.ACEPTADO, "order.accepted",
                    OrderStatus.EN_PREPARACION, "order.preparing", OrderStatus.DESPACHADO, "order.dispatched",
                    OrderStatus.ENTREGADO, "order.delivered", OrderStatus.CANCELADO, "order.cancelled"));
            assertThat(context.getBeansOfType(Queue.class)).hasSize(4);
            assertThat(context.getBeansOfType(Binding.class)).hasSize(4);
            var topic = context.getBean(TopicExchange.class);
            assertThat(topic.getName()).isEqualTo("pedidos360.orders.events");
            assertThat(topic.isDurable()).isTrue();
            assertThat(topic.isAutoDelete()).isFalse();
            var dlx = context.getBean(DirectExchange.class);
            assertThat(dlx.getName()).isEqualTo("pedidos360.dlx");
            assertThat(dlx.isDurable()).isTrue();
            assertThat(dlx.isAutoDelete()).isFalse();
            for (String route : new String[] {"notifications", "tickets"}) {
                Queue queue = context.getBean(route + "Queue", Queue.class);
                Queue dlq = context.getBean(route + "DeadLetterQueue", Queue.class);
                assertThat(queue.isDurable()).isTrue();
                assertThat(dlq.isDurable()).isTrue();
                assertThat(queue.getArguments()).containsEntry("x-queue-type", "quorum")
                        .containsEntry("x-dead-letter-exchange", dlx.getName())
                        .containsEntry("x-dead-letter-routing-key", dlq.getName())
                        .containsEntry("x-delivery-limit", 3);
                assertThat(dlq.getArguments()).containsEntry("x-queue-type", "quorum");
                Binding binding = context.getBean(route + "Binding", Binding.class);
                assertThat(binding.getDestination()).isEqualTo(queue.getName());
                assertThat(binding.getExchange()).isEqualTo(topic.getName());
                assertThat(binding.getRoutingKey()).isEqualTo(route.equals("notifications") ? "order.#" : "order.accepted");
                Binding deadBinding = context.getBean(route + "DeadLetterBinding", Binding.class);
                assertThat(deadBinding.getDestination()).isEqualTo(dlq.getName());
                assertThat(deadBinding.getExchange()).isEqualTo(dlx.getName());
                assertThat(deadBinding.getRoutingKey()).isEqualTo(dlq.getName());
            }
        });
    }

    @Test
    void appliesJsonConverterOnlyToPublisher() {
        contextRunner.run(context -> {
            assertThat(context.getBeansOfType(MessageConverter.class)).isEmpty();
            var template = new RabbitTemplate(mock(ConnectionFactory.class));
            context.getBean(RabbitTemplateCustomizer.class).customize(template);
            var message = template.getMessageConverter().toMessage(Map.of("orderId", 1), new MessageProperties());
            assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MessagingProperties.class)
    @Import({RabbitTopologyConfig.class, RabbitInfrastructureConfig.class})
    static class TestConfig {
        @Bean ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules();
        }
    }
}
