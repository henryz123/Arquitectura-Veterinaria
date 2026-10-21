# ADR-0004 — PostgreSQL, Flyway e invariantes concurrentes

**Estado:** aceptado; V001 materializada y pendiente de validación contra PostgreSQL exclusivo.
**Contexto V1:** stock no negativo, única salida por operación, agenda sin solapes e idempotencia.

## Decisión

PostgreSQL 18 es la persistencia transaccional. Flyway administra solo migraciones versionadas e inmutables. `database/migration/` es la única fuente versionada: Maven la incorpora durante la compilación en `target/classes/db/migration` y Flyway ejecuta solo `classpath:db/migration`. Movimientos de stock y cambios comerciales usan transacciones, bloqueo/condición de saldo y claves únicas de operación. Agenda usa una garantía temporal en base de datos además de validación de aplicación. Importes usan `numeric` y tiempos `timestamptz`.

## Consecuencias

No se permite una migración manual fuera de Flyway ni modificar una aplicada. V001 contiene únicamente los fundamentos de identidad/tenancy de G4-I00. RLS, permisos, sesiones e índices necesitan prueba contra PostgreSQL real antes de declarar el vertical integrado; exclusiones temporales, bloqueos e idempotencia pertenecen a incrementos posteriores.

## Reversión

Cada cambio declara rollback o razón de irreversibilidad, backup previo y estrategia de compatibilidad. Nunca se usa `clean` fuera de una base efímera autorizada.
