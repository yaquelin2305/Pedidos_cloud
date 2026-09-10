# ms-pedidos360-catalog

Microservicio de dominio catalog de Pedidos360. Expone `/api/catalog/*` y persiste sus datos mediante
Spring Data JPA.

El motor de base de datos cloud aún no está definido (duda D5): no se incluye driver JDBC hasta
resolverlo con el docente.

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
