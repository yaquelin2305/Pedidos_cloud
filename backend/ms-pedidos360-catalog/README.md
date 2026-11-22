# ms-pedidos360-catalog

Microservicio de dominio catalog de Pedidos360. Expone `/api/catalog/*`, persiste sus datos en
PostgreSQL mediante Spring Data JPA y publica alertas de stock bajo en RabbitMQ.

## Endpoints

| Método | Ruta | Rol requerido | Descripción |
|---|---|---|---|
| GET | `/api/catalog/products` | Admin, Operator, Customer | Lista productos activos (paginado) |
| GET | `/api/catalog/products/{id}` | Admin, Operator, Customer | Obtiene un producto |
| POST | `/api/catalog/products` | Admin | Crea un producto. `sku` es opcional: si viene vacío, se autogenera |
| PUT | `/api/catalog/products/{id}` | Admin | Actualiza un producto |
| DELETE | `/api/catalog/products/{id}` | Admin | Desactiva un producto (borrado lógico) |
| PATCH | `/api/catalog/products/{id}/stock/decrease` | Cualquier JWT válido | Descuenta stock. Uso interno desde `ms-pedidos360-orders` al aceptar un pedido, no lo invoca el cliente final |
| PATCH | `/api/catalog/products/{id}/stock` | Admin, Operator | Ajusta el stock al valor absoluto recibido, desde la tabla del front |

Todas las rutas exigen un JWT válido emitido por el IDaaS (Azure Entra).

## Decisiones de diseño

- **Borrado lógico (`active=false`)** en vez de borrado físico: un producto referenciado por pedidos
  históricos no puede desaparecer de la base de datos sin romper esa trazabilidad.
- **`@Version` (bloqueo optimista)** en `Product`: `ms-pedidos360-orders` descuenta stock al aceptar
  pedidos, y varios pedidos pueden aceptarse casi al mismo tiempo sobre el mismo producto. Sin control de
  concurrencia, dos descuentos simultáneos podrían leer el mismo stock y perder una de las dos
  actualizaciones. Con `@Version`, la segunda transacción en confirmar falla con
  `OptimisticLockException` en vez de corromper el dato.
- **`ddl-auto=validate`** por defecto: en una base compartida, dejar que Hibernate modifique el esquema
  automáticamente (`update`) es riesgoso — un despliegue puede alterar la estructura sin control de
  versiones. `validate` obliga a que el esquema exista de antemano y solo verifica que las entidades
  coincidan con él. El Compose de la raíz usa `JPA_DDL_AUTO=update` porque levanta un PostgreSQL local y
  vacío, donde las tablas se crean al primer arranque.

## Mensajería (RabbitMQ)

### Topología

Todos los nombres viven en `application.yml` bajo `pedidos360.messaging.*` y se leen con el record
`MessagingProperties`; ninguna clase los escribe como texto. `RabbitTopologyConfig` declara los beans.

| Tipo | Nombre | Detalle |
|---|---|---|
| Exchange direct | `pedidos360.catalog.events` | Recibe las alertas con routing key `stock.low` |
| Exchange direct (DLX) | `pedidos360.dlx` | Compartido con orders, mismos argumentos |
| Cola quorum | `pedidos360.catalog.stock-alerts` | `x-dead-letter-exchange=pedidos360.dlx`, `x-dead-letter-routing-key=pedidos360.catalog.stock-alerts.dlq`, `x-delivery-limit=3` |
| DLQ quorum | `pedidos360.catalog.stock-alerts.dlq` | Ligada al DLX con su propio nombre como routing key |

Las colas son quorum y durables: quedan replicadas en los dos nodos del clúster.

### Flujo

```text
ProductServiceImpl --StockLowEvent (Spring)--> StockEventPublisher (after commit)
  --[stock.low]--> pedidos360.catalog.events --> pedidos360.catalog.stock-alerts
  --> StockAlertListener --> RestockAlertNotifier (log de alerta de reposición)
```

- `ProductServiceImpl` no conoce RabbitMQ: publica `StockLowEvent` con `ApplicationEventPublisher` solo
  cuando el stock **cruza** el umbral `pedidos360.catalog.low-stock-threshold` (5): antes estaba sobre él
  y ahora queda igual o bajo. Aplica a `decreaseStock`, `updateStock` y `update`.
- `StockEventPublisher` envía después del commit, con `messageId` igual a `eventId` y
  `contentType=application/json`. Si el broker falla, lo registra y no lo propaga: el stock ya se guardó.
- El `ConfirmCallback` registra los NACK del broker y el `ReturnsCallback` los mensajes no enrutables
  (`RabbitInfrastructureConfig`).

### Política de ACK y errores

`acknowledge-mode: manual`, `prefetch: 10`. Todos los listeners delegan en `ManualAckHandler`:

| Caso | Respuesta | Destino |
|---|---|---|
| Éxito | `basicAck` | — |
| JSON inválido, campo obligatorio nulo, `IllegalArgumentException` | `basicNack(requeue=false)` + WARN | DLQ de inmediato |
| `RecoverableProcessingException` u otra `RuntimeException` | `basicNack(requeue=true)` + WARN | Reintento; al superar `x-delivery-limit=3` va a la DLQ |

El body se deserializa a mano con `ObjectMapper` (`MessagePayloadReader`), sin depender del header
`__TypeId__`. `DeadLetterListener` registra en ERROR cada mensaje de la DLQ (cola de origen, routing key
original, motivo de `x-death`, intentos y body) y luego lo confirma.

## Streaming (Kafka)

Catalog es **productor** del estado vigente del stock. La integración vive en `streaming/` y no se mezcla
con `messaging/` (RabbitMQ): la alerta de stock bajo por RabbitMQ sigue igual.

