package cl.duoc.pedidos360.orders.messaging.support;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Deserializa el body de un mensaje de forma explicita con el ObjectMapper de Spring, sin depender
 * del header {@code __TypeId__}: un mensaje publicado a mano desde la consola tambien se puede leer.
 */
@Component
@RequiredArgsConstructor
public class MessagePayloadReader {

    private final ObjectMapper objectMapper;

    public <T> T read(Message message, Class<T> type) {
        try {
            return objectMapper.readValue(message.getBody(), type);
        } catch (IOException ex) {
            throw new InvalidMessageException("JSON invalido para " + type.getSimpleName() + ": "
                    + ex.getMessage());
        }
    }

    public <T> T require(T value, String field) {
        if (value == null) {
            throw new InvalidMessageException("Falta el campo obligatorio " + field);
        }
        return value;
    }
}
