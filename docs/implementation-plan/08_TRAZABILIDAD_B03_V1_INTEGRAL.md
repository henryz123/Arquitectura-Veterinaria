# Trazabilidad B03 — agenda, citas y grooming

**Fecha de actualización:** 2026-10-27
**Fuente de verdad:** `arquitectura_veterinaria_v1_FINAL/B03_CITAS_Y_GROOMING.md` (inmutable).
**Checkpoint cubierto:** catálogo de servicios, horario operativo y disponibilidad profesional. B03 no está cerrado.

| Requisito | Estado | Evidencia verificable | Pendiente para cierre V1 |
|---|---|---|---|
| RF-B03-001 | Parcial verificado | V022 persiste horario semanal local y disponibilidad de profesionales con RLS; Angular permite registrar/listar ambos; E2E lo confirma. | Bloqueos, ausencias, edición y su efecto sobre reservas futuras. |
| RF-B03-002 | Parcial verificado | V022 y `/schedule` crean/listan servicios clínica/grooming con duración por 15 minutos, precio referencial, publicación y estado; E2E persiste un servicio real. | Edición visual, inactivación y consumo solo de servicios publicados desde CLIENT. |
| RF-B03-003 | Verificado | CLIENT solicita desde Angular una cita con mascota propia vigente, servicio activo/publicado, fecha futura e idempotency key; PostgreSQL persiste una solicitud `PENDIENTE` sin reservar cupo. El E2E autentica CLIENT y comprueba la respuesta visible. | Incluir la solicitud de grooming y las reglas de contrapropuesta en sus verticales. |
| RF-B03-004 | Pendiente | Solo existe el modelo persistente de propuesta. | API/UI de contrapropuesta e historial visible. |
| RF-B03-005 | Pendiente | No existe aceptación CLIENT. | Revalidación al aceptar y rechazo sin reserva. |
| RF-B03-006 | Parcial verificado | ADMIN_VETERINARIA/RECEPCION confirma una solicitud `PENDIENTE`; la transacción crea una sola reserva, cambia el estado y registra el evento. El reintento de la misma solicitud devuelve la misma reserva. | Reprogramación, contrapropuesta, cancelación y sus transiciones autorizadas. |
| RF-B03-007 | Parcial verificado | La confirmación bloquea la solicitud y serializa por profesional con bloqueo asesor transaccional; dentro de la misma transacción revalida rol, horario, disponibilidad, bloqueos y reservas. La regresión con COMMIT real libera dos transacciones simultáneas sobre el mismo candado: una confirma y la otra recibe `B03_SLOT_UNAVAILABLE`; no queda traslape persistido ni fixture residual. | Probar la misma invariante para grooming, reprogramación y cambios concurrentes de estado. |
| RF-B03-008 | Parcial verificado | V020 y el servicio revalidan profesionales activos con rol `VETERINARIO`/`GROOMING`; UI no expone identidad sensible. | Obligación de veterinario al confirmar una cita clínica. |
| RF-B03-009 | Pendiente | Existe tabla de eventos de agenda sin escritor funcional. | Reasignación, aviso y traza para cambios autorizados. |
| RF-B03-010 | Parcial verificado | Servicios y reservas usan el mismo modelo para `GROOMING`, y la confirmación exige rol GROOMING vigente. | Recorrido de navegador de grooming, contrapropuesta y confirmación por personal asignado. |
| RF-B03-011 | Pendiente | Existe tabla `grooming_attention`, sin API/UI. | Inicio, finalización y notas operativas por encargado. |
| RF-B03-012 | Parcial verificado | CLIENT cancela directamente solicitudes pendientes/propuestas o solicita cancelar una confirmada; recepción/admin mantiene o aprueba con justificación. La aprobación libera el intervalo y conserva el evento. La regresión PostgreSQL comprueba que el rechazo mantiene la reserva, la aprobación la libera una vez, los reintentos no duplican eventos y CLIENT Bosque recibe `404` para una solicitud Aurora; E2E lo verifica. | Cancelación desde contrapropuesta, manejo de casos en atención y notificaciones. |
| RF-B03-013 | Pendiente | B02 mantiene la restricción clínica por veterinario. | Asociar atención clínica a una cita sin ampliar acceso de recepción/grooming. |
| RF-B03-014 | Pendiente | V022 persiste bloqueos, sin gestión ni inasistencia. | Gestión autorizada y efecto sobre reservas. |
| RF-B03-015 | Pendiente | El evento reserva `notification_emitted_at`, sin mecanismo de emisión. | Notificaciones conforme a cambios válidos. |
| RF-B03-016 | Parcial verificado | La creación de solicitud y la confirmación escriben eventos tenant-locales; CLIENT y STAFF consultan proyecciones limitadas de sus solicitudes. | Historial de propuestas, cancelaciones, reprogramaciones y grooming. |

