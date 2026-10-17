# ms-pedidos360-orders

Microservicio de dominio orders de Pedidos360. Expone `/api/orders/*` y persiste sus datos mediante
Spring Data JPA.

Utiliza PostgreSQL mediante el driver JDBC incluido. En el entorno local, Compose levanta la base
de datos junto con los servicios y el clúster RabbitMQ.

## Endpoints

| Método | Ruta | Roles |
|---|---|---|
| GET | `/api/orders` | Admin, Operator, Customer (Customer ve solo sus propios pedidos) |
| GET | `/api/orders/{id}` | Admin, Operator, Customer (Customer solo si es el dueño) |
| POST | `/api/orders` | Customer, Operator, Admin |
| PATCH | `/api/orders/{id}/status` | Operator, Admin |
| DELETE | `/api/orders/{id}` (cancela) | Customer (solo desde CREADO), Operator, Admin (desde CREADO o ACEPTADO) |

## Máquina de estados

```
CREADO -> ACEPTADO <-> EN_PREPARACION <-> DESPACHADO <-> ENTREGADO
CREADO -> CANCELADO
ACEPTADO -> CANCELADO
```

Cualquier otra transición responde 409. Al aceptar un pedido (`ACEPTADO`), este servicio descuenta
stock en `ms-pedidos360-catalog` por cada línea antes de confirmar el cambio; si el catálogo
rechaza el descuento (stock insuficiente), la transición se aborta y el pedido queda en su estado
anterior.

Admin y Operator pueden corregir un estado registrado por error volviendo un paso atrás:
`ENTREGADO → DESPACHADO`, `DESPACHADO → EN_PREPARACION` o `EN_PREPARACION → ACEPTADO`.
Se utiliza el mismo `PATCH /api/orders/{id}/status`. La corrección elimina la fecha del hito
revertido, conserva las anteriores y no modifica el stock. Volver a avanzar registra una nueva
fecha de despacho o entrega. El stock solo se descuenta al pasar de `CREADO` a `ACEPTADO`.

No se permite volver de `ACEPTADO` a `CREADO`, porque la aceptación ya comprometió stock.
Cancelar es una operación separada, mantiene las restricciones por rol y no repone stock
automáticamente. Un pedido `CANCELADO` no se reactiva.

## Integración con catalog

La URL de `ms-pedidos360-catalog` se configura con `CATALOG_SERVICE_URL`. Las llamadas salientes
reenvían el mismo JWT recibido en la petición original, para que catalog aplique su propia
validación de seguridad.

El nombre del cliente (`customerName`) se toma del claim `name` del JWT al crear el pedido y
queda guardado junto al `customerId`, para que el front lo muestre en la tabla de pedidos.

## Cómo levantarlo

Desde la raíz del repositorio, con Docker iniciado y ambos archivos de entorno completos:

```powershell
docker compose --env-file .env --env-file frontend/.env up -d --build orders
docker compose --env-file .env --env-file frontend/.env logs -f orders
```

Esto levanta orders, catalog, PostgreSQL y los dos nodos RabbitMQ. Para iniciar también frontend,
BFF y gateway, omitir `orders` del primer comando. Compose necesita cargar explícitamente
`frontend/.env` para resolver los argumentos `VITE_` de construcción.

También se puede ejecutar con Java 21 y Maven, exportando las variables de entorno:

```
mvn spring-boot:run
```

Maven no carga `.env` automáticamente. El puerto AMQP de los nodos de Compose no se publica al
host: para ejecutar orders fuera de Docker hace falta un broker accesible en `RABBITMQ_ADDRESSES`.

## Variables de entorno

| Variable | Descripción |
|---|---|
| `SERVER_PORT` | Puerto HTTP (por defecto 8081) |
| `DB_URL` | URL JDBC de PostgreSQL (en Compose, el contenedor `postgres`) |
| `DB_USERNAME` | Usuario de la base de datos |
| `DB_PASSWORD` | Password de la base de datos |
| `JPA_DDL_AUTO` | Estrategia de Hibernate DDL (por defecto validate) |
| `JWT_ISSUER_URI` | Issuer del IDaaS (Azure Entra) |
| `JWT_AUDIENCE` | Audience esperado del token |
| `CATALOG_SERVICE_URL` | URL base de ms-pedidos360-catalog (por defecto `http://localhost:8082`) |
| `RABBITMQ_ADDRESSES` | Brokers AMQP; por defecto `localhost:5672`, en Compose los dos nodos internos |
| `RABBITMQ_USERNAME` | Usuario propio de RabbitMQ, distinto de `guest` |
| `RABBITMQ_PASSWORD` | Contraseña del usuario RabbitMQ |
| `RABBITMQ_ERLANG_COOKIE` | Solo para los nodos de Compose: cookie compartida del clúster |

## Mensajería de pedidos

`OrderServiceImpl` emite un `OrderEvent` mediante `ApplicationEventPublisher` después de guardar
en `create`, `changeStatus` y `cancel`. El evento contiene `eventId` (UUID), `orderId`, `customerId`,
`customerName`, `previousStatus`, `status`, `totalAmount` y `occurredAt` (Instant).
`previousStatus` es nulo únicamente al crear. El servicio de negocio no depende de AMQP.

`OrderEventPublisher` escucha con `@TransactionalEventListener(AFTER_COMMIT)`: un rollback no
publica mensajes. El evento se serializa a JSON, con `contentType=application/json` y
`messageId=eventId`. El convertidor JSON se aplica solo a `RabbitTemplate`, no se declara como
bean, para que los listeners reciban el body crudo y puedan rechazar explícitamente JSON inválido.

