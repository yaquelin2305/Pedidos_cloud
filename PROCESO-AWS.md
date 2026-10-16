# Proceso AWS

## AWS API Gateway HTTP API - reemplazado

El despliegue anterior usaba `pedidos360_Api` (`khqmb25tz2`), una HTTP API de AWS
con autorizador JWT que reenviaba las peticiones a nginx. Nginx exponía además
`/api/*` y `/me` directamente al BFF, permitiendo omitir esa validación perimetral.
Este registro resume la arquitectura anterior; no había un archivo PROCESO-AWS.md
en esta revisión del repositorio.

## API Gateway propio (Spring Cloud Gateway)

Por solicitud del docente se reemplaza el servicio administrado por un gateway
propio en Docker Compose. Spring Cloud Gateway WebFlux valida firma, issuer,
audience, vigencia, scope `access_as_user` y roles por método y ruta. El BFF
mantiene su segunda validación JWT y sus permisos. Orders y catalog siguen internos.

Nginx termina TLS para ambos nombres del mismo certificado:

- `52.207.140.106.nip.io`: sirve solamente la SPA.
- `api.52.207.140.106.nip.io`: reenvía únicamente al gateway.

Solo nginx publica 80/443. CORS reside en el gateway, con el origen exacto de la
SPA, sin credentials ni comodines. El preflight se resuelve antes de autenticar.

El workflow comprueba que el certificado incluya el SAN `api.$DOMAIN`; si falta,
detiene Compose para liberar el puerto 80 y ejecuta certbot con `--expand` y ambos
nombres. La emisión requiere que ambos nombres resuelvan a la EC2 y que el puerto
80 sea accesible. No cambia la configuración de AWS, Azure ni los secrets.

### Pasos manuales del equipo

1. Revisar la rama `api-gateway` y cambiar el secret `VITE_API_BASE_URL` a
   `https://api.52.207.140.106.nip.io` antes del merge que dispara el despliegue.
2. Tras desplegar, revisar `docker stats` y los reinicios. Si falta memoria,
   subir la EC2 a `t3.medium`.
3. Validar login y llamadas con Admin, Operator y Customer contra la URL real.
   Capturar todas las rutas con token y sin token, roles insuficientes (403),
   token inválido (401), respuestas correctas y preflight con un solo encabezado
   `Access-Control-Allow-Origin`. Confirmar que el dominio de la SPA no devuelve
   JSON del backend en `/api/orders` ni `/me`.
4. Después de la validación completa, eliminar `pedidos360_Api` (`khqmb25tz2`).

### Verificacion local de la migracion (2026-10-16)

| Componente | Pasados | Fallados / errores |
|---|---:|---:|
| Gateway (`mvn clean test`, Java 21) | 48 | 0 |
| BFF (`mvn clean test`, Java 21) | 56 | 0 |
| Orders (`mvn clean test`, Java 21) | 29 | 0 |
| Catalog (`mvn clean test`, Java 21) | 12 | 0 |
| Frontend (`npm test`) | 12 | 0 |

`npm run build` termina correctamente (aviso de bundle superior a 500 kB).
`docker compose config` pasa con variables ficticias. La imagen del gateway se
construye y arranca con limite de 320 MiB: curl con token falso devuelve 401 JSON;
el preflight devuelve 200 con un solo Access-Control-Allow-Origin exacto.
`nginx -t` pasa usando un certificado temporal con ambos nombres.

Estas comprobaciones locales no sustituyen el flujo real con Azure y RDS ni la
medicion de las cuatro JVM juntas en EC2. No se ha desplegado esta rama.
