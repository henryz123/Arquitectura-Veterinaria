# B03 - solicitud CLIENT y confirmación atómica

**Fecha:** 2026-10-25
**Alcance:** vertical de solicitud CLIENT y confirmación manual sobre V022 ya aplicada. No se creó ni aplicó una migración nueva.

## Controles implementados

- Solo una sesión `CLIENT` con vinculación aprobada puede solicitar para una mascota propia activa. El servicio se resuelve por tenant y debe estar activo y publicado.
- La solicitud exige fecha futura America/Lima, duración de servicio válida y fecha de inicio/fin dentro de un mismo día operativo. Una solicitud `PENDIENTE` no crea una reserva ni consume disponibilidad.
- La clave UUID de idempotencia es única por solicitante. El reintento equivalente recupera la misma solicitud sin abortar la transacción PostgreSQL.
- Solo `ADMIN_VETERINARIA` o `RECEPCION` puede confirmar. La operación bloquea la solicitud, adquiere un bloqueo asesor transaccional por profesional y revalida dentro de la transacción: rol vigente, horario de la veterinaria, disponibilidad individual, bloqueos y reservas vigentes.
- Una confirmación crea una reserva, actualiza la solicitud y escribe el evento de agenda en la misma transacción. El reintento de una solicitud ya confirmada devuelve su única reserva vigente.
- La API y la UI no exponen correo, nombres del profesional ni datos clínicos; los listados siguen el tenant de la sesión.

## Evidencia ejecutada

- `mvnw.cmd '-Dtest=SchedulingAccessPolicyTest,SchedulingServiceTest,SchedulingDatabaseIntegrationTest' test`: 11 correctas, 0 fallos/errores, Java 21, PostgreSQL local y Flyway desactivado.
- `npm.cmd run validate:contract`: OpenAPI válido con Redocly.
- `npm.cmd test -- --watch=false`: 5 archivos, 12 pruebas correctas.
- `npm.cmd run build`: correcto; advertencia no bloqueante de 20.06 kB sobre el presupuesto inicial de 500 kB.
- `B03_SERVICES_AVAILABILITY_BROWSER_E2E=PASS` y `B03_CLIENT_CONFIRMATION_BROWSER_E2E=PASS`: Chrome local verifica solicitud visible, confirmación, rechazo visible de horario incompatible, estado confirmado desde CLIENT y aislamiento Aurora/Bosque.

## Límite conocido

La integración comprueba el rechazo de solape y la implementación usa una serialización transaccional por profesional. Falta una prueba que dispare dos confirmaciones desde transacciones realmente simultáneas; este punto queda abierto y bloquea declarar RF-B03-007 cerrado.
