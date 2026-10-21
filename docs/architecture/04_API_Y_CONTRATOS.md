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

## Autenticación de navegador y selección de contexto

El login crea una sesión opaca de servidor. Una ruta de acceso pública incluye un `tenantSlug` que solo sirve para localizar una cuenta del contexto solicitado; no es autoridad ni se acepta un `tenantId` arbitrario. Tras validar credenciales, tipo de cuenta, estado de cuenta y estado de tenant, la sesión derivada pasa a ser la única fuente confiable de tenant y roles.

El navegador recibe solo una cookie `HttpOnly`, `Secure` en producción, `SameSite=Lax` y con alcance mínimo. La respuesta de login devuelve un token CSRF vinculado a la sesión para mantenerlo solo en memoria del cliente; no devuelve el token opaco de sesión ni permite persistir el CSRF en almacenamiento web. Las llamadas mutantes añaden ese token CSRF. Producción sirve SPA y API en mismo origen; en desarrollo Angular usa un proxy local hacia `/api`. Si una excepción exige orígenes distintos, se documenta y autoriza antes de usar `SameSite=None`, CORS con credenciales y cookies de terceros.

El recurso técnico `GET /api/v1/session-context` devuelve únicamente el tipo de cuenta, roles efectivos y metadatos mínimos del tenant de la sesión. `GET /api/v1/tenant-profile/{tenantSlug}` permite demostrar el perfil del tenant propio; un slug ajeno se responde como no visible sin derivar autoridad de la ruta. Ambos recursos soportan el vertical G4-I00 y no añaden requisitos funcionales nuevos.

## Contrato OpenAPI de diseño

`docs/contracts/openapi-v1.yaml` es el contrato OpenAPI 3.1 canónico para las familias que abren los primeros verticales. Mantiene el marcador `demo.invalid`, versión `0.1.0-design` y `x-v1-requirements` hasta que una ruta sea implementada y probada. G4-I00 autoriza instalar un validador formal y corregir este contrato antes de generar proveedor o consumidor. Cada incremento materializa solo las rutas autorizadas, valida sintaxis y prueba compatibilidad antes de ampliar el contrato.
