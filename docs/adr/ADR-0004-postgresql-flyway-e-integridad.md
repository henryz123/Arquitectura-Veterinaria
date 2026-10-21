# ADR-0004 — PostgreSQL, Flyway e invariantes concurrentes

**Estado:** aceptado para implementación posterior.
**Contexto V1:** stock no negativo, única salida por operación, agenda sin solapes e idempotencia.

## Decisión

PostgreSQL 18 es la persistencia transaccional. Flyway administra solo migraciones versionadas e inmutables. Movimientos de stock y cambios comerciales usan transacciones, bloqueo/condición de saldo y claves únicas de operación. Agenda usa una garantía temporal en base de datos además de validación de aplicación. Importes usan `numeric` y tiempos `timestamptz`.

## Consecuencias

No se permite una migración manual fuera de Flyway ni modificar una aplicada. La primera migración necesita probar RLS, índices, exclusiones temporales, bloqueos e idempotencia contra PostgreSQL real antes de ser aceptada.

## Reversión

Cada cambio declara rollback o razón de irreversibilidad, backup previo y estrategia de compatibilidad. Nunca se usa `clean` fuera de una base efímera autorizada.
