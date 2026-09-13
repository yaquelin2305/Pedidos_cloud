# Pedidos360 Frontend

Aplicación React 18 + Vite 5 para gestionar pedidos y catálogo. El código se organiza por
módulos en `src/modules/`: autenticación, panel, pedidos y catálogo. Los componentes y
estilos compartidos viven en `src/shared/`.

## Estado de integración

El inicio y cierre de sesión usan MSAL y Azure Entra. Las rutas tienen guards de
autenticación y rol. `shared/http/apiClient.js` obtiene el access token de la API y
adjunta `Authorization: Bearer ...` a las peticiones.

Los servicios de pedidos y catálogo todavía usan fixtures en memoria; sus mutaciones
se pierden al recargar. La conexión de esos servicios al BFF está pendiente. El helper
`getHttpErrorMessage(error)` de `shared/utils/httpError.js` extrae el mensaje de error
del backend, con un respaldo cuando no hay respuesta utilizable; aún no está conectado
a los servicios.

## Cómo levantarlo

Requiere Node.js 22 o posterior y npm. Desde `frontend/`:

```sh
npm ci
npm run dev -- --port 5173 --strictPort
```

Antes de iniciar, configura las variables de `.env.example` en un archivo local `.env`.
El origen y las rutas de retorno deben coincidir exactamente con los registrados en Azure.
Con ese puerto, abre `http://localhost:5173`. Sin las variables obligatorias la aplicación
muestra un error de configuración. Reinicia Vite después de cambiar el entorno.

```sh
npm test
npm run build
npm run preview
```

`npm test` ejecuta los archivos `src/**/*.test.js` con el ejecutor nativo de Node.
`npm run build` genera `dist/`; un build correcto no verifica por sí solo el login ni la
conexión con Azure. Para usar MSAL en preview también debe estar registrado ese origen.

## Variables de entorno

| Variable | Uso |
|---|---|
| `VITE_API_BASE_URL` | Base del API Gateway en nube; para integración local, `http://localhost:8080` (BFF) |
| `VITE_API_SCOPE` | Scope expuesto por la API, por ejemplo `api://<API_CLIENT_ID>/access_as_user` |
| `VITE_AZURE_CLIENT_ID` | Identificador de la App Registration de la SPA |
| `VITE_AZURE_AUTHORITY` | Authority del tenant; workforce: `https://login.microsoftonline.com/<TENANT_ID>` |
| `VITE_AZURE_KNOWN_AUTHORITY` | Host de la authority para External ID/CIAM; vacío en workforce. Única variable opcional |
| `VITE_AZURE_REDIRECT_URI` | Retorno del login, por ejemplo `http://localhost:5173/auth/callback` |
| `VITE_AZURE_POST_LOGOUT_REDIRECT_URI` | Retorno del logout, por ejemplo `http://localhost:5173/login` |

Las variables `VITE_` son públicas en el navegador. No deben contener contraseñas ni
client secrets. Las credenciales de las cuentas de prueba no forman parte del código ni
de los archivos de ejemplo.

## Endpoints previstos

Las rutas siguientes ya están expuestas por el BFF y se consumirán mediante `apiClient`.
Todas requieren un JWT válido; los roles indicados son los exigidos por la API.

| Método | Ruta | Roles | Resultado |
|---|---|---|---|
| GET | `/api/orders` | Admin, Operator, Customer | Lista de pedidos |
| GET | `/api/orders/{id}` | Admin, Operator, Customer | Detalle del pedido |
| POST | `/api/orders` | Admin, Operator, Customer | Crea un pedido (201) |
| PATCH | `/api/orders/{id}/status` | Admin, Operator | Cambia el estado (200) |
| DELETE | `/api/orders/{id}` | Admin, Operator, Customer | Cancela y devuelve el pedido (200) |
| GET | `/api/catalog/products` | Admin, Operator, Customer | Lista plana de productos devuelta por el BFF |
| GET | `/api/catalog/products/{id}` | Admin, Operator, Customer | Detalle del producto |
| POST | `/api/catalog/products` | Admin | Crea un producto (201) |
| PUT | `/api/catalog/products/{id}` | Admin | Actualiza un producto (200) |
| DELETE | `/api/catalog/products/{id}` | Admin | Desactiva un producto (204, sin cuerpo) |
| PATCH | `/api/catalog/products/{id}/stock` | Admin, Operator | Establece el stock absoluto (200) |

Customer solo puede consultar y cancelar sus propios pedidos, según las reglas de orders.
La pantalla de catálogo sigue restringida a Admin y Operator; Customer puede consultar
productos por API para completar el selector de un pedido.

La creación de pedidos envía únicamente `{ "items": [{ "productId": 10, "quantity": 2 }] }`.
El backend asigna `customerName` desde el claim `name` del JWT para todos los roles.
Al integrar, los servicios deberán adaptar el contrato real (`totalAmount`, estados como
`CREADO` y `ACEPTADO`, IDs numéricos) al modelo de las vistas (`total`, estados en inglés).
El detalle utiliza `item.subtotal` cuando existe y conserva el cálculo local para fixtures.

## Equivalencias MSAL

| Pauta (Angular) | Implementación real (React) |
|---|---|
| MsalGuard | RequireAuth y RequireRole |
| MsalInterceptor | Interceptor de axios en shared/http/apiClient.js |
| MsalService | Hook useMsal() de @azure/msal-react |
| MsalModule.forRoot(...) | MsalProvider en main.jsx + config/msalConfig.js |

MSAL gestiona el flujo Authorization Code con PKCE. La adaptación de la pauta a React
mantiene guards, proveedor de autenticación e interceptor; la autorización definitiva se
aplica también en el API Gateway, BFF y microservicios.
