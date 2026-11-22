# ms-pedidos360-report

Servicio de analitica de Pedidos360 (Java 21, Spring Boot 3.5.16 y PostgreSQL).
Escucha pedidos y stock para mantener reportes consultables sin llamar a los servicios de origen.
Puerto interno **8085**, paquete `cl.duoc.pedidos360.report`.

## Ejecucion

Desde la raiz del monorepo:

```sh
docker compose --env-file frontend/.env --env-file .env config --quiet
docker compose --env-file frontend/.env --env-file .env up --build -d orders catalog report
```

Orders declara su topico y DLT; catalog declara los de stock. Los brokers no crean topicos automaticamente.
El comando habitual sin nombres de servicios sigue levantando el stack completo.
Este necesita un equipo con al menos 16 GB de RAM; cerrar otras aplicaciones si falta memoria.
Report tiene un limite de 448 MB y espera a PostgreSQL y los tres brokers healthy.

Con Maven y Java 21, definir estas variables en la terminal antes de `mvn spring-boot:run`:

| Variable | Uso |
|---|---|
| `DB_URL` | URL JDBC de PostgreSQL |
| `DB_USERNAME`, `DB_PASSWORD` | Credenciales de la BD |
| `JPA_DDL_AUTO` | `validate` por defecto; `update` para crear las tablas en desarrollo, como en Compose |
| `JWT_ISSUER_URI`, `JWT_AUDIENCE` | Issuer y audience de Azure Entra |
| `KAFKA_BOOTSTRAP_SERVERS` | Por defecto `localhost:9092,localhost:9093,localhost:9094`; Compose usa los nombres internos |
| `SERVER_PORT` | Por defecto 8085 |

`KAFKA_UI_USERNAME` y `KAFKA_UI_PASSWORD` pertenecen al `.env` local para levantar Kafka UI.
Ninguna credencial se versiona. Las tablas son `report_processed_events`, `report_order_snapshots`,
`report_product_sales` y `report_stock_snapshots`, en la misma BD del sistema.

## Consumo y contrato

Grupo **`pedidos360-report`**, independiente de `pedidos360-audit`. Cada grupo recibe todos los eventos.
Consume `pedidos360.orders.events` y `pedidos360.catalog.stock`, con una factory tipada por mensaje
(`OrderEventMessage` y `StockChangedMessage`). Cada factory tiene **concurrencia 3**: son seis miembros
al estar activos ambos listeners, tres por topico. Cada uno procesa una de sus tres particiones.
La key por agregado conserva el orden de sus cambios y Kafka reasigna particiones cuando cae un consumidor.

Configuracion centralizada en `application.yml`: `earliest`, `max.poll.records=50`, auto commit desactivado,
confirmacion por registro (`ack-mode=record`), deserializadores envueltos en `ErrorHandlingDeserializer`
y tipos propios sin depender de headers Java del productor.

## Proyecciones

- Creacion: guarda estado y fecha inicial.
- `CREADO -> ACEPTADO`: registra aceptacion y suma unidades e ingresos por producto.
- Entrega: registra fecha; la correccion `ENTREGADO -> DESPACHADO` la borra.
- Cancelacion: registra fecha y resta los items solo si el estado anterior era `ACEPTADO`.
- Un evento sin snapshot previo crea uno con los datos disponibles. No se inventan fechas antiguas:
  si falta la creacion, ese pedido no participa del promedio de lead time.
- Stock: reemplaza el snapshot del producto; solo los activos con `lowStock=true` aparecen en stock bajo.
- `eventId` se inserta con `ON CONFLICT DO NOTHING` en la misma transaccion que la proyeccion.
  Un duplicado se ignora; un fallo revierte ambos cambios y permite reintentar.
- Las ventas se incrementan atomicamente en PostgreSQL: pedidos de distintas particiones pueden compartir
  productos. Los productos se bloquean en orden de ID para evitar deadlocks entre pedidos.

Los reportes reflejan los eventos recibidos. Si se reconstruyen desde cero despues de vencer la retencion
no se recupera la historia eliminada: pueden faltar fechas o ventas previas, y una cancelacion retenida
sin su aceptacion puede producir un saldo negativo por producto. No se presenta como un historico completo.

## Manejo de errores

Jakarta Validation revisa identidad, tipo, fechas, estado, montos e items antes de delegar al servicio.
JSON ilegible, payload nulo y payload invalido van **directamente a DLT**. Un fallo transitorio tiene
**3 reintentos separados por 1000 ms** y, si persiste, va a `<topico>.DLT` en la misma particion.
Los logs incluyen grupo, topico, particion, offset, key y causa; cada reintento se registra en WARN.

El recoverer combina `ByteArraySerializer` y `JsonSerializer` para conservar tambien JSON roto.
Su productor usa `acks=all`, idempotencia, `delivery.timeout.ms=30000`, `request.timeout.ms=15000`
y `max.block.ms=5000`. Tras procesar o recuperar el mensaje se confirma el offset.
No hay transacciones Kafka ni outbox: el consumo puede repetirse y la BD lo hace idempotente.

## API

Todas las rutas requieren JWT valido y rol **Admin u Operator**. Sin token o con token invalido: 401;
rol Customer: 403. Se validan firma, issuer, audience y vigencia como en los otros servicios.

| Metodo | Ruta | Respuesta |
|---|---|---|
| GET | `/api/reports/summary` | Conteos de los seis estados, ventas confirmadas, entregados, cancelados, lead time medio en minutos y ultimo evento |
| GET | `/api/reports/top-products?limit=5` | Ranking por unidades vendidas; desempate por ID; limite 1 a 20, fuera de rango devuelve 400 |
| GET | `/api/reports/low-stock` | Snapshots activos de stock bajo |

Las ventas confirmadas suman totales con fecha de aceptacion y estado distinto de CANCELADO.
El lead time promedia entrega menos creacion de los entregados con ambas fechas, o es `null` sin muestras.
`lastEventAt` considera pedidos y stock; es `null` cuando no hay eventos.

Report no publica puerto al host en Compose. La ruta del gateway `/api/reports/**` corresponde a la
integracion del gateway; este servicio no modifica esa configuracion.

## Pruebas

```sh
mvn test
```

Pruebas unitarias de proyeccion y consultas, listeners con validacion, politica de reintentos/DLT y
MockMvc para respuestas 200, 400, 401 y 403. Los JWT simulados prueban autorizacion local;
no reemplazan una comprobacion con tokens reales de Azure.
