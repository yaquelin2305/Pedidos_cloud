# Pedidos360

Sistema de gestión de pedidos con arquitectura cloud native. Monorepo con el frontend y los ocho
servicios de backend.

| Componente | Ruta | Descripción |
|---|---|---|
| Frontend | `frontend/` | React + Vite, autenticación con Azure AD (MSAL) |
| Gateway | `backend/ms-pedidos360-gateway/` | Spring Cloud Gateway WebFlux, seguridad perimetral y CORS |
| BFF | `backend/ms-pedidos360-bff/` | Spring Boot, valida el JWT y enruta a los microservicios de dominio |
| Orders | `backend/ms-pedidos360-orders/` | Spring Boot, CRUD de pedidos y estados |
| Catalog | `backend/ms-pedidos360-catalog/` | Spring Boot, CRUD de productos y stock |
| MQ Admin | `backend/ms-pedidos360-mq-admin/` | Spring Boot, API REST para administrar colas, exchanges y bindings de RabbitMQ |
| Kafka Admin | `backend/ms-pedidos360-kafka-admin/` | Spring Boot, API REST para administrar tópicos, configuraciones, particiones y consumer groups de Kafka |
| Report | `backend/ms-pedidos360-report/` | Spring Boot, consume los eventos de Kafka y expone reportes en tiempo real |
| Audit | `backend/ms-pedidos360-audit/` | Spring Boot, consume los eventos de Kafka y expone la bitácora de auditoría |

## Stack

Java 21 + Spring Boot 3.5, React 18 + Vite 5, Spring Cloud 2025.0.3 con Gateway WebFlux como API Manager y Azure
Entra como IDaaS.

## Base de datos

PostgreSQL 16 en un contenedor de Compose, con los datos en el volumen `postgres-data`.
`orders` y `catalog` comparten una sola base (`pedidos360`) — no hay aislamiento de
esquema entre servicios porque sus tablas no tienen nombres en conflicto y la rúbrica no
exige base de datos por servicio.

## Docker Compose

El Compose de la raíz construye frontend, gateway, BFF, orders, catalog, mq-admin, kafka-admin, report y
audit, y levanta PostgreSQL. Con `include` incorpora la infraestructura de mensajería, que vive en archivos propios:

| Archivo | Servicios |
|---|---|
| `compose.yml` | aplicaciones + PostgreSQL |
| `infra/rabbitmq/compose.yml` | `rabbitmq-1`, `rabbitmq-2` |
| `infra/kafka/compose.yml` | `zookeeper-1..3`, `kafka-1..3`, `kafka-ui` |

`include` fusiona los tres en un solo proyecto, así que el mismo comando levanta todo y los
`depends_on` entre archivos funcionan. Todo corre en localhost; Azure Entra es el único servicio
externo.

Requiere Docker con Compose, las variables de Azure en `frontend/.env` y las del backend
en `.env` de la raíz, siguiendo `.env.example`. Ambos archivos son locales. Compose
configura `JPA_DDL_AUTO=update` para que orders y catalog creen sus tablas al arrancar.

Desde la raíz:

```sh
docker compose --env-file frontend/.env --env-file .env up --build -d
docker compose --env-file frontend/.env --env-file .env logs -f
docker compose --env-file frontend/.env --env-file .env down
```

La SPA queda en `http://localhost:5173` (mismo puerto que Vite, así sirven las redirect
URIs ya registradas en Azure) y el gateway en `http://localhost:8090`.
`VITE_API_BASE_URL` debe ser `http://localhost:8090`, sin sufijo de ruta.

```text
Navegador --> nginx (localhost:5173) --> SPA / MSAL --> Azure Entra
Navegador --> gateway (localhost:8090) (JWT, scope, roles, CORS)
          --> bff:8080 (segunda validación JWT y roles)
          --> orders:8081 / catalog:8082 --> postgres:5432
          --> mq-admin:8083 (/api/mq/**, solo Admin) --> rabbitmq-1 / rabbitmq-2
          --> kafka-admin:8084 (/api/kafka/**, solo Admin) --> kafka-1..3
          --> report:8085 (/api/reports/**, Admin y Operator) --> postgres:5432
          --> audit:8086 (/api/audit/**, solo Admin) --> postgres:5432
orders / catalog --> kafka-1..3 --> report / audit   (eventos de pedidos y de stock)
```

