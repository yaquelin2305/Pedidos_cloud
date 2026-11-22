# ms-pedidos360-kafka-admin

Administrador de Kafka de Pedidos360. Expone una API REST en `/api/kafka/*` para consultar el clúster,
crear, modificar y eliminar tópicos y sus configuraciones, aumentar particiones e inspeccionar los
consumer groups con su lag. Solo lo usa el rol **Admin** y se consume a través del gateway
(`http://localhost:8090/api/kafka/...`).

## Endpoints

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| GET | `/api/kafka/cluster` | — | 200: `clusterId`, `controllerId` y brokers (`id`, `host`, `port`) |
| GET | `/api/kafka/topics` | — | 200: lista con `name`, `partitions` y `replicationFactor`, sin los tópicos internos |
| GET | `/api/kafka/topics/{name}` | — | 200: por partición `leader`, `replicas` e `isr`, más las configuraciones; 404 si no existe |
| POST | `/api/kafka/topics` | `TopicCreateRequestDTO` | 201; 400 si es inválido; 409 si ya existe |
| PATCH | `/api/kafka/topics/{name}/configs` | `TopicConfigUpdateRequestDTO` | 200 con las configuraciones resultantes; 400; 404 |
| POST | `/api/kafka/topics/{name}/partitions` | `PartitionIncreaseRequestDTO` | 200; 400 si no supera las actuales; 404 |
| DELETE | `/api/kafka/topics/{name}` | — | 204; 404 si no existe; 409 si es un tópico del sistema |
| GET | `/api/kafka/consumer-groups` | — | 200: `groupId`, `state` y número de miembros |
| GET | `/api/kafka/consumer-groups/{groupId}` | — | 200: miembros, offsets por partición (`committedOffset`, `endOffset`, `lag`) y `totalLag`; 404 |

Todas las rutas exigen un JWT válido del IDaaS con el rol `Admin`: sin token responde 401 y con otro
rol, 403. El gateway aplica la misma regla antes de reenviar.

## Arquitectura

Por capas, igual que el resto de los servicios:

| Paquete | Contenido |
|---|---|
| `controller` | `KafkaAdminController`: valida la entrada y delega. No importa nada de Kafka |
| `service` | `KafkaAdminService` y `KafkaAdminServiceImpl`, la única clase que usa `AdminClient` |
| `dto` | Requests, responses, la regla de las configuraciones (`TopicConfigRules`) y las anotaciones de validación |
| `config` | Seguridad JWT, el bean `AdminClient` y `KafkaAdminProperties` (tópicos protegidos) |
| `exception` | Excepciones propias y `GlobalExceptionHandler` |

El `AdminClient` se crea una sola vez con las propiedades que Spring Boot arma desde `spring.kafka.*`
y se cierra al apagar la aplicación. Sus llamadas son asíncronas: el servicio las espera y traduce los
errores del broker a excepciones propias. Los timeouts están en `application.yml`
(`request.timeout.ms=10000`, `default.api.timeout.ms=15000`): con los brokers caídos la API responde
503 en pocos segundos en vez de quedarse colgada.

## Validación de entrada

**`TopicCreateRequestDTO`**
- `name`: obligatorio, hasta 249 caracteres, solo letras, dígitos, `.`, `-` y `_`, distinto de `.` y
  `..`, y sin el prefijo `_`, que Kafka reserva para sus tópicos internos.
- `partitions`: obligatorio, entre 1 y 12.
- `replicationFactor`: obligatorio, entre 1 y 3. Además no puede superar los brokers disponibles.
- `configs`: opcional. Solo acepta las claves de la lista permitida, con un valor válido para cada una.

**`TopicConfigUpdateRequestDTO`**
- `changes`: al menos un cambio, sin dos cambios sobre la misma clave. Se aplican todos juntos con
  `incrementalAlterConfigs`.
- Cada cambio (`ConfigChangeDTO`) lleva `key`, `operation` y `value`. `operation` es `SET` o `DELETE`;
  `SET` exige `value` y `DELETE` devuelve la configuración al valor por defecto del broker. Un valor
  distinto responde 400 indicando los permitidos.
- `min.insync.replicas` no puede superar el `replicationFactor` del tópico.

Los brokers del clúster fijan `min.insync.replicas=2` por defecto. Un tópico creado con
`replicationFactor` 1 y sin `configs` hereda ese valor, y entonces los productores con `acks=all` no pueden
escribir en él. Para un tópico de una sola réplica hay que enviar `{"min.insync.replicas": "1"}` en `configs`.

**`PartitionIncreaseRequestDTO`**
- `totalCount`: obligatorio, entre 2 y 12, y mayor que las particiones actuales. Kafka no permite
  reducir las particiones de un tópico.

**Configuraciones permitidas**

