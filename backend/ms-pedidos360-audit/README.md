# ms-pedidos360-audit

Bitácora de auditoría de Pedidos360. Consume **todos** los eventos de negocio de Kafka (pedidos y stock)
y guarda cada uno en `audit_entries`, con quién lo hizo, un resumen legible, el payload completo y su
posición en Kafka (tópico, partición, offset). Expone la bitácora por REST, solo para Admin.

## Consumo de Kafka

| Tópico | DTO | Factory |
|---|---|---|
| `pedidos360.orders.events` | `OrderEventMessage` (+ `OrderLineMessage`) | `orderEventListenerContainerFactory` |
| `pedidos360.catalog.stock` | `StockChangedMessage` | `stockListenerContainerFactory` |

**Consumer group `pedidos360-audit`, concurrencia 1 por tópico.** Es un grupo distinto de
`pedidos360-report`: los dos grupos **comparten** los eventos, cada uno recibe todos los mensajes y
lleva sus propios offsets, sin competir. Un solo consumidor basta porque el volumen es bajo, y sigue
respetando el orden dentro de cada partición. Si se levanta otra instancia del servicio, Kafka
rebalancea las particiones entre ambas.

Estructura (la lógica de dominio no conoce Kafka):

```
streaming/
├── config/     StreamingProperties, KafkaConsumerConfig, KafkaErrorHandlingConfig
├── message/    OrderEventMessage, OrderLineMessage, StockChangedMessage (copia propia del contrato)
├── consumer/   OrderEventsListener, StockListener   ← @KafkaListener: valida y delega
└── support/    EventValidator, InvalidEventException
service/        AuditService (sin imports de Kafka), EventOrigin
repository/     AuditEntryRepository
entity/         AuditEntry, AggregateType
```

### Configuración del cliente

Toda en `application.yml`:

- `spring.kafka.consumer.*`: grupo, `auto-offset-reset: earliest` (un grupo nuevo reconstruye la
  bitácora desde lo retenido), `enable-auto-commit: false`, `max-poll-records: 50`, y
  `ErrorHandlingDeserializer` delegando en `StringDeserializer` (key) y `JsonDeserializer` (value), con
  `spring.json.use.type.headers: false` y `spring.json.trusted.packages` limitado a `streaming.message`.
- `spring.kafka.listener.ack-mode: record`: commit por mensaje procesado o enviado a la DLT.
- `pedidos360.streaming.*` (`StreamingProperties`): tópicos, sufijo de la DLT, concurrencia y reintentos.

`KafkaConsumerConfig` crea una factory por tipo de mensaje: ambas parten de esas propiedades y solo
fijan `spring.json.value.default.type`.

### Validación del payload

`EventValidator` aplica las anotaciones de los DTO: `@NotNull` en `eventId`, `eventType`, `occurredAt`,
el id del agregado, `status` (pedido) y `stock`/`active` (stock); `@PositiveOrZero` en montos y stock;
`@Valid` en las líneas del pedido. Un mensaje vacío (tombstone) también es inválido.

### Política de errores

| Caso | Tratamiento | Destino |
|---|---|---|
| Procesado bien | commit del offset | — |
| JSON ilegible (`DeserializationException`) | sin reintentos | DLT de inmediato |
| Payload inválido (`InvalidEventException`) | sin reintentos | DLT de inmediato |
| Error transitorio (p. ej. base de datos caída) | 3 reintentos, 1 s entre ellos (`FixedBackOff`) | Si persiste, DLT |

- La DLT es `<tópico>.DLT`, en la **misma partición** de origen. Un mensaje ilegible se reenvía con sus
  bytes originales; Kafka agrega headers con el tópico, la partición, el offset y el grupo de origen, y la
  excepción.
- Cada reintento queda en `WARN` y cada envío a la DLT en `ERROR`, con grupo, tópico, partición,
  offset, key y causa.
- Tras enviar a la DLT se confirma el offset: un mensaje defectuoso nunca bloquea la partición.
- **Idempotencia:** `event_id` es único. Un evento ya registrado (re-entrega o reintento) se ignora con
  un `INFO`, sin efectos adversos.

## Bitácora

Tabla `audit_entries` (la crea Hibernate con `JPA_DDL_AUTO=update`):

| Columna | Contenido |
|---|---|
| `event_id` (único) | `eventId` del mensaje |
| `topic`, `kafka_partition`, `kafka_offset` | Dónde está el mensaje en Kafka |
| `event_type` | `ORDER_CREATED`, `ORDER_STATUS_CHANGED`, `ORDER_CANCELLED` o un tipo de cambio de stock |
| `aggregate_type`, `aggregate_id` | `ORDER` + `orderId`, o `PRODUCT` + `productId` |
| `actor_id`, `actor_name` | Quién ejecutó la acción (solo en eventos de pedido) |
| `summary` | Frase legible, p. ej. `Pedido 12: ACEPTADO → EN_PREPARACION por Ana` |
| `payload` | El mensaje completo en JSON |
| `occurred_at`, `received_at` | Cuándo ocurrió el hecho y cuándo llegó a audit |

## Endpoints

Requieren token válido con rol **Admin** (401 sin token o con token inválido, 403 con otro rol).

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/audit/entries?aggregateType=&aggregateId=&page=0&size=20` | Página de la bitácora, del evento más reciente al más antiguo |

- `aggregateType` (opcional): `ORDER` o `PRODUCT`. Otro valor → 400 con los valores permitidos.
- `aggregateId` (opcional): id del pedido o producto.
- `page` ≥ 0; `size` entre 1 y 100 (400 si no). Cada entrada devuelve `payload` como objeto JSON.

## Cómo levantarlo

Con el Compose de la raíz (servicio `audit`, sin puerto publicado; se accede por el gateway):

```sh
docker compose --env-file frontend/.env --env-file .env up --build -d
```

Con Maven, con los brokers y PostgreSQL de Compose arriba:

```sh
mvn spring-boot:run
```

## Variables de entorno

| Variable | Descripción |
|---|---|
| `SERVER_PORT` | Puerto HTTP (por defecto 8086) |
| `DB_URL` | URL JDBC de PostgreSQL |
| `DB_USERNAME` | Usuario de la base de datos |
| `DB_PASSWORD` | Password de la base de datos |
| `JPA_DDL_AUTO` | Estrategia de Hibernate DDL (por defecto validate; Compose usa update) |
| `JWT_ISSUER_URI` | Issuer del IDaaS (Azure Entra) |
| `JWT_AUDIENCE` | Audience esperado del token |
| `KAFKA_BOOTSTRAP_SERVERS` | Brokers Kafka; por defecto `localhost:9092,localhost:9093,localhost:9094` |

## Puerto

8086
