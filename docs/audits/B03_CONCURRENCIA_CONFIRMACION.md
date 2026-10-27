# B03 — Evidencia de concurrencia de confirmación

**Fecha:** 2026-10-27

**Ámbito:** base local `arquitectura_veterinaria`; no se aplicó una migración.
**Fuente funcional:** `arquitectura_veterinaria_v1_FINAL/B03_CITAS_Y_GROOMING.md` — AT-B03-03 y RF-B03-007.

## Método reproducible

`SchedulingConcurrencyCommitIntegrationTest` usa credenciales sintéticas locales ya excluidas de Git y Flyway desactivado. Crea dos solicitudes clínicas sobre la misma franja para un veterinario asignable, abre dos transacciones Spring en conexiones independientes y adquiere primero el mismo bloqueo asesor PostgreSQL que usa `SchedulingService.confirmScheduleRequest`.

La prueba comprueba que ambas confirmaciones quedan esperando dicho bloqueo. Tras liberarlo, ambas transacciones continúan y realizan su resultado normal con `COMMIT` real. No se emplean transacciones de prueba que hagan rollback del resultado bajo prueba.

## Resultado verificado

- Una confirmación creó la reserva y dejó la solicitud en `CONFIRMADA`.
- La otra transacción recibió `B03_SLOT_UNAVAILABLE`.
- Tras ambos `COMMIT`, existen exactamente una solicitud `CONFIRMADA` y una `PENDIENTE` entre los dos IDs del arnés; el intervalo queda no disponible.
- El arnés elimina únicamente sus IDs de solicitud y las filas dependientes con el rol de migración local. La verificación posterior devolvió `0` fixtures con las observaciones sintéticas de concurrencia.

## Límites pendientes

Esta evidencia cubre competencia simultánea de dos confirmaciones clínicas para el mismo profesional e intervalo. Siguen pendientes las variantes para reprogramación, grooming con trabajador de roles múltiples, cancelación frente a confirmación, revocación de rol y ausencias/bloqueos concurrentes. B03 permanece parcial hasta cubrir sus requisitos funcionales restantes.