| Clave | Valor aceptado |
|---|---|
| `retention.ms`, `retention.bytes`, `segment.ms`, `delete.retention.ms`, `max.message.bytes`, `min.insync.replicas` | Entero positivo |
| `cleanup.policy` | `delete`, `compact` o `compact,delete` |
| `min.cleanable.dirty.ratio` | Decimal entre 0 y 1 |

Cualquier otra clave se rechaza. `GET /api/kafka/topics/{name}` devuelve solo estas configuraciones.

El nombre de la ruta se valida con los mismos caracteres que el de creación, pero sin rechazar el
prefijo `_`: así un tópico interno se puede describir y, al intentar eliminarlo, la respuesta es el
409 de recurso protegido y no un 400 de formato. Los errores de validación responden 400 con el
formato común y listan cada campo con su mensaje:

```json
{"timestamp": "...", "status": 400, "error": "Bad Request",
 "message": "name: no puede empezar con '_'", "path": "/api/kafka/topics"}
```

## Errores del broker

| Situación | Excepción | HTTP |
|---|---|---|
| El tópico ya existe | `ResourceConflictException` | 409 |
| El tópico o el consumer group no existe | `ResourceNotFoundException` | 404 |
| Tópico protegido | `ProtectedResourceException` | 409 |
| Particiones, factor de réplicas o configuración rechazados por el broker | `InvalidKafkaRequestException` | 400 |
| Brokers inalcanzables o tiempo agotado | `BrokerUnavailableException` | 503 |
| Cualquier otro error de Kafka (por ejemplo, de autorización) | `KafkaOperationException` | 500, con un mensaje genérico: el detalle del broker solo queda en el log |

Las operaciones que modifican el clúster (crear, eliminar, cambiar configuraciones y aumentar
particiones) quedan registradas en el log a nivel INFO.

Kafka no falla al describir un consumer group que no existe: lo devuelve en estado `DEAD` y sin
miembros. El servicio lo detecta y responde 404.

## Recursos protegidos

`pedidos360.kafka-admin.protected-topics` en `application.yml` lista los cuatro tópicos del sistema:
`pedidos360.orders.events`, `pedidos360.catalog.stock` y sus DLT. Estos tópicos y todos los que
empiezan con `_` (por ejemplo `__consumer_offsets`) **no se pueden eliminar**: de ellos dependen
orders, catalog, report y audit. Sus configuraciones permitidas sí se pueden modificar.

## Lag de un consumer group

`GET /api/kafka/consumer-groups/{groupId}` calcula el lag por partición como
`endOffset − committedOffset`: el último offset escrito por el broker (`listOffsets` con
`OffsetSpec.latest()`) menos el último confirmado por el grupo (`listConsumerGroupOffsets`).
`totalLag` es la suma. Un lag que crece indica que el grupo no alcanza a consumir lo que se produce.

## Variables de entorno

| Variable | Descripción |
|---|---|
| `SERVER_PORT` | Puerto HTTP (por defecto 8084) |
| `JWT_ISSUER_URI` | Issuer del tenant de Azure Entra |
| `JWT_AUDIENCE` | Audience de la API |
| `KAFKA_BOOTSTRAP_SERVERS` | Brokers; `localhost:9092,localhost:9093,localhost:9094` por defecto, `kafka-1:29092,kafka-2:29092,kafka-3:29092` en Compose |

## Cómo levantarlo

Con Docker Compose desde la raíz del repositorio (ver README raíz); el servicio no publica puertos y
se accede por el gateway. Para desarrollo local con Maven, con el clúster Kafka arriba (sus puertos
externos son `127.0.0.1:9092`, `9093` y `9094`):

```sh
mvn spring-boot:run
mvn test
```

## Ejemplos (vía gateway, con un token Admin en `$TOKEN`)

```sh
curl -i http://localhost:8090/api/kafka/cluster -H "Authorization: Bearer $TOKEN"

curl -i http://localhost:8090/api/kafka/topics/pedidos360.orders.events -H "Authorization: Bearer $TOKEN"

curl -i -X POST http://localhost:8090/api/kafka/topics -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"demo.topic","partitions":3,"replicationFactor":3,"configs":{"retention.ms":"86400000"}}'

curl -i -X PATCH http://localhost:8090/api/kafka/topics/demo.topic/configs -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"changes":[{"key":"retention.ms","operation":"SET","value":"3600000"},{"key":"segment.ms","operation":"DELETE"}]}'

curl -i -X POST http://localhost:8090/api/kafka/topics/demo.topic/partitions -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"totalCount":6}'

curl -i http://localhost:8090/api/kafka/consumer-groups/pedidos360-report -H "Authorization: Bearer $TOKEN"

curl -i -X DELETE http://localhost:8090/api/kafka/topics/demo.topic -H "Authorization: Bearer $TOKEN"

curl -i -X DELETE http://localhost:8090/api/kafka/topics/pedidos360.orders.events -H "Authorization: Bearer $TOKEN"
```

El último ejemplo responde 409, porque el tópico es parte del sistema.
