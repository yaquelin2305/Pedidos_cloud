# Pedidos360

Sistema de gestión de pedidos con arquitectura cloud native. Monorepo con el frontend y los tres
microservicios de backend.

| Componente | Ruta | Descripción |
|---|---|---|
| Frontend | `frontend/` | React + Vite, autenticación con Azure AD (MSAL) |
| BFF | `backend/ms-pedidos360-bff/` | Spring Boot, valida el JWT y enruta a los microservicios de dominio |
| Orders | `backend/ms-pedidos360-orders/` | Spring Boot, CRUD de pedidos y estados |
| Catalog | `backend/ms-pedidos360-catalog/` | Spring Boot, CRUD de productos y stock |

## Stack

Java 21 + Spring Boot 3.5, React 18 + Vite 5, AWS API Gateway (HTTP API) como API Manager y Azure
Entra como IDaaS.

## Pendiente

El motor de base de datos cloud está por definir con el docente: Oracle Autonomous en OCI, Oracle XE
en contenedor, o PostgreSQL/MySQL en RDS. Hasta resolverlo, `orders` y `catalog` no declaran driver
JDBC.

## Docker Compose

El Compose de la raíz construye frontend, BFF, orders y catalog. Reutiliza los Dockerfile
de los microservicios; no incorpora una base de datos ni otros servicios.

Requiere Docker con Compose, las variables de Azure en `frontend/.env` y las del backend
en `.env` de la raíz, siguiendo `.env.example`. Ambos archivos son locales. La base de
datos debe ser accesible desde los contenedores y tener las tablas creadas: se conserva
`ddl-auto=validate`. Antes de levantar orders/catalog falta incorporar el driver JDBC
del motor que acuerde el equipo.

Desde la raíz:

```sh
docker compose --env-file frontend/.env --env-file .env up --build -d
docker compose --env-file frontend/.env --env-file .env logs -f
docker compose --env-file frontend/.env --env-file .env down
```

El frontend queda en `http://localhost:5173`. Detén el servidor Vite si está usando ese
puerto. En Azure, los retornos locales deben coincidir con
`http://localhost:5173/auth/callback` y `http://localhost:5173/login`.

Nginx sirve el build de React y reenvía `/api/*` y `/me` al BFF. En Compose se fija
`VITE_API_BASE_URL=/` para usar el mismo origen, sin cambiar el `.env` del frontend.
Los microservicios solo son accesibles dentro de la red de Compose; sus URLs internas
usan los nombres `bff`, `orders` y `catalog`, no `localhost`.

Las variables `VITE_` se incorporan durante el build: después de cambiarlas hay que
reconstruir la imagen del frontend. No contienen contraseñas ni secretos de servidor.
`depends_on` ordena el inicio de los contenedores, pero no comprueba que Spring ya esté
listo.