Todos los nombres y claves se definen en `application.yml`, bajo `pedidos360.messaging`, y se
leen con un único record `MessagingProperties`. Los listeners resuelven las colas desde los beans.

| Estado destino | Routing key |
|---|---|
| CREADO | `order.created` |
| ACEPTADO | `order.accepted` |
| EN_PREPARACION | `order.preparing` |
| DESPACHADO | `order.dispatched` |
| ENTREGADO | `order.delivered` |
| CANCELADO | `order.cancelled` |

Las correcciones publican la clave del estado destino y conservan el estado anterior en el evento.

### Topología

El exchange `pedidos360.orders.events` es `topic` durable. El DLX compartido `pedidos360.dlx`
se declara igual que en catalog: `direct`, durable y sin auto-delete.

| Cola principal | Binding al topic | DLQ y routing key hacia el DLX |
|---|---|---|
| `pedidos360.orders.notifications` | `order.#` | `pedidos360.orders.notifications.dlq` |
| `pedidos360.orders.tickets` | `order.accepted` | `pedidos360.orders.tickets.dlq` |

Las cuatro colas son quorum y durables. Las principales llevan `x-delivery-limit=3`,
`x-dead-letter-exchange=pedidos360.dlx` y `x-dead-letter-routing-key` con el nombre de su DLQ.
Las DLQ tienen bindings al DLX con esa misma clave.

### Consumidores y comprobantes

- `consumer/notification`: registra el pedido, cliente y cambio de estado en el log.
- `consumer/ticket`: delega el comprobante a `OrderTicketGenerator`, que registra id, cliente,
  total y fecha. Solo genera para `CREADO → ACEPTADO`. Una corrección
  `EN_PREPARACION → ACEPTADO` recibe ACK sin generar otro comprobante.
- `consumer/deadletter`: escucha ambas DLQ, registra el fallo en nivel ERROR y confirma el mensaje.

No se envían correos ni se guardan tickets. La exclusión de correcciones no implementa
idempotencia ante reentregas del mismo evento.

### ACK manual y errores

Los listeners reciben `Message`, `Channel` y delivery tag. Dentro de `ManualAckHandler` leen
el JSON con `ObjectMapper`, sin confiar en `__TypeId__`, y validan los campos obligatorios.
Un body `null` también se considera inválido. `previousStatus` puede ser nulo al crear.

| Resultado | Respuesta al broker |
|---|---|
| Procesamiento correcto o corrección sin ticket | `basicAck(tag, false)` |
| JSON inválido, campos obligatorios nulos o `IllegalArgumentException` | WARN y `basicNack(tag, false, false)`: DLQ inmediata |
| `RecoverableProcessingException` u otra `RuntimeException` inesperada | WARN y `basicNack(tag, false, true)`: reintento limitado por la cola quorum |

RabbitMQ envía a la DLQ los mensajes que superan el límite de entrega. Los listeners usan
`acknowledge-mode=manual`, `prefetch=10` y `default-requeue-rejected=false`. El helper y sus
mensajes de log son equivalentes a catalog. Si falla la respuesta por un canal cerrado se registra
el error; el broker puede reentregar los mensajes no confirmados al reconectar.

El consumidor de DLQ registra cola de origen, routing key original, motivo y contador de `x-death`,
contador `x-delivery-count` cuando está disponible y body; después hace ACK. Por ello una DLQ
vacía no implica que no recibió mensajes: la evidencia está en los logs.

El productor utiliza confirmaciones correlacionadas y publicación obligatoria: los NACK del broker
y los mensajes no enrutables se registran en callbacks de `RabbitInfrastructureConfig`.
Una `AmqpException` se registra sin propagarse al HTTP porque el pedido ya quedó confirmado.
No hay reenvío automático de publicaciones fallidas en esta etapa.

### Pruebas y demostración

Ejecutar `mvn test` dentro del servicio. Las pruebas verifican emisión y datos del evento,
transiciones rechazadas, commit y rollback, mapa de destinos, configuración de colas y convertidor,
ACK/NACK, correcciones sin ticket y registro de ambas DLQ.

En la consola `http://localhost:15672`, entrar con el usuario local y abrir el exchange
`pedidos360.orders.events`. Publicar con routing key `order.accepted`, content type
`application/json` y este body de demostración:

```json
{
  "eventId": "0b6f6c1e-8a43-4d5e-9f41-2f8f3f7a1c11",
  "orderId": 990002,
  "customerId": "demo-local",
  "customerName": "Prueba Maria",
  "previousStatus": "CREADO",
  "status": "ACEPTADO",
  "totalAmount": 100,
  "occurredAt": "2026-10-16T20:00:00Z"
}
```

Usar el mismo UUID en la propiedad AMQP `message_id`. Deben aparecer notificación y comprobante.
Repetir con un nuevo UUID y `previousStatus=EN_PREPARACION`: solo debe aparecer la notificación.
Publicar `{invalid-json` y después `null` con la misma routing key: cada mensaje debe registrarse
en ambas DLQ con motivo `rejected`. Son eventos sintéticos, no crean pedidos en PostgreSQL.

Las evidencias de esta verificación se conservan localmente, fuera del repositorio. Para completar la demostración
integrada, iniciar sesión en el frontend con un usuario autorizado, crear y aceptar un pedido real
y comprobar sus logs, además del descuento de stock en catalog.

## Puerto

8081

## Pendiente

No existe compensación automática si el descuento de stock falla a mitad de un pedido con varias
líneas (ver comentario en `OrderServiceImpl.changeStatus`).
