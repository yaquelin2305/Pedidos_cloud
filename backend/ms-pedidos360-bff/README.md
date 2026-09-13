# ms-pedidos360-bff

Backend for Frontend de Pedidos360. Valida el JWT emitido por el IDaaS (firma, issuer, audience y
vigencia), expone los claims del usuario y enruta las peticiones hacia `ms-pedidos360-orders` y
`ms-pedidos360-catalog`.

## Estado

- Capa de seguridad completa: validación de JWT, mapeo de roles y scopes a authorities,
  respuestas 401 (token ausente o inválido) frente a 403 (rol insuficiente).
- Endpoint `GET /me`: devuelve `subject`, `name`, `roles` y `scopes` del token validado.
- Routing de pedidos y productos implementado: los controladores delegan en los proxy services,
  que consumen orders/catalog mediante interfaces `@HttpExchange` y reenvían el encabezado
  `Authorization` recibido. Los microservicios vuelven a validar el JWT.
- El listado de productos adapta la respuesta paginada de catálogo a una lista plana.

## Cómo levantarlo

```
mvn spring-boot:run
```

Requiere un `JWT_ISSUER_URI` alcanzable: el `JwtDecoder` descarga el JWK del issuer al arrancar.

## Variables de entorno

| Variable | Descripción |
|---|---|
| `SERVER_PORT` | Puerto HTTP (por defecto 8080) |
| `JWT_ISSUER_URI` | Issuer del IDaaS (Azure Entra). Sin valor por defecto |
| `JWT_AUDIENCE` | Audience esperado en el claim `aud`. Sin valor por defecto |
| `ORDERS_BASE_URL` | URL base de ms-pedidos360-orders (por defecto `http://localhost:8081`) |
| `CATALOG_BASE_URL` | URL base de ms-pedidos360-catalog (por defecto `http://localhost:8082`) |

## Endpoints

| Método | Ruta | Acceso |
|---|---|---|
| GET | `/me` | Cualquier usuario autenticado |
| GET | `/api/orders` | Admin, Operator, Customer |
| GET | `/api/orders/{id}` | Admin, Operator, Customer |
| POST | `/api/orders` | Admin, Operator, Customer; devuelve 201 |
| PATCH | `/api/orders/{id}/status` | Admin, Operator; devuelve 200 |
| DELETE | `/api/orders/{id}` | Admin, Operator, Customer; cancela y devuelve el pedido (200) |
| GET | `/api/catalog/products` | Admin, Operator, Customer |
| GET | `/api/catalog/products/{id}` | Admin, Operator, Customer |
| POST | `/api/catalog/products` | Admin; devuelve 201 |
| PUT | `/api/catalog/products/{id}` | Admin; devuelve 200 |
| DELETE | `/api/catalog/products/{id}` | Admin; desactiva el producto (204, sin cuerpo) |
| PATCH | `/api/catalog/products/{id}/stock` | Admin, Operator; devuelve 200 |

Todas las rutas exigen JWT válido. Los GET devuelven 200. Orders comprueba además la
propiedad de los pedidos para Customer y las transiciones permitidas: Customer cancela
solo desde `CREADO`; Admin y Operator desde `CREADO` o `ACEPTADO`.

`POST /api/orders` recibe solo `items` (cada línea con `productId` y `quantity`): el nombre
del cliente lo obtiene orders desde el claim `name` del JWT. El cambio de estado recibe
`{"status":"ACEPTADO"}` y el ajuste de stock `{"stock":50}` (valor absoluto).
El descuento de stock es una operación interna entre orders y catalog, no una ruta del BFF.

## Pruebas

```
mvn test
```

`BffSecurityTest` firma tokens de prueba con un par RSA en memoria y verifica cada rama de la
validación: token válido, sin token, expirado, audience incorrecto, issuer incorrecto, firma de
otra clave, y 200 frente a 403 según el rol.

`OrderControllerTest` y `ProductControllerTest` usan MockMvc con los proxy services mockeados.
Cubren el camino feliz de cada ruta, los roles permitidos, los rechazos 403 y el reenvío exacto
de `Authorization`, identificadores y DTOs. No requieren Azure ni microservicios reales.
