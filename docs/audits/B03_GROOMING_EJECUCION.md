# B03 — ejecución grooming sobre agenda unificada

**Fecha:** 2026-10-27
**Estado:** implementación e integración verificadas; E2E autenticado pendiente de credenciales sintéticas locales.
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
- Build Angular: `npm.cmd run build` → correcto. Advertencia no bloqueante: bundle inicial de 545.68 kB, 45.68 kB sobre el presupuesto configurado de 500 kB.
- Contrato: `npm.cmd run validate:contract` → OpenAPI válido con Redocly.
- E2E preparado: `frontend/e2e/b03-grooming.e2e.mjs` recorre solicitud CLIENT, confirmación STAFF, inicio y finalización visible por el encargado y verificación final. Aún **no se ejecutó**.

## Bloqueo E2E y Agent Browser

No se creó ni alteró ninguna cuenta para forzar el recorrido de navegador. En `.env.local` faltan, sin revelar valores, `AV_DEMO_GROOMER_AURORA_EMAIL`, `AV_DEMO_GROOMER_AURORA_PASSWORD` y `AV_E2E_CHROME_PATH`.

Agent Browser está instalado como versión 0.27.0. El lanzador PowerShell `agent-browser.ps1` queda bloqueado por la política local de ejecución; `agent-browser.cmd --version` funciona. Mientras falten la cuenta grooming sintética y la ruta de Chromium no es posible declarar E2E o accesibilidad autenticada como superados.

## Límites pendientes B03

- Propuesta/reprogramación específica de grooming y su aceptación CLIENT.
- Gestión de bloqueos, ausencias e inasistencias.
- Notificaciones y un historial completo para administración/recepción.
- Carreras adicionales entre confirmación/cancelación y ejecución grooming.
