# ms-pedidos360-bff

Backend for Frontend de Pedidos360. Valida el JWT emitido por el IDaaS (firma, issuer, audience y
vigencia), expone los claims del usuario y enrutará las peticiones hacia `ms-pedidos360-orders` y
`ms-pedidos360-catalog`.

## Estado

- Capa de seguridad completa: validación de JWT, mapeo de roles y scopes a authorities,
  respuestas 401 (token ausente o inválido) frente a 403 (rol insuficiente).
- Endpoint `GET /me`: devuelve `subject`, `name`, `roles` y `scopes` del token validado.
- Pendiente del contrato de endpoints: interfaces `@HttpExchange` de orders/catalog y los
  controladores que enrutan hacia ellos.

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

## Pruebas

```
mvn test
```

`BffSecurityTest` firma tokens de prueba con un par RSA en memoria y verifica cada rama de la
validación: token válido, sin token, expirado, audience incorrecto, issuer incorrecto, firma de
otra clave, y 200 frente a 403 según el rol.
