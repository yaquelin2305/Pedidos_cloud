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

PostgreSQL en Amazon RDS (Learner Lab). `orders` y `catalog` comparten una sola base
(`orders_db`) — no hay aislamiento de esquema entre servicios porque sus tablas no
tienen nombres en conflicto y la rúbrica no exige base de datos por servicio.

## Docker Compose

El Compose de la raíz construye frontend, gateway, BFF, orders y catalog. Reutiliza los Dockerfile
de los microservicios; no incorpora una base de datos ni otros servicios.

Requiere Docker con Compose, las variables de Azure en `frontend/.env` y las del backend
en `.env` de la raíz, siguiendo `.env.example`. Ambos archivos son locales. La base de
datos debe ser accesible desde los contenedores. Compose configura `JPA_DDL_AUTO=update`
para los servicios de dominio.

Desde la raíz:

```sh
docker compose --env-file frontend/.env --env-file .env up --build -d
docker compose --env-file frontend/.env --env-file .env logs -f
docker compose --env-file frontend/.env --env-file .env down
```

El despliegue usa `https://52.207.140.106.nip.io` para la SPA y
`https://api.52.207.140.106.nip.io` para la API. Ambos nombres necesitan el certificado
TLS montado en `/etc/letsencrypt`; el workflow lo crea o amplía al desplegar.
`VITE_API_BASE_URL` debe apuntar al subdominio `api`, sin sufijo de ruta.

```text
Navegador --> nginx:443 (52.207.140.106.nip.io) --> SPA / MSAL --> Azure Entra
Navegador --> nginx:443 (api.52.207.140.106.nip.io)
          --> gateway:8090 (JWT, scope, roles, CORS)
          --> bff:8080 (segunda validación JWT y roles)
          --> orders:8081 / catalog:8082 --> PostgreSQL RDS
```

Solo nginx publica los puertos 80 y 443. El dominio de la SPA no reenvía peticiones
al backend; las rutas `/api/*` y `/me` únicamente se atienden por el gateway en el
subdominio API. El Bearer y los paths llegan intactos al BFF. Los servicios se
comunican por los nombres internos de Compose.

Para desarrollo sin TLS, levantar cada servicio con Maven, el gateway en 8090 y
Vite en 5173; configurar `VITE_API_BASE_URL=http://localhost:8090`.

Los límites iniciales son gateway/BFF 320 MiB cada uno y orders/catalog 448 MiB cada
uno. Cada JVM limita el heap al 60% de su contenedor; el resto también necesita
memoria nativa. Revisar `docker stats` después del despliegue en la EC2 de 2 GiB.

Las variables `VITE_` se incorporan durante el build: después de cambiarlas hay que
reconstruir la imagen del frontend. No contienen contraseñas ni secretos de servidor.
`depends_on` ordena el inicio de los contenedores, pero no comprueba que Spring ya esté
listo.
