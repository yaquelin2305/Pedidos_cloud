# ms-pedidos360-catalog

Microservicio de dominio catalog de Pedidos360. Expone `/api/catalog/*` y persiste sus datos mediante
Spring Data JPA.

El motor de base de datos cloud aún no está definido (duda D5): no se incluye driver JDBC hasta
resolverlo con el docente. El código de entidades y repositorios es agnóstico del motor.

## Endpoints

| Método | Ruta | Rol requerido | Descripción |
|---|---|---|---|
| GET | `/api/catalog/products` | Admin, Operator, Customer | Lista productos activos (paginado) |
| GET | `/api/catalog/products/{id}` | Admin, Operator, Customer | Obtiene un producto |
| POST | `/api/catalog/products` | Admin | Crea un producto |
| PUT | `/api/catalog/products/{id}` | Admin | Actualiza un producto |
| DELETE | `/api/catalog/products/{id}` | Admin | Desactiva un producto (borrado lógico) |
| PATCH | `/api/catalog/products/{id}/stock/decrease` | Cualquier JWT válido | Descuenta stock. Uso interno desde `ms-pedidos360-orders` al aceptar un pedido, no lo invoca el cliente final |

Todas las rutas exigen un JWT válido emitido por el IDaaS (Azure Entra). Documentación interactiva en
`/swagger-ui.html` (público, sin requerir token para explorar el contrato).

## Decisiones de diseño

- **Borrado lógico (`active=false`)** en vez de borrado físico: un producto referenciado por pedidos
  históricos no puede desaparecer de la base de datos sin romper esa trazabilidad.
- **`@Version` (bloqueo optimista)** en `Product`: `ms-pedidos360-orders` descuenta stock al aceptar
  pedidos, y varios pedidos pueden aceptarse casi al mismo tiempo sobre el mismo producto. Sin control de
  concurrencia, dos descuentos simultáneos podrían leer el mismo stock y perder una de las dos
  actualizaciones. Con `@Version`, la segunda transacción en confirmar falla con
  `OptimisticLockException` en vez de corromper el dato.
- **`ddl-auto=validate`** por defecto: en un entorno cloud compartido por el equipo, dejar que Hibernate
  modifique el esquema automáticamente (`update`) es riesgoso — un despliegue puede alterar la estructura
  sin control de versiones. `validate` obliga a que el esquema exista de antemano (migración manual o
  futura herramienta de migraciones) y solo verifica que las entidades coincidan con él.

## Cómo levantarlo

```
mvn spring-boot:run
```

## Variables de entorno

| Variable | Descripción |
|---|---|
| `SERVER_PORT` | Puerto HTTP (por defecto 8082) |
| `DB_URL` | URL JDBC de la base de datos cloud |
| `DB_USERNAME` | Usuario de la base de datos |
| `DB_PASSWORD` | Password de la base de datos |
| `JPA_DDL_AUTO` | Estrategia de Hibernate DDL (por defecto validate) |
| `JWT_ISSUER_URI` | Issuer del IDaaS (Azure Entra) |
| `JWT_AUDIENCE` | Audience esperado del token |

## Puerto

8082
