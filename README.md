# Pedidos360

Sistema de gestión de pedidos con arquitectura cloud native. Monorepo con el frontend y los cuatro
microservicios de backend.

| Componente | Ruta | Descripción |
|---|---|---|
| Frontend | `frontend/` | React + Vite, autenticación con Azure AD (MSAL) |
| Gateway | `backend/ms-pedidos360-gateway/` | Spring Cloud Gateway WebFlux, seguridad perimetral y CORS |
| BFF | `backend/ms-pedidos360-bff/` | Spring Boot, valida el JWT y enruta a los microservicios de dominio |
| Orders | `backend/ms-pedidos360-orders/` | Spring Boot, CRUD de pedidos y estados |
| Catalog | `backend/ms-pedidos360-catalog/` | Spring Boot, CRUD de productos y stock |

## Stack

Java 21 + Spring Boot 3.5, React 18 + Vite 5, Spring Cloud 2025.0.3 con Gateway WebFlux como API Manager y Azure
Entra como IDaaS.

## Base de datos

PostgreSQL 16 en un contenedor de Compose, con los datos en el volumen `postgres-data`.
`orders` y `catalog` comparten una sola base (`pedidos360`) — no hay aislamiento de
esquema entre servicios porque sus tablas no tienen nombres en conflicto y la rúbrica no
exige base de datos por servicio.

## Docker Compose

El Compose de la raíz construye frontend, gateway, BFF, orders y catalog, y levanta
PostgreSQL y un clúster RabbitMQ de dos nodos. Todo corre en localhost; Azure Entra es el único
servicio externo.

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
```

Se publican los puertos 5173 (SPA), 8090 (gateway), 5432 (PostgreSQL, para
inspeccionar la base) y 15672 (consola de RabbitMQ, solo en `127.0.0.1`). BFF, orders y catalog solo son accesibles dentro de la red de
Compose. El Bearer y los paths llegan intactos al BFF.

Para desarrollo sin contenedores, levantar cada servicio con Maven, el gateway en 8090
y Vite en 5173 con `VITE_API_BASE_URL=http://localhost:8090`.

Los límites iniciales son gateway/BFF 320 MiB cada uno y orders/catalog 448 MiB cada
uno. Cada JVM limita el heap al 60% de su contenedor; el resto también necesita
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
