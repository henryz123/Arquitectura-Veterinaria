# B03 — ejecución grooming sobre agenda unificada

**Fecha:** 2026-10-28
**Estado:** implementación, integración y E2E autenticado verificados; B03 permanece parcial por requisitos ajenos a esta ejecución.
**Fuente de verdad:** `arquitectura_veterinaria_v1_FINAL/B03_CITAS_Y_GROOMING.md`, DEC-067 y RN-076.

## Alcance implementado

- Reutiliza `appointment_management.schedule_request`, `schedule_reservation` y `grooming_attention` de V022; no se creó ni aplicó una migración.
- `ADMIN_VETERINARIA` o `RECEPCION` conserva la confirmación de una solicitud grooming con encargado `GROOMING` activo y disponible. La misma reserva participa en la validación de solapes por persona.
- Solo el empleado con rol `GROOMING` vigente y asignado a la reserva puede listar su tarea, iniciarla y completarla. Un veterinario sin ese rol recibe denegación de autorización.
- El inicio registra una única atención grooming y cambia la reserva/solicitud a `EN_ATENCION`. La finalización guarda resultado y notas operativas, cambia ambos registros a `COMPLETADA` y conserva la tarea como historial del encargado.
- Las acciones de inicio y finalización son idempotentes para la reserva ya procesada: no duplican atención ni eventos `GROOMING_INICIADO`/`GROOMING_COMPLETADO`.
- Angular añade `/grooming`, con navegación condicional para el rol, lista de tareas asignadas, inicio, formulario de finalización y estados vacíos, de carga y error. La pantalla no proyecta propietarios, mascotas ni historia clínica.

## API y seguridad

| Ruta | Actor permitido | Protección y límite |
|---|---|---|
| `GET /api/v1/grooming/tasks` | `GROOMING` asignado | Sesión STAFF; devuelve solo tareas de su propio `principalId`, sin datos clínicos. |
| `POST /api/v1/grooming/reservations/{reservationId}/start` | `GROOMING` asignado | Sesión, CSRF, rol efectivo, tenant confiable, bloqueo de reserva y servicio tipo `GROOMING`. |
| `POST /api/v1/grooming/reservations/{reservationId}/complete` | `GROOMING` asignado | Los mismos controles; resultado requerido y notas opcionales con límites del contrato. |

No se agregaron funciones `SECURITY DEFINER`, permisos `PUBLIC`, SQL dinámico ni privilegios adicionales. Las tablas siguen usando RLS y el contexto tenant obtenido desde la sesión.

## Evidencia ejecutada

- Backend Java 21, PostgreSQL local y Flyway desactivado:
  `mvnw.cmd '-Dtest=SchedulingAccessPolicyTest,SchedulingServiceTest,SchedulingDatabaseIntegrationTest,SchedulingConcurrencyCommitIntegrationTest' test` → **15/15 correctas**.
  La integración crea sus datos sintéticos, demuestra el rechazo del veterinario sin rol grooming, el aislamiento Aurora/Bosque, la ejecución del asignado, idempotencia y la persistencia del historial completado.
- Angular: `npm.cmd test -- --watch=false` → **5 archivos / 16 pruebas correctas**.
- Build Angular: `npm.cmd run build` → correcto. El lazy loading de rutas reduce el bundle inicial a 282.44 kB, por debajo del presupuesto de 500 kB, sin modificar `angular.json`.
- Contrato: `npm.cmd run validate:contract` → OpenAPI válido con Redocly.
- E2E: `frontend/e2e/b03-grooming.e2e.mjs` pasó dos veces con Chrome local. El recorrido crea o reutiliza mediante formularios visibles el servicio, horario y disponibilidad sintéticos; luego verifica solicitud CLIENT, confirmación STAFF, inicio/finalización por GROOMING, persistencia, denegación de historia clínica y aislamiento. La segunda ejecución confirmó idempotencia: una solicitud, una reserva y una atención `COMPLETADA` para la clave de operación sintética.
- Angular: `npm.cmd test -- --watch=false` → **6 archivos / 19 pruebas correctas**. La regresión añadió el registro global de `es-PE`, necesario para que `DatePipe` no interrumpa el renderizado de tareas grooming.

## Fixture E2E y Agent Browser

La cuenta GROOMING de Aurora se creó mediante `database/demo/seed-groomer-synthetic.psql`, que reutiliza las funciones de invitación y aceptación autorizadas, usa `av_app`, está limitada a la base local y se ejecutó dos veces sin duplicar la cuenta. La política local bloqueó el lanzador `.ps1`; no se modificó esa política y la ejecución controlada invocó el mismo SQL auditado con `psql` y variables solo en memoria.

Agent Browser está instalado como versión 0.27.0. El lanzador PowerShell `agent-browser.ps1` continúa bloqueado por la política local; `agent-browser.cmd --version` funciona. El E2E autenticado se ejecutó con Playwright y Chrome local; no se declara una auditoría automática `a11y` porque esa versión de Agent Browser no incluye ese subcomando.

## Límites pendientes B03

- Propuesta/reprogramación específica de grooming y su aceptación CLIENT.
- Notificaciones y un historial completo para administración/recepción.
- Carreras adicionales entre confirmación/cancelación y ejecución grooming.
