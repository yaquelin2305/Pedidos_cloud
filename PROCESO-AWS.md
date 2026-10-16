# Proceso AWS

## AWS API Gateway HTTP API ? reemplazado

El despliegue anterior usaba `pedidos360_Api` (`khqmb25tz2`), una HTTP API de AWS
con autorizador JWT que reenviaba las peticiones a nginx. Nginx expon?a adem?s
`/api/*` y `/me` directamente al BFF, permitiendo omitir esa validaci?n perimetral.
Este registro resume la arquitectura anterior; no hab?a un archivo PROCESO-AWS.md
en esta revisi?n del repositorio.

## API Gateway propio (Spring Cloud Gateway)

Por solicitud del docente se reemplaza el servicio administrado por un gateway
propio en Docker Compose. Spring Cloud Gateway WebFlux valida firma, issuer,
audience, vigencia, scope `access_as_user` y roles por m?todo y ruta. El BFF
mantiene su segunda validaci?n JWT y sus permisos. Orders y catalog siguen internos.

Nginx termina TLS para ambos nombres del mismo certificado:

- `52.207.140.106.nip.io`: sirve solamente la SPA.
- `api.52.207.140.106.nip.io`: reenv?a ?nicamente al gateway.

Solo nginx publica 80/443. CORS reside en el gateway, con el origen exacto de la
SPA, sin credentials ni comodines. El preflight se resuelve antes de autenticar.

El workflow comprueba que el certificado incluya el SAN `api.$DOMAIN`; si falta,
detiene Compose para liberar el puerto 80 y ejecuta certbot con `--expand` y ambos
nombres. La emisi?n requiere que ambos nombres resuelvan a la EC2 y que el puerto
80 sea accesible. No cambia la configuraci?n de AWS, Azure ni los secrets.

### Pasos manuales del equipo

1. Revisar la rama `api-gateway` y cambiar el secret `VITE_API_BASE_URL` a
   `https://api.52.207.140.106.nip.io` antes del merge que dispara el despliegue.
2. Tras desplegar, revisar `docker stats` y los reinicios. Si falta memoria,
   subir la EC2 a `t3.medium`.
3. Validar login y llamadas con Admin, Operator y Customer contra la URL real.
   Capturar todas las rutas con token y sin token, roles insuficientes (403),
   token inv?lido (401), respuestas correctas y preflight con un solo encabezado
   `Access-Control-Allow-Origin`. Confirmar que el dominio de la SPA no devuelve
   JSON del backend en `/api/orders` ni `/me`.
4. Despu?s de la validaci?n completa, eliminar `pedidos360_Api` (`khqmb25tz2`).
