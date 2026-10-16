# ms-pedidos360-orders

Microservicio de dominio orders de Pedidos360. Expone `/api/orders/*` y persiste sus datos mediante
Spring Data JPA.

El motor de base de datos cloud aún no está definido (duda D5): no se incluye driver JDBC hasta
resolverlo con el docente.

## Endpoints

| Método | Ruta | Roles |
|---|---|---|
| GET | `/api/orders` | Admin, Operator, Customer (Customer ve solo sus propios pedidos) |
| GET | `/api/orders/{id}` | Admin, Operator, Customer (Customer solo si es el dueño) |
| POST | `/api/orders` | Customer, Operator, Admin |
| PATCH | `/api/orders/{id}/status` | Operator, Admin |
| DELETE | `/api/orders/{id}` (cancela) | Customer (solo desde CREADO), Operator, Admin (desde CREADO o ACEPTADO) |

## Máquina de estados

```
CREADO -> ACEPTADO <-> EN_PREPARACION <-> DESPACHADO <-> ENTREGADO
CREADO -> CANCELADO
ACEPTADO -> CANCELADO
```

Cualquier otra transición responde 409. Al aceptar un pedido (`ACEPTADO`), este servicio descuenta
stock en `ms-pedidos360-catalog` por cada línea antes de confirmar el cambio; si el catálogo
rechaza el descuento (stock insuficiente), la transición se aborta y el pedido queda en su estado
anterior.

Admin y Operator pueden corregir un estado registrado por error volviendo un paso atrás:
`ENTREGADO → DESPACHADO`, `DESPACHADO → EN_PREPARACION` o `EN_PREPARACION → ACEPTADO`.
Se utiliza el mismo `PATCH /api/orders/{id}/status`. La corrección elimina la fecha del hito
revertido, conserva las anteriores y no modifica el stock. Volver a avanzar registra una nueva
fecha de despacho o entrega. El stock solo se descuenta al pasar de `CREADO` a `ACEPTADO`.

No se permite volver de `ACEPTADO` a `CREADO`, porque la aceptación ya comprometió stock.
Cancelar es una operación separada, mantiene las restricciones por rol y no repone stock
automáticamente. Un pedido `CANCELADO` no se reactiva.

## Integración con catalog

La URL de `ms-pedidos360-catalog` se configura con `CATALOG_SERVICE_URL`. Las llamadas salientes
reenvían el mismo JWT recibido en la petición original, para que catalog aplique su propia
validación de seguridad.

El nombre del cliente (`customerName`) se toma del claim `name` del JWT al crear el pedido y
queda guardado junto al `customerId`, para que el front lo muestre en la tabla de pedidos.

## Cómo levantarlo

```
mvn spring-boot:run
```

## Variables de entorno

| Variable | Descripción |
|---|---|
| `SERVER_PORT` | Puerto HTTP (por defecto 8081) |
| `DB_URL` | URL JDBC de PostgreSQL (en Compose, el contenedor `postgres`) |
| `DB_USERNAME` | Usuario de la base de datos |
| `DB_PASSWORD` | Password de la base de datos |
| `JPA_DDL_AUTO` | Estrategia de Hibernate DDL (por defecto validate) |
| `JWT_ISSUER_URI` | Issuer del IDaaS (Azure Entra) |
| `JWT_AUDIENCE` | Audience esperado del token |
| `CATALOG_SERVICE_URL` | URL base de ms-pedidos360-catalog (por defecto `http://localhost:8082`) |

## Puerto

8081

## Pendiente

No existe compensación automática si el descuento de stock falla a mitad de un pedido con varias
líneas (ver comentario en `OrderServiceImpl.changeStatus`).