Se publican los puertos 5173 (SPA), 8090 (gateway), 5432 (PostgreSQL, para
inspeccionar la base, solo en `127.0.0.1`), 15672 (consola de RabbitMQ, solo en `127.0.0.1`),
8088 (Kafka UI) y 9092-9094 (brokers Kafka), estos últimos también solo en `127.0.0.1`. BFF, orders,
catalog, mq-admin, kafka-admin, report y audit solo son accesibles dentro de la red de Compose. El Bearer y los paths llegan
intactos al BFF.

Para desarrollo sin contenedores, levantar cada servicio con Maven, el gateway en 8090
y Vite en 5173 con `VITE_API_BASE_URL=http://localhost:8090`.

Los límites iniciales son gateway, BFF, mq-admin y kafka-admin 320 MiB cada uno, y
orders, catalog, report y audit 448 MiB cada uno. Cada JVM limita el heap al 60% de su contenedor; el resto también necesita
memoria nativa.

Las variables `VITE_` se incorporan durante el build: después de cambiarlas hay que
reconstruir la imagen del frontend. No contienen contraseñas ni secretos de servidor.
`depends_on` ordena el inicio de los contenedores, pero no comprueba que Spring ya esté
listo.

## RabbitMQ

Clúster de dos nodos (`rabbit@rabbitmq-1` y `rabbit@rabbitmq-2`, imagen `rabbitmq:4.1-management`)
formado con `classic_config` desde `infra/rabbitmq/rabbitmq.conf`. Ambos nodos comparten la cookie
Erlang `RABBITMQ_ERLANG_COOKIE`; el usuario es `RABBITMQ_USERNAME`/`RABBITMQ_PASSWORD` (no `guest`).
Cada nodo guarda sus datos en su propio volumen. AMQP (5672) queda interno a la red de Compose;
solo se publica la consola de `rabbitmq-1`.

Levantar todo y verificar el clúster:

```sh
docker compose --env-file frontend/.env --env-file .env up --build -d
docker compose --env-file frontend/.env --env-file .env exec rabbitmq-1 rabbitmqctl cluster_status
```

`cluster_status` debe listar los dos nodos en *Running Nodes*. La consola está en
`http://localhost:15672` con las credenciales de `.env`.

El clúster también se puede levantar solo, desde la raíz:

```sh
docker compose --project-directory . --env-file .env -f infra/rabbitmq/compose.yml up -d
```

Las colas son quorum: en la consola, *Queues and Streams* → una cola → *Members* muestra ambos nodos.
También desde la terminal:

```sh
docker compose --env-file frontend/.env --env-file .env exec rabbitmq-1 rabbitmq-queues quorum_status pedidos360.catalog.stock-alerts
```

`classic_config` solo aplica la primera vez que arranca un nodo. Si un nodo quedó fuera del clúster,
borrar sus volúmenes y volver a levantarlos:

```sh
docker compose --env-file frontend/.env --env-file .env rm -sf rabbitmq-1 rabbitmq-2
docker volume rm pedidos_cloud_rabbitmq-1-data pedidos_cloud_rabbitmq-2-data
```

La topología, el flujo de mensajes y la política de errores de cada servicio están en su README.
`ms-pedidos360-mq-admin` permite crear, consultar y eliminar colas, exchanges y bindings a través
del gateway (`/api/mq/**`, rol Admin), sin poder eliminar la topología de orders y catalog.

## Kafka

Clúster en modo Zookeeper definido en `infra/kafka/compose.yml`, con imágenes Confluent Platform
`7.9.11` (Kafka 3.9, la última serie que incluye Zookeeper) y Kafka UI `ghcr.io/kafbat/kafka-ui:v1.5.0`.

