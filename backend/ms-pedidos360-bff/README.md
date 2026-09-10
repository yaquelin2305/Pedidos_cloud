# ms-pedidos360-bff

Backend for Frontend de Pedidos360. Valida el JWT emitido por el IDaaS (issuer, audience, firma y
vigencia) y enruta las peticiones hacia los microservicios `ms-pedidos360-orders` y
`ms-pedidos360-catalog`.

## Cómo levantarlo

```
mvn spring-boot:run
```

## Variables de entorno

| Variable | Descripción |
|---|---|
| `SERVER_PORT` | Puerto HTTP (por defecto 8080) |
| `JWT_ISSUER_URI` | Issuer del IDaaS (Azure Entra) |
| `JWT_AUDIENCE` | Audience esperado del token |

## Puerto

8080
