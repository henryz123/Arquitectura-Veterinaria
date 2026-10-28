# B03 — Bloqueos, ausencias e inasistencias

**Fecha:** 2026-10-27
**Alcance:** RF-B03-001, RF-B03-014 y complemento de RF-B03-016.
**Base persistente:** V022 ya aplicada en `arquitectura_veterinaria`; no se creó ni aplicó una migración nueva.

## Decisión de implementación

Se reutiliza `appointment_management.schedule_block`, creado por V022 y protegido por el modelo tenant-local existente. Un bloqueo pertenece a una organización o a un profesional asignable; su intervalo debe ser futuro, no vacío y no puede afectar reservas ya confirmadas. Su función es impedir confirmaciones posteriores que se superpongan, no reescribir ni borrar reservas existentes.

La inasistencia se limita a una cita `CONFIRMADA`. ADMIN_VETERINARIA o RECEPCION debe aportar un motivo breve; la transición cambia de manera atómica la reserva y la solicitud a `INASISTENCIA`, registra una única traza y libera la franja para disponibilidad futura. Un reintento devuelve el mismo resultado sin crear eventos adicionales.

## Controles aplicados

- `GET/POST /api/v1/schedule/blocks` queda restringido a ADMIN_VETERINARIA y RECEPCION. El servicio fija el contexto tenant antes de consultar o escribir.
- El bloqueo profesional exige un trabajador activo y asignable; no se aceptan identificadores de otros tenants.
- `POST /api/v1/schedule/requests/{requestId}/no-show` carga y bloquea la solicitud, exige estado `CONFIRMADA` y toma el mismo bloqueo asesor del profesional que la confirmación antes de liberar la reserva.
- La consulta de disponibilidad existente incorpora `schedule_block`; las reservas confirmadas no se alteran por crear una ausencia posterior.
- No se otorgaron permisos nuevos, no se añadió SQL dinámico, no se modificaron V001–V022 ni se desactivó RLS.

## Evidencia ejecutada

| Capa | Comando o recorrido | Resultado |
|---|---|---|
| Backend | `mvnw.cmd '-Dtest=SchedulingAccessPolicyTest,SchedulingServiceTest,SchedulingDatabaseIntegrationTest,SchedulingConcurrencyCommitIntegrationTest' test` con Java 21, PostgreSQL local y Flyway desactivado | 17 pruebas, 0 fallos, 0 errores, 0 omitidas |
| Angular | `npm.cmd test -- --watch=false` | 5 archivos, 17 pruebas correctas |
| Angular | `npm.cmd run build` | Correcto; advertencia no bloqueante: paquete inicial 552.63 kB, 52.63 kB sobre el presupuesto configurado |
| Contrato | `npm.cmd run validate:contract` | Correcto con Redocly |
| Navegador | `npm.cmd run test:e2e:b03-blocks-no-show` con Chrome local y backend/frontend locales | `B03_BLOCKS_NO_SHOW_BROWSER_E2E=PASS` |

El E2E autenticado solicita una cita clínica sintética, la confirma desde STAFF, registra la inasistencia desde la interfaz y crea un bloqueo profesional desde `/schedule`. Después consulta la agenda mediante la sesión autorizada para comprobar que el estado y el bloqueo persisten. El fixture usa identificadores controlados y no contiene credenciales ni datos reales.

## Límites vigentes

- El historial administrativo completo de cambios todavía no dispone de una pantalla dedicada.
- RF-B03-015 (notificaciones) continúa pendiente.
- El E2E de grooming sigue bloqueado por la ausencia local de `AV_DEMO_GROOMER_AURORA_EMAIL` y `AV_DEMO_GROOMER_AURORA_PASSWORD`; no se sustituyeron roles ni se modificó `.env.local`.
- B03 permanece parcial hasta cubrir sus requisitos y regresiones restantes.

## Historial administrativo visible

`GET /api/v1/schedule/changes` proyecta exclusivamente `id`, solicitud, código de acción, fechas anteriores/nuevas, motivo y creación. Solo ADMIN_VETERINARIA o RECEPCION del tenant puede consultarlo. La proyección no expone el actor ni identificadores de profesionales y reutiliza RLS de V022.

La integración descubrió que las fechas previa/nueva son opcionales por diseño. El mapper JDBC ya trata esos valores nulos de forma segura. La prueba de integración comprueba que Aurora ve su evento de inasistencia y Bosque no recibe ese `requestId`; Angular presenta la trazabilidad en `/schedule` y el E2E vuelve a comprobarla con el flujo autenticado.