### Tópicos que declara

`KafkaTopicConfig` los crea al arrancar con beans `NewTopic`; los valores salen de
`pedidos360.streaming.topics.*` en `application.yml`.

| Tópico | Particiones | RF | `min.insync.replicas` | `cleanup.policy` | Otros |
|---|---|---|---|---|---|
| `pedidos360.catalog.stock` | 3 | 3 | 2 | `compact` | `segment.ms=3600000`, `min.cleanable.dirty.ratio=0.1` |
| `pedidos360.catalog.stock.DLT` | 3 | 3 | 2 | `delete` | `retention.ms=1209600000` (14 días) |

- **Compactado:** el tópico representa el estado vigente de cada producto y solo importa el último
  valor por key. La compactación conserva el último snapshot de cada producto indefinidamente, así un
  consumidor nuevo reconstruye el inventario completo leyendo desde el inicio. `segment.ms` y
  `min.cleanable.dirty.ratio` bajos hacen que la compactación se pueda observar en Kafka UI.
- **Key = `productId`:** todos los cambios de un producto caen en la misma partición y conservan su
  orden; además es la key por la que se compacta.
- **3 particiones:** hasta 3 consumidores en paralelo por grupo.
- **RF 3 + `min.insync.replicas=2` + `acks=all`:** cada mensaje confirmado está en al menos 2 brokers y
  se sigue escribiendo con un broker caído. Con dos caídos el envío falla: se prefiere no aceptar datos
  a perderlos.
- **DLT:** la usan los consumidores para los mensajes que no pueden procesar. El recoverer publica en la
  misma partición de origen, por eso tiene 3 particiones; 14 días dan margen para investigar.

### Flujo

1. `ProductServiceImpl` publica el evento de dominio `StockChangedEvent` (en `event/`, sin dependencias
   de Kafka) al final de cada operación que cambia el producto:

   | Operación | `eventType` | `previousStock` |
   |---|---|---|
   | `create` | `PRODUCT_CREATED` | `null` |
   | `update` | `PRODUCT_UPDATED` | stock anterior |
   | `updateStock` | `STOCK_ADJUSTED` | stock anterior |
   | `decreaseStock` (al aceptar un pedido) | `STOCK_DECREASED` | stock anterior |
   | `deactivate` | `PRODUCT_DEACTIVATED` | stock vigente |

2. `StockChangedKafkaPublisher` lo escucha **después del commit** (si la transacción se revierte no se
   publica nada), lo convierte con `StockChangedMessageMapper` y lo envía de forma asíncrona con
   `KafkaTemplate.send(topic, productId, message)`.
3. El resultado se registra en el callback: `INFO` con tópico, partición, offset, key y `eventId`, o
   `ERROR` con la causa. El publicador nunca propaga excepciones: el cambio ya está guardado y un fallo
   de Kafka no debe convertirse en un error HTTP ni impedir el publicador de RabbitMQ.

### Mensaje `StockChangedMessage`

| Campo | Para qué |
|---|---|
| `eventId` | Identidad única; los consumidores descartan duplicados |
| `eventType` | Qué cambió (tabla de arriba) |
| `schemaVersion` (`1`) | Evolucionar el contrato sin romper consumidores |
| `source` (`ms-pedidos360-catalog`) | Origen, para auditoría |
| `occurredAt` | Momento del cambio (ISO-8601) |
| `productId` | Agregado; también es la key |
| `sku`, `name` | Identificar el producto sin consultar catalog |
| `previousStock` | Variación del stock (`null` al crear) |
| `stock` | Valor vigente, lo que conserva la compactación |
| `lowStockThreshold` | El consumidor aplica la misma regla de stock bajo que catalog |
| `lowStock` | `stock <= lowStockThreshold`, lectura directa para alertas |
| `active` | Un producto desactivado sale del inventario vigente |

### Configuración del productor

En `spring.kafka.producer.*`: key `StringSerializer` (legible en Kafka UI), value `JsonSerializer`
construido con el `ObjectMapper` de Spring Boot (`KafkaProducerConfig`), `acks=all`,
`enable.idempotence=true` (los reintentos no duplican), `compression.type=lz4`, `linger.ms=10`,
`delivery.timeout.ms=30000`, `request.timeout.ms=15000` (Kafka exige que `delivery.timeout.ms` sea al
menos `linger.ms + request.timeout.ms`), `max.block.ms=5000` (con los brokers caídos el envío no bloquea
el hilo HTTP más de 5 s) y `spring.json.add.type.headers=false` (los consumidores no dependen de nombres
de clase de catalog).

## Cómo levantarlo

```
mvn spring-boot:run
```

## Variables de entorno

| Variable | Descripción |
|---|---|
| `SERVER_PORT` | Puerto HTTP (por defecto 8082) |
| `DB_URL` | URL JDBC de PostgreSQL (en Compose, el contenedor `postgres`) |
| `DB_USERNAME` | Usuario de la base de datos |
| `DB_PASSWORD` | Password de la base de datos |
| `JPA_DDL_AUTO` | Estrategia de Hibernate DDL (por defecto validate) |
| `JWT_ISSUER_URI` | Issuer del IDaaS (Azure Entra) |
| `JWT_AUDIENCE` | Audience esperado del token |
| `RABBITMQ_ADDRESSES` | Nodos del clúster; por defecto `localhost:5672` |
| `RABBITMQ_USERNAME` | Usuario de RabbitMQ |
| `RABBITMQ_PASSWORD` | Password de RabbitMQ |
| `KAFKA_BOOTSTRAP_SERVERS` | Brokers Kafka; por defecto `localhost:9092,localhost:9093,localhost:9094` |

## Puerto

8082
