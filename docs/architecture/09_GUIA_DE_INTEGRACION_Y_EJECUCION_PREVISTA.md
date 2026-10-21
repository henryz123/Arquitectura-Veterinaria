# G2 — Guía de integración y ejecución prevista

## Flujo de una operación privada

1. Angular envía un DTO validado al contrato OpenAPI por HTTPS, con cookie de sesión y CSRF cuando sea mutación.
2. El filtro Spring resuelve sesión, tipo de cuenta, tenant confiable, roles y estado.
3. El controlador traduce el DTO sin reglas de negocio, llama al caso de uso y adjunta correlación.
4. El caso de uso autoriza recurso/transición, aplica invariantes y abre una transacción de tenant.
5. El adaptador PostgreSQL fija el contexto local, persiste agregado, auditoría/outbox y cualquier movimiento atómico.
6. El resultado se serializa mediante un DTO permitido por rol; el evento de notificación se entrega después sin deshacer el hecho.
7. Angular representa éxito o error estable, sin inferir permisos ni manipular tenant.

## Puertos y perfiles previstos

| Entorno | UI | API | DB | Nota |
|---|---|---|---|---|
| Local | `http://localhost:4200` | `http://localhost:8080` | `localhost:5432` | CORS temporal y explícito; no se asume operativo aún |
| Test integrado | no requerida | efímero/puerto asignado | PostgreSQL aislado | datos sintéticos; sin usar producción |
| Demo | mismo origen HTTPS | `/api/v1` | privada al servicio | cookies seguras, TLS y CORS mínimo |

Los puertos son intención de documentación, no listeners activos. Cualquier cambio se hace por configuración externa, no hardcodeado en UI.

## Health, errores y observabilidad

El backend futuro expone health sin secretos; detalle operativo se restringe. `traceId` enlaza petición, auditoría y error controlado. Las respuestas no filtran IDs ajenos, SQL, configuraciones ni diagnósticos. Logs y métricas se revisan antes de cualquier publicación, con datos sintéticos.

## README técnico de estado

Mientras no se materialice G4, el procedimiento ejecutable es intencionalmente inexistente: no hay `mvnw`, `npm`, Docker, migraciones ni servicios de aplicación. La primera tarea autorizada instalará/configurará lo mínimo en una rama controlada y añadirá comandos reales solo después de que su resultado pueda verificarse.
