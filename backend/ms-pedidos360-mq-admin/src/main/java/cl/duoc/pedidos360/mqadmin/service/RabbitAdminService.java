package cl.duoc.pedidos360.mqadmin.service;

import cl.duoc.pedidos360.mqadmin.dto.BindingRequestDTO;
import cl.duoc.pedidos360.mqadmin.dto.BindingResponseDTO;
import cl.duoc.pedidos360.mqadmin.dto.ExchangeRequestDTO;
import cl.duoc.pedidos360.mqadmin.dto.ExchangeResponseDTO;
import cl.duoc.pedidos360.mqadmin.dto.QueueRequestDTO;
import cl.duoc.pedidos360.mqadmin.dto.QueueResponseDTO;

/**
 * Operaciones de administracion de RabbitMQ expresadas en terminos del dominio del administrador.
 * Quien la usa no conoce las clases de Spring AMQP.
 */
public interface RabbitAdminService {

    QueueResponseDTO createQueue(QueueRequestDTO request);

    QueueResponseDTO getQueue(String name);

    void deleteQueue(String name);

    ExchangeResponseDTO createExchange(ExchangeRequestDTO request);

    void deleteExchange(String name);

    BindingResponseDTO createBinding(BindingRequestDTO request);

    void deleteBinding(String exchange, String queue, String routingKey);
}
