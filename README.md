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
