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

## Puerto

8082
