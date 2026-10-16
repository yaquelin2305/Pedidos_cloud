# Gateway de Pedidos360

API Gateway reactivo con Java 21, Spring Boot 3.5.16 y Spring Cloud 2025.0.3.
Valida JWT y permisos antes de reenviar al BFF. No reescribe paths ni sustituye
`Authorization`. El BFF mantiene su propia seguridad.

## Configuración

| Variable | Uso / default local |
|---|---|
| `SERVER_PORT` | Puerto HTTP; `8090` |
| `JWT_ISSUER_URI` | Issuer del tenant; obligatorio |
| `JWT_AUDIENCE` | Audience de la API; obligatorio |
| `JWT_REQUIRED_SCOPE` | Scope en `scp`; `access_as_user` |
| `BFF_BASE_URL` | Destino único; `http://localhost:8080` |
| `CORS_ALLOWED_ORIGINS` | Orígenes separados por comas; `http://localhost:5173` |

La firma se verifica contra JWKS del issuer; también se validan issuer, expiración,
audience y scope. Token ausente o inválido devuelve 401; rol insuficiente, 403.
Ambos errores usan JSON con `timestamp`, `status`, `error` y `message`.

CORS se procesa antes de autenticar, permite GET/POST/PUT/PATCH/DELETE/OPTIONS y
headers Authorization/Content-Type, sin credentials, con maxAge 3600. En Compose
el único origen permitido es `http://localhost:5173`.

## Rutas y roles

Todas las rutas van al BFF con timeout de conexión de 5 s y respuesta de 15 s.

| Método | Ruta | Roles |
|---|---|---|
| OPTIONS | /** | Público (preflight) |
| GET | /me | Autenticado |
| GET, POST | /api/orders | Admin, Operator, Customer |
| GET, DELETE | /api/orders/{id} | Admin, Operator, Customer |
| PATCH | /api/orders/{id}/status | Admin, Operator |
| GET | /api/catalog/products, /api/catalog/products/{id} | Admin, Operator, Customer |
| POST | /api/catalog/products | Admin |
| PUT, DELETE | /api/catalog/products/{id} | Admin |
| PATCH | /api/catalog/products/{id}/stock | Admin, Operator |

Cualquier otra combinación se rechaza. Los permisos coinciden con los controladores
del BFF; las reglas de negocio y propiedad de pedidos siguen en los servicios.

## Ejecutar y probar

Configurar las variables obligatorias en la terminal, sin versionar valores reales,
y levantar el BFF en 8080. Desde esta carpeta:

```sh
mvn spring-boot:run
mvn clean test
```

Las pruebas usan WebTestClient, JWT firmados con RSA generado en memoria y
MockWebServer como BFF. Importan las rutas reales de producción, sin depender de
Azure ni de una base de datos. El perfil `test` sustituye solo el decoder y sus
claves, manteniendo los mismos validadores.

```sh
# Sin token: 401 JSON
curl -i http://localhost:8090/api/orders
# TOKEN contiene un access token real, nunca se guarda en el repositorio
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8090/api/orders
# Preflight: 200 y un solo Access-Control-Allow-Origin exacto
curl -i -X OPTIONS http://localhost:8090/api/orders \
  -H 'Origin: http://localhost:5173' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: Authorization, Content-Type'
```

## Referencias de versión

- [Spring Cloud 2025.0](https://github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-2025.0-Release-Notes)
- [Propiedades de Gateway WebFlux](https://docs.spring.io/spring-cloud-gateway/reference/configprops.html)

Se usa el prefijo `spring.cloud.gateway.server.webflux` para rutas, filtros y cliente HTTP.