## Evidencia del checkpoint

- V022 aplicada una sola vez; SHA-256 fuente `1CCDE1A6BDD3512371CC59D21BCE7D61182C22BCA3EC3FC5651285EB6391929D`, checksum Flyway `2011436639`.
- La auditoría de SQL, preflight reversible, ACL, RLS y catálogo se registran en `docs/audits/B03_V022_PREAUDITORIA_Y_VERIFICACION.md`.
- Backend: 11/11 pruebas de política, servicio e integración B03 correctas contra PostgreSQL local, con Flyway desactivado durante pruebas.
- Angular: 12/12 pruebas correctas, build correcto con advertencia de presupuesto inicial no bloqueante.
- OpenAPI: Redocly válido.
- Playwright: `B03_SERVICES_AVAILABILITY_BROWSER_E2E=PASS` y `B03_CLIENT_CONFIRMATION_BROWSER_E2E=PASS`; ambos usan Chrome local, datos sintéticos y comprueban aislamiento Aurora/Bosque.
- Agent Browser: la ruta protegida `/schedule` redirige a autenticación y su árbol accesible expone `main`, título, etiquetas nativas y navegación de contexto. La versión instalada no tiene subcomando `a11y`.

## Próxima operación concreta

Implementar contrapropuesta/reprogramación con aceptación CLIENT y, después, grooming, bloqueos/ausencias, ejecución, notificaciones e historial visible. La prueba de dos confirmaciones realmente simultáneas ya complementa el rechazo secuencial de solape; faltan las variantes de reprogramación y grooming.

## Actualización 2026-10-27 — estado vigente de reprogramación

Esta sección sustituye el estado de las filas RF-B03-004 a RF-B03-009, RF-B03-012 y RF-B03-016 de la tabla anterior; conserva el texto previo como historial del checkpoint anterior.

| Requisito | Estado vigente | Evidencia nueva y límite pendiente |
|---|---|---|
| RF-B03-004 | Parcial verificado | STAFF propone fecha/hora y profesional desde `/schedule`; CLIENT ve solo su proyección, acepta o rechaza. Las propuestas no ocupan cupo, se versionan y se cierran al sustituirse. Falta historial visual completo. |
| RF-B03-005 | Parcial verificado | La aceptación CLIENT bloquea solicitud/propuesta, exige fecha futura y rechaza una alternativa que ya está ocupada. En reprogramación conserva la reserva original y exige revalidación STAFF posterior. Falta E2E de rechazo visible y la variante grooming. |
| RF-B03-006 | Parcial verificado | La reprogramación aceptada vuelve a validar rol, horario, disponibilidad, bloqueos y reservas antes de actualizar una única reserva. Falta cobertura de todas las transiciones grooming y ausencias. |
| RF-B03-007 | Parcial verificado | La prueba con COMMIT real deja una sola confirmación. Reprogramación bloquea ambos profesionales en orden estable y rechaza una alternativa ocupada sin perder la reserva original. Faltan carreras grooming y cancelación-confirmación. |
| RF-B03-008 | Parcial verificado | V020 y el servicio revalidan profesionales activos; la confirmación clínica no admite rol ajeno. Falta el recorrido E2E de grooming. |
| RF-B03-009 | Parcial verificado | STAFF reasigna una cita confirmada sin cambiar fecha/hora. La transacción valida disponibilidad, conserva una reserva única y registra `PROFESIONAL_REASIGNADO`; el reintento no duplica evento. Faltan notificación e historial visual. |
| RF-B03-012 | Parcial verificado | La cancelación también cubre estados de reprogramación sin liberar el intervalo hasta la resolución autorizada. Faltan casos en atención y notificaciones. |
| RF-B03-016 | Parcial verificado | Solicitud, confirmación, cancelación, propuesta, decisión, reprogramación y reasignación generan trazas tenant-locales. CLIENT no recibe identidad interna del profesional. Falta pantalla de historial completo y eventos grooming. |

### Evidencia actualizada

- Backend: `mvnw.cmd '-Dtest=SchedulingAccessPolicyTest,SchedulingServiceTest,SchedulingDatabaseIntegrationTest,SchedulingConcurrencyCommitIntegrationTest' test` con Java 21, PostgreSQL local y Flyway desactivado: 13/13 correctas.
- Angular: `npm.cmd test -- --watch=false`: 15/15 correctas. `npm.cmd run build`: correcto; advertencia no bloqueante de 29.52 kB sobre el presupuesto inicial.
- OpenAPI: `npm.cmd run validate:contract` válido con Redocly.
- Playwright/Chromium: `B03_REPROGRAMMING_BROWSER_E2E=PASS`; STAFF propone, CLIENT acepta una proyección sin identidad interna y STAFF confirma el cambio real.

