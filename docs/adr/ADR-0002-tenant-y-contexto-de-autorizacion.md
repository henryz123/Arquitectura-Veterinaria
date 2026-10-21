# ADR-0002 — Tenant confiable y autorización en capas

**Estado:** aceptado para implementación posterior.
**Contexto V1:** aislamiento por tenant, recurso, rol, titularidad y estado es obligatorio.

## Decisión

El tenant privado se obtiene de la sesión/identidad autenticada y de una ruta pública validada para catálogo, nunca de un campo arbitrario del cliente. La aplicación autoriza antes de acceder a datos. PostgreSQL añade RLS con contexto de transacción y claves/restricciones por tenant; el rol de aplicación no es dueño del esquema.

## Alternativas descartadas

- Confiar solo en filtros Angular o en un `tenantId` recibido: vulnerable a manipulación.
- Esquema/base por tenant: complejidad operativa desproporcionada para V1.
- Solo RLS sin política de aplicación: no resuelve roles, titularidad ni transiciones.

## Validación y reversión

Pruebas negativas cruzadas, prueba de pool con `SET LOCAL` y auditoría de permisos. Si RLS resulta incompatible con una capacidad verificada de PostgreSQL/Flyway, se bloquea la migración y se reabre el ADR; no se desactiva silenciosamente.
