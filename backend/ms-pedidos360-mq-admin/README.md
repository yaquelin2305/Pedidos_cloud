# ms-pedidos360-mq-admin

Administrador de RabbitMQ de Pedidos360. Expone una API REST en `/api/mq/*` para crear, consultar y
eliminar colas, exchanges y bindings del clúster. Solo lo usa el rol **Admin** y se consume a través
del gateway (`http://localhost:8090/api/mq/...`).

## Endpoints

| Método | Ruta | Body / params | Respuesta |
|---|---|---|---|
| POST | `/api/mq/queues` | `QueueRequestDTO` | 201 + `QueueResponseDTO` |
| GET | `/api/mq/queues/{name}` | — | 200 con nombre, mensajes y consumidores; 404 si no existe |
| DELETE | `/api/mq/queues/{name}` | — | 204; 404 si no existe; 409 si está protegida |
| POST | `/api/mq/exchanges` | `ExchangeRequestDTO` | 201 + `ExchangeResponseDTO` |
| DELETE | `/api/mq/exchanges/{name}` | — | 204; 404 si no existe; 409 si está protegido |
| POST | `/api/mq/bindings` | `BindingRequestDTO` | 201 + `BindingResponseDTO`; 404 si no existe la cola o el exchange |
| DELETE | `/api/mq/bindings` | query `exchange`, `queue`, `routingKey` | 204; 404 si no existe la cola o el exchange; 409 si es un binding del sistema |

Todas las rutas exigen un JWT válido del IDaaS con el rol `Admin`: sin token responde 401 y con otro
rol, 403. El gateway aplica la misma regla antes de reenviar.

## Arquitectura

Por capas, igual que el resto de los servicios:

| Paquete | Contenido |
|---|---|
| `controller` | `RabbitAdminController`: valida la entrada y delega. No importa nada de Spring AMQP |
| `service` | `RabbitAdminService` y `RabbitAdminServiceImpl`, la única clase que usa `AmqpAdmin` y `RabbitTemplate` |
| `dto` | Requests, responses, enums `QueueType` / `ExchangeType` y las anotaciones `@ResourceName` / `@RoutingKey` |
| `config` | Seguridad JWT y `MqAdminProperties` (nombres protegidos) |
| `exception` | Excepciones propias y `GlobalExceptionHandler` |

La existencia de una cola se consulta con `AmqpAdmin.getQueueInfo`. Para los exchanges no existe un
equivalente, así que el servicio hace una declaración pasiva: el broker responde 404 si no existe.

## Validación de entrada

**`QueueRequestDTO`**
- `name`: obligatorio, hasta 255 caracteres, solo letras, dígitos, `.`, `-` y `_`, y sin el prefijo
  reservado `amq.`.
- `type`: `CLASSIC` o `QUORUM`, obligatorio.
- `durable`: opcional, `true` por defecto.
- `deadLetterExchange` y `deadLetterRoutingKey`: opcionales.
- Una cola `QUORUM` no puede ser no durable.
- No se acepta `deadLetterRoutingKey` sin `deadLetterExchange`.

**`ExchangeRequestDTO`**
- `name`: mismas reglas que la cola.
- `type`: `DIRECT`, `TOPIC`, `FANOUT` o `HEADERS`. Un valor distinto responde 400 indicando los valores
  permitidos.
- `durable`: opcional, `true` por defecto.

**`BindingRequestDTO`**
- `exchange` y `queue`: mismas reglas de nombre.
- `routingKey`: hasta 255 caracteres. Solo admite `*` y `#` como comodines. Vacía por defecto (fanout).

Los nombres de la ruta y de la query (`DELETE /api/mq/bindings`) se validan con las mismas reglas. Los
errores responden 400 con el formato común y listan cada campo con su mensaje:

```json
{"timestamp": "...", "status": 400, "error": "Bad Request",
 "message": "quorumDurable: una cola QUORUM debe ser durable", "path": "/api/mq/queues"}
```

## Errores del broker

| Situación | Excepción | HTTP |
|---|---|---|
| La cola o el exchange no existe | `ResourceNotFoundException` | 404 |
| Ya existe, o el broker responde PRECONDITION_FAILED (mismo nombre con otros argumentos) | `ResourceConflictException` | 409 |
| Recurso protegido | `ProtectedResourceException` | 409 |
| Broker caído, credenciales rechazadas o tiempo agotado | `BrokerUnavailableException` | 503 |

## Nombres protegidos

`pedidos360.mq-admin.protected-names` en `application.yml` lista la topología de orders y catalog:
sus exchanges, colas y DLQ. El administrador no puede eliminar esos recursos ni desligar un binding
entre dos de ellos, porque rompería el flujo de mensajes del sistema. Sí puede agregar bindings de
prueba sobre ellos y eliminarlos después.

## Variables de entorno

| Variable | Descripción |
|---|---|
| `SERVER_PORT` | Puerto HTTP (por defecto 8083) |
| `JWT_ISSUER_URI` | Issuer del tenant de Azure Entra |
| `JWT_AUDIENCE` | Audience de la API |
| `RABBITMQ_ADDRESSES` | Nodos del clúster; `localhost:5672` por defecto, `rabbitmq-1:5672,rabbitmq-2:5672` en Compose |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | Usuario de RabbitMQ (no `guest`) |

## Cómo levantarlo

Con Docker Compose desde la raíz del repositorio (ver README raíz); el servicio no publica puertos y
se accede por el gateway. Para desarrollo local con Maven, con un RabbitMQ en `localhost:5672`:

```sh
mvn spring-boot:run
mvn test
```

## Ejemplos (vía gateway, con un token Admin en `$TOKEN`)

```sh
curl -i -X POST http://localhost:8090/api/mq/queues -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"demo.alerts","type":"QUORUM","deadLetterExchange":"pedidos360.dlx","deadLetterRoutingKey":"demo.alerts.dlq"}'

curl -i http://localhost:8090/api/mq/queues/demo.alerts -H "Authorization: Bearer $TOKEN"

curl -i -X POST http://localhost:8090/api/mq/exchanges -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"name":"demo.events","type":"TOPIC"}'

curl -i -X POST http://localhost:8090/api/mq/bindings -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"exchange":"demo.events","queue":"demo.alerts","routingKey":"demo.#"}'

curl -i -X DELETE "http://localhost:8090/api/mq/bindings?exchange=demo.events&queue=demo.alerts&routingKey=demo.%23" \
  -H "Authorization: Bearer $TOKEN"

curl -i -X DELETE http://localhost:8090/api/mq/queues/pedidos360.orders.tickets -H "Authorization: Bearer $TOKEN"
```

El último ejemplo responde 409, porque la cola es parte de la topología del sistema. En la query, `#`
se escribe `%23`.
