# ADR-0005 — REST, OpenAPI, errores e idempotencia

**Estado:** aceptado para implementación posterior.
**Contexto V1:** Angular consume backend, operaciones críticas no se duplican y los errores no revelan datos.

## Decisión

Exponer REST bajo `/api/v1`, documentado primero en OpenAPI. DTOs de entrada/salida son independientes de entidades persistentes. Errores usan formato estable y códigos HTTP semánticos. Operaciones críticas requieren `Idempotency-Key`, ligada a actor, tenant, operación y huella de petición.

## Consecuencias

Cambiar una ruta, esquema o error publicado exige actualizar OpenAPI, consumidor y pruebas de contrato de forma coordinada. No se habilita GraphQL, API pública de terceros ni versionado nuevo sin ADR.