**Próxima operación vigente:** grooming operativo sobre la agenda consolidada: solicitud CLIENT, confirmación STAFF, ejecución por GROOMING, estados e historial. Luego bloques/ausencias, inasistencias, notificaciones e historial visual.

## Actualización 2026-10-27 — ejecución grooming asignada

Esta actualización sustituye el límite de RF-B03-010, RF-B03-011 y complementa RF-B03-016. Las filas históricas se conservan para mostrar el avance incremental.

| Requisito | Estado vigente | Evidencia nueva y límite pendiente |
|---|---|---|
| RF-B03-010 | Parcial verificado | CLIENT ya solicita un servicio grooming, STAFF lo confirma con encargado activo/disponible y `/grooming` muestra solo la tarea del encargado asignado. Falta la reprogramación grooming con aceptación CLIENT y su E2E autenticado. |
| RF-B03-011 | Parcial verificado | El encargado GROOMING inicia y completa su reserva mediante API y Angular; el resultado/notas se conservan en `grooming_attention` y el historial asignado. La integración PostgreSQL verifica rol, asignación, idempotencia y eventos. Falta E2E autenticado y estados operativos adicionales aprobados. |
| RF-B03-016 | Parcial verificado | Los eventos `GROOMING_INICIADO` y `GROOMING_COMPLETADO` se escriben una sola vez y la tarea completada permanece visible al encargado. Falta una consulta/historial visual completo para administración/recepción y notificaciones. |

### Evidencia de esta actualización

- Backend: 15/15 pruebas B03 focalizadas correctas con Java 21, PostgreSQL local y Flyway desactivado; incluye rechazo de la reserva grooming Bosque desde un encargado Aurora.
- Angular: 5 archivos/16 pruebas correctas; build correcto con advertencia de presupuesto inicial no bloqueante.
- OpenAPI: Redocly válido. Contrato cubre las tres rutas grooming y sus proyecciones sin datos clínicos.
- E2E: el recorrido `b03-grooming.e2e.mjs` está preparado pero **no ejecutado**; faltan las variables locales `AV_DEMO_GROOMER_AURORA_EMAIL`, `AV_DEMO_GROOMER_AURORA_PASSWORD` y `AV_E2E_CHROME_PATH`. No se declaran resultados de navegador ni accesibilidad autenticada.

**Próxima operación vigente:** cuando existan las credenciales grooming sintéticas, ejecutar el recorrido E2E controlado; mientras tanto, continuar B03 con bloques/ausencias e inasistencias, sin afirmar cierre del bloque.

## Actualización 2026-10-27 — bloqueos, ausencias e inasistencias

Esta actualización sustituye el límite anterior de RF-B03-001 y RF-B03-014 y complementa RF-B03-016. No altera las filas históricas ni la especificación V1.

| Requisito | Estado vigente | Evidencia nueva y límite pendiente |
|---|---|---|
| RF-B03-001 | Parcial verificado | ADMIN_VETERINARIA/RECEPCION consulta y crea bloqueos tenant-locales de organización o profesional desde `/schedule`. El bloqueo exige intervalo futuro y profesional activo/asignable; la disponibilidad lo aplica a confirmaciones posteriores sin alterar reservas ya existentes. Falta gestión visual completa de horarios semanales y edición/retiro de bloqueos cuando V1 lo permita. |
| RF-B03-014 | Parcial verificado | ADMIN_VETERINARIA/RECEPCION registra `INASISTENCIA` solo sobre una solicitud confirmada y con motivo. La transacción bloquea solicitud y profesional, actualiza reserva/solicitud, libera la franja y escribe una sola traza; el reintento es idempotente. Falta integrar las transiciones restantes de atención clínica y su historial administrativo visible. |
| RF-B03-016 | Parcial verificado | `INASISTENCIA_REGISTRADA` conserva el cambio tenant-local y el E2E comprueba la persistencia visible. Falta una consulta visual integral de eventos, propuestas, reasignaciones y grooming. |

### Evidencia de esta actualización

- Backend: 17/17 pruebas B03 focalizadas correctas con Java 21, PostgreSQL local y Flyway desactivado, incluidas ausencia tenant-local, rechazo cross-tenant, liberación de franja e idempotencia de inasistencia.
- Angular: 5 archivos/17 pruebas correctas; build correcto con advertencia no bloqueante de 52.63 kB sobre el presupuesto inicial.
- OpenAPI: Redocly válido; contrato añade las rutas protegidas de bloques e inasistencia y sus esquemas.
- Playwright/Chrome: `B03_BLOCKS_NO_SHOW_BROWSER_E2E=PASS`; CLIENT solicita, STAFF confirma, registra la inasistencia y crea un bloqueo real desde el navegador. La consulta posterior autorizada comprueba los datos persistidos.

**Próxima operación vigente:** mantener pendiente el E2E grooming hasta contar con credenciales sintéticas y continuar B03 con historial visible y notificaciones aprobadas, sin declararlo completo.
