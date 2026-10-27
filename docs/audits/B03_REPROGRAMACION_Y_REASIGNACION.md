# B03 - Reprogramacion y reasignacion controladas

**Fecha:** 2026-10-27
**Alcance:** cambio de fecha/hora con aceptacion CLIENT y reasignacion de profesional sin cambiar hora.
**Migraciones:** ninguna. V022 conserva el esquema, RLS y los privilegios ya auditados.

## Decisiones verificadas

- Una propuesta no crea reserva ni ocupa el nuevo intervalo. El CLIENT autenticado recibe solo su alternativa propia y no ve identificadores de profesionales ni actores internos.
- Al aceptar, la transaccion bloquea solicitud y propuesta; rechaza una alternativa ya ocupada sin cambiar la reserva original. La confirmacion final sigue siendo exclusiva de ADMIN_VETERINARIA o RECEPCION y revalida rol, horario, disponibilidad, bloqueos y solapes bajo bloqueo asesor por profesional.
- La reprogramacion aceptada mantiene la reserva original hasta que la confirmacion final mueve una unica reserva. Si la revalidacion falla, no se libera el intervalo original.
- Cambiar solo de profesional es una operacion distinta: no requiere aceptacion CLIENT, conserva fecha/hora, bloquea ambos profesionales en orden estable y escribe `PROFESIONAL_REASIGNADO`.
- V1 no define un plazo arbitrario de caducidad para propuestas. Se rechaza una fecha que ya no sea futura y no se inventa una ventana temporal adicional.

## Evidencia ejecutada

- Backend Java 21: 13/13 pruebas B03 correctas, incluida alternativa ocupada, rechazo CLIENT, aceptacion, revalidacion, reasignacion idempotente, aislamiento Aurora/Bosque y confirmacion concurrente con COMMIT real.
- Angular: 15/15 pruebas correctas y build correcto; la advertencia de presupuesto inicial es no bloqueante.
- OpenAPI: Redocly valido.
- Chromium/Playwright: `B03_REPROGRAMMING_BROWSER_E2E=PASS`; CLIENT crea/reutiliza un fixture sintetico controlado, STAFF propone, CLIENT acepta y STAFF confirma el cambio real.

## Limites pendientes

No se declara B03 cerrado: faltan grooming operativo, bloques/ausencias, inasistencias, notificaciones, historial visual completo y las carreras concurrentes especificas de esos estados.
