# G2 — API REST y contratos

## Convención común

La API se publica bajo `/api/v1`. Recursos en plural y kebab-case; acciones de negocio excepcionales se expresan como subrecursos/acciones explícitas. OpenAPI es el contrato público interno versionado antes de implementar consumidores. Angular usa el contrato HTTP y nunca modelos de tablas.

Respuestas correctas usan `{"data": ...}` y colecciones añaden `meta` y `links`; errores siguen una variante estable de Problem Details: `code`, `message`, `traceId` y `fieldErrors` únicamente cuando sea seguro. Nunca se devuelven stack traces, SQL, tokens, hashes ni información para enumerar usuarios.

## Familias de recursos candidatas

| Dominio | Ruta base | Consumidores | Reglas de contrato |
|---|---|---|---|
| Identidad | `/api/v1/auth`, `/api/v1/staff-accounts`, `/api/v1/client-accounts` | público, personal, cliente | rutas de cuenta separadas; cookie de sesión y CSRF |
| SaaS | `/api/v1/tenants`, `/api/v1/ruc-reviews` | administrador SaaS | campos globales mínimos; sin clínica/comercio interno |
| Propietarios/mascotas | `/api/v1/owners`, `/api/v1/pets`, `/api/v1/pet-links` | recepción, cliente, veterinario | titularidad y privacidad antes de serializar |
| Clínica | `/api/v1/clinical-episodes`, `/api/v1/vaccinations`, `/api/v1/prescriptions` | veterinario | cliente no recibe contenido clínico íntegro |
| Agenda | `/api/v1/availability`, `/api/v1/appointments`, `/api/v1/grooming-services` | cliente y personal autorizado | transición, asignación y no traslape |
| Inventario/POS | `/api/v1/products`, `/api/v1/inventory`, `/api/v1/pos-sales` | administrador/cajero | operaciones críticas exigen `Idempotency-Key` |
| Comercio web | `/api/v1/public/tenants/{slug}`, `/api/v1/carts`, `/api/v1/orders` | visitante, cliente, cajero | catálogo público solo de tenant activo; pedido privado |
| Operación | `/api/v1/dashboard`, `/api/v1/reports`, `/api/v1/audit` | roles autorizados | proyecciones mínimas, no exposición masiva |

## Semántica necesaria

- `POST` de creación devuelve 201 y `Location`; mutaciones de estado validan transiciones y devuelven 200/204 según cuerpo.
- 400 para JSON inválido, 401 para ausencia/sesión inválida, 403 para autorización denegada cuando no revela recursos, 404 para recurso no visible, 409 para estado/conflicto, 422 para validación semántica y 429 para límites.
- Listas operativas usan paginación por cursor cuando no se necesita salto por número. Los reportes breves pueden usar periodo y agregación explícitos.
- `Idempotency-Key` es obligatorio en venta POS, confirmación/cancelación de pedido, devolución, ajustes y operaciones equivalentes que mueven stock o cambian hechos irreversibles.
- Cada contrato incluye ejemplos sintéticos, autorización requerida, estados, errores, criterios V1 cubiertos y pruebas de contrato.

## Autenticación de navegador

El login crea una sesión opaca de servidor. El navegador recibe solo una cookie `HttpOnly`, `Secure` en producción, `SameSite=Lax` y con alcance mínimo. Las llamadas mutantes añaden un token CSRF vinculado a sesión. Producción sirve SPA y API en mismo origen; si una excepción exige orígenes distintos, se documenta y autoriza antes de usar `SameSite=None`, CORS con credenciales y cookies de terceros.

## Contrato OpenAPI de diseño

`docs/contracts/openapi-v1.yaml` es un contrato OpenAPI 3.1 de diseño con las familias que abren los primeros verticales. Expone el marcador `demo.invalid`, versión `0.1.0-design` y `x-v1-requirements` precisamente para no presentarse como endpoint, servidor o prueba existentes. En esta estación no hay parser YAML/OpenAPI instalado y no se instalará fuera de una tarea autorizada; la validación formal de sintaxis es un criterio obligatorio de I00. Cada incremento materializa solo las rutas autorizadas, valida sintaxis y prueba compatibilidad antes de ampliar el contrato.