| Componente | Rol |
|---|---|
| `zookeeper-1..3` | Ensemble de 3 nodos. Guarda los metadatos del clúster y elige al broker controlador. Funciona mientras haya mayoría (2 de 3): tolera la caída de un nodo |
| `kafka-1..3` | Brokers con IDs 1, 2 y 3. Con replicación 3 y `min.insync.replicas=2`, cada mensaje confirmado queda en al menos 2 brokers y el clúster sigue aceptando escrituras con un broker caído |
| `kafka-ui` | Consola web para administrar brokers, tópicos, particiones, mensajes y consumer groups (offsets y lag) |

Configuración de los brokers que vale para todo el clúster: `default.replication.factor=3`,
`min.insync.replicas=2`, `num.partitions=3`, `unclean.leader.election.enable=false` (nunca se elige
líder a una réplica desactualizada) y `auto.create.topics.enable=false`: un tópico solo existe si lo
declara su servicio dueño o lo crea el administrador. Los tópicos internos de offsets y transacciones
también usan replicación 3.

Cada broker tiene dos listeners: `INTERNAL` (`kafka-N:29092`), que usan los contenedores de Compose, y
`EXTERNAL` (`localhost:9092/9093/9094`), para correr servicios con Maven desde el host.

| Servicio | Puerto en el host | Uso |
|---|---|---|
| `kafka-1` | `127.0.0.1:9092` | bootstrap externo |
| `kafka-2` | `127.0.0.1:9093` | bootstrap externo |
| `kafka-3` | `127.0.0.1:9094` | bootstrap externo |
| `kafka-ui` | `127.0.0.1:8088` | consola web |
| `zookeeper-1..3` | no se publican | 2181 cliente, 2888/3888 quórum |

Variables en `.env` (ver `.env.example`): `KAFKA_UI_USERNAME` y `KAFKA_UI_PASSWORD`, obligatorias,
son las credenciales del formulario de Kafka UI. Los servicios reciben `KAFKA_BOOTSTRAP_SERVERS`
desde Compose (`kafka-1:29092,kafka-2:29092,kafka-3:29092`); con Maven, el valor por defecto es
`localhost:9092,localhost:9093,localhost:9094`.

Levantar todo con el comando de siempre, o solo Kafka:

```sh
docker compose --env-file frontend/.env --env-file .env up --build -d
docker compose --project-directory . --env-file .env -f infra/kafka/compose.yml up -d
```

Kafka UI queda en `http://127.0.0.1:8088`. Los brokers y los Zookeeper tienen healthcheck: un
Zookeeper está *healthy* solo cuando ya forma parte del quórum, y un broker cuando responde a
peticiones. Kafka UI y los servicios que usan Kafka esperan a que los tres brokers estén *healthy*.

Verificar el quórum de Zookeeper (uno debe ser `leader` y dos `follower`) y los 3 brokers:

```sh
docker compose --env-file frontend/.env --env-file .env exec zookeeper-1 sh -c "echo srvr | nc localhost 2181 | grep Mode"
docker compose --env-file frontend/.env --env-file .env exec kafka-1 kafka-broker-api-versions --bootstrap-server localhost:29092
```

Repetir el primer comando con `zookeeper-2` y `zookeeper-3`. El segundo lista `kafka-1`, `kafka-2` y
`kafka-3` con sus IDs. En Kafka UI, *Brokers* muestra los tres.

Memoria: cada broker usa un heap de 256-512 MiB (`mem_limit` 768 MiB), cada Zookeeper 64-128 MiB
(256 MiB) y Kafka UI 512 MiB. Con las aplicaciones, RabbitMQ y PostgreSQL, el stack completo necesita
un equipo de **al menos 16 GB de RAM** (unos 8 GB asignados a Docker), o cerrar otras aplicaciones.

`ms-pedidos360-kafka-admin` permite consultar el clúster, crear, modificar y eliminar tópicos y sus
configuraciones, aumentar particiones e inspeccionar los consumer groups con su lag, a través del
gateway (`/api/kafka/**`, rol Admin). No puede eliminar los cuatro tópicos del sistema ni los
internos de Kafka; ver su README para los endpoints y las validaciones.
