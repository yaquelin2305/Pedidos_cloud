package cl.duoc.pedidos360.mqadmin.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import cl.duoc.pedidos360.mqadmin.dto.BindingRequestDTO;
import cl.duoc.pedidos360.mqadmin.dto.BindingResponseDTO;
import cl.duoc.pedidos360.mqadmin.dto.ExchangeRequestDTO;
import cl.duoc.pedidos360.mqadmin.dto.ExchangeResponseDTO;
import cl.duoc.pedidos360.mqadmin.dto.QueueRequestDTO;
import cl.duoc.pedidos360.mqadmin.dto.QueueResponseDTO;
import cl.duoc.pedidos360.mqadmin.dto.ResourceName;
import cl.duoc.pedidos360.mqadmin.dto.RoutingKey;
import cl.duoc.pedidos360.mqadmin.service.RabbitAdminService;

/**
 * API REST de administracion de RabbitMQ. Solo valida la entrada y delega en
 * {@link RabbitAdminService}; no conoce las clases de Spring AMQP. Los cuerpos se validan con
 * {@code @Valid} y los nombres de la ruta y de la query con las mismas reglas que los DTOs.
 */
@RestController
@RequestMapping("/api/mq")
@PreAuthorize("hasRole('Admin')")
@RequiredArgsConstructor
public class RabbitAdminController {

    private final RabbitAdminService rabbitAdminService;

    @PostMapping("/queues")
    public ResponseEntity<QueueResponseDTO> createQueue(@Valid @RequestBody QueueRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rabbitAdminService.createQueue(request));
    }

    @GetMapping("/queues/{name}")
    public ResponseEntity<QueueResponseDTO> getQueue(@PathVariable @ResourceName String name) {
        return ResponseEntity.ok(rabbitAdminService.getQueue(name));
    }

    @DeleteMapping("/queues/{name}")
    public ResponseEntity<Void> deleteQueue(@PathVariable @ResourceName String name) {
        rabbitAdminService.deleteQueue(name);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/exchanges")
    public ResponseEntity<ExchangeResponseDTO> createExchange(@Valid @RequestBody ExchangeRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rabbitAdminService.createExchange(request));
    }

    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<Void> deleteExchange(@PathVariable @ResourceName String name) {
        rabbitAdminService.deleteExchange(name);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bindings")
    public ResponseEntity<BindingResponseDTO> createBinding(@Valid @RequestBody BindingRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rabbitAdminService.createBinding(request));
    }

    @DeleteMapping("/bindings")
    public ResponseEntity<Void> deleteBinding(@RequestParam @ResourceName String exchange,
            @RequestParam @ResourceName String queue,
            @RequestParam(defaultValue = "") @RoutingKey String routingKey) {
        rabbitAdminService.deleteBinding(exchange, queue, routingKey);
        return ResponseEntity.noContent().build();
    }
}
