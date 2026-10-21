# B03 — Citas clínicas, agenda y baños/peluquería | Especificación funcional V1.0 FINAL

**Estado:** CERRADO FUNCIONALMENTE para V1 académica. **Procesos:** PB-04, PB-06, PB-12. **Dependencias:** Bloque 1 (roles/autenticación), B02 (propietarios/mascotas). **Confirmado:** cliente propone horario; recepción/administración confirma, contrapropone y asigna veterinario al confirmar; cliente acepta alternativas y se vuelve a comprobar disponibilidad; grooming confirmado por recepción/administración, ejecutado por servicios; cliente cancela pendientes directamente.

## 1. Resultados y límites

Proporcionar agenda operativa para clínica y grooming, con solicitud online, confirmación humana, contrapropuestas, reasignación de veterinarios, atención y cancelación. Una reserva no es el cobro ni una atención clínica; estos procesos se enlazan sin convertirse en registros idénticos.

**Fuera de V1:** autoprogramación confirmada por el cliente sin aprobación de la veterinaria, calendarios externos, GPS, algoritmo inteligente de optimización de agenda, reservas de recursos físicos complejos, pagos automáticos y políticas de penalización/cobro anticipado.

## 2. Casos de uso candidatos

| ID | Caso |
|---|---|
| CU-B03-01 | Configurar horario de veterinaria y disponibilidad de trabajadores |
| CU-B03-02 | Publicar servicios y duración/precio referencial |
| CU-B03-03 | Solicitar cita clínica |
| CU-B03-04 | Evaluar, confirmar o contrapropner cita |
| CU-B03-05 | Aceptar/rechazar contrapropuesta |
| CU-B03-06 | Reasignar veterinario / reprogramar cita confirmada |
| CU-B03-07 | Atender o marcar inasistencia de cita clínica |
| CU-B03-08 | Solicitar reserva de grooming |
| CU-B03-09 | Confirmar/contraproponer reserva grooming |
| CU-B03-10 | Ejecutar servicio grooming |
| CU-B03-11 | Cancelar solicitud pendiente o pedir cancelación confirmada |
| CU-B03-12 | Consultar agenda propia según roles |

## 3. Horarios, asignación y capacidad [DEC-067 y RO-04]

- Una veterinaria configura horas operativas semanales y bloqueos extraordinarios (feriados/cierre, mantenimiento), **hora local America/Lima**.
- Administrador/recepción registra disponibilidad, ausencias y tiempos no laborables de empleados. Para V1 no hacen falta turnos complejos ni sincronización con Google Calendar.
- Al confirmar una **cita clínica** es obligatorio asignar veterinario activo con rol clínico y disponibilidad para todo el intervalo.
- Al confirmar **grooming** se requiere asignar encargado de servicios activo y con disponibilidad; autorización de confirmación sigue en recepción/administración, nunca se hereda al groomer por sí solo.
- Se usa **un calendario consolidado por persona**: un empleado con varios roles no puede ejecutar dos citas o tareas presenciales incompatibles al mismo tiempo, aunque sean de módulos distintos.
- Servicios contienen duración fija configurable en minutos (propuesta: incrementos de 15 minutos). El intervalo ocupado se evalúa como `[inicio, fin)` para admitir una nueva cita exactamente cuando terminó la anterior.
- La agenda de cita confirmada ocupa el intervalo asignado; propuestas y solicitudes pendientes **no bloquean cupos**. Las reservas no confirmadas podrán volverse inviables y deberán contrapropnerse.
- V1 no maneja estaciones/salas ni mascotas concurrentes atendidas por mismo profesional. Añadir capacidad por bañera/mesa queda fuera de V1 salvo requerimiento explícito.
- Las citas no se pueden confirmar en el pasado; una fecha pasada sí puede registrarse como atención clínica histórica por el veterinario sin inventar citas.

## 4. Datos lógicos

**ServicioOfrecido:** tenant, tipo clínica/grooming, nombre, descripción pública, duración_min, precio_referencial, publicado/activo, restricciones básicas para tipo de mascota (opcionales).

**CalendarioProfesional:** tenant, trabajador, franjas semanales; **Ausencia/Bloqueo:** inicio/fin, motivo breve, creador; límites de fecha. Personal desactivado deja de estar asignable a nuevas citas; sus citas ya confirmadas deben reprogramarse por personal autorizado, no borrarse.

**SolicitudAgenda:** tenant, cliente/propietario, mascota, clase clínica/grooming, servicio, fecha/hora solicitada, estado, instante de creación, observación no clínica visible para recepcionista. Un cliente no reserva mascotas de otros.

**PropuestaHorario:** solicitud, nueva fecha/hora/duración, profesional propuesto si lo hay, instante, emisor (recepción/admin), estado enviada/aceptada/rechazada/cerrada, versión para impedir aceptación de propuesta obsoleta. Propuestas sin aceptación no bloquean cupos.

**ReservaConfirmada:** solicitud, intervalo confirmado, profesional asignado, versión y estado confirmado/en_atención/completado/inasistencia/cancelado. Solo una reserva efectiva actual por solicitud.

**AtenciónGrooming:** reserva, encargado responsable, inicio/fin, resultado, notas operativas breves; no sustituye historia clínica.

**RegistroCambioAgenda:** actor, anterior/nuevo intervalo, anterior/nuevo profesional, motivo, notificación emitida y aceptación cuando cambió fecha/hora.

## 5. Estados definitivos propuestos (no confundir solicitud y reserva)

### 5.1 Solicitud general

| Estado | Quién dispara | Próximo estado válido |
|---|---|---|
| `PENDIENTE` | Cliente crea solicitud | `CONFIRMADA`, `PROPUESTA`, `CANCELADA`, `CERRADA` |
| `PROPUESTA` | Recepción/admin propone alternativa | `PENDIENTE` (acepta y se revalida), `CANCELADA`, `CERRADA`, `PROPUESTA` (nueva versión) |
| `CONFIRMADA` | Recepción/admin verifica y asigna recurso | `EN_ATENCION`, `COMPLETADA` según flujo, `CANCELACION_SOLICITADA`, `REPROGRAMACION_PROPUESTA`, `CANCELADA`, `INASISTENCIA` |
| `REPROGRAMACION_PROPUESTA` | Recepción/admin requiere nueva fecha; horario original se conserva confirmado solo si sigue viable | `CONFIRMADA` tras aceptación + validación, `PENDIENTE_REPROGRAMACION` si original ya no es viable, `CANCELADA`, `CERRADA` |
| `CANCELACION_SOLICITADA` | Cliente pide cancelar confirmada | `CANCELADA` o `CONFIRMADA` si se rechaza y sigue vigente |
| `EN_ATENCION` | Profesional inicia servicio | `COMPLETADA` |
| `COMPLETADA`, `CANCELADA`, `INASISTENCIA`, `CERRADA` | Resolución | Terminal respecto a la reserva, salvo corrección auditada |

**Precisión de seguridad:** en una cita confirmada, proponer cambio de hora no borra automáticamente el horario confirmado anterior ni lo libera sin una decisión explícita. La alternativa propuesta no bloquea un segundo horario. Si se cancela la cita anterior, el sistema debe reflejarlo y notificarlo, no dejar una falsa cita confirmada. El usuario deberá aprobar la regla exacta de retención del horario original en caso de contrapropuesta.

### 5.2 Invariantes de transición

- Solo recepción/administración confirma y contrapropone, tanto clínica como grooming; el cliente acepta/rechaza; el veterinario/groomer registra su ejecución.
- Cuando un profesional asignado está indisponible, recepción/admin puede reasignar sin cambiar hora notificando al cliente; cambio de fecha/hora requiere aceptación.
- Aceptar contrapropuesta no confirma si el slot ya está ocupado: vuelve a `PENDIENTE` o `PROPUESTA` con explicación y nueva alternativa.
- El cliente cancela directamente solicitudes `PENDIENTE`/`PROPUESTA`, pero para `CONFIRMADA` envía petición cuya resolución pertenece a recepción/administración (regla funcional V1 asignada a recepción/administración).
- Una cita `COMPLETADA` no debe cancelarse retrospectivamente como si nunca se hubiera atendido; sus ajustes administrativos conservarán historial.
- Registrar atención clínica requiere rol veterinario; una consulta puede asociarse a la cita al concluir sin que recepción edite clínica.
- Servicios grooming no quedan automáticamente completados porque haya pasado el horario; profesional registra atención/inasistencia.

## 6. Flujos operativos

### F-B03-01 — Solicitud de cita

Cliente elige mascota propia, tipo de servicio clínico, fecha/hora deseadas, observación general → solicita → estado `PENDIENTE` → recepción/admin revisa agenda, disponibilidad de veterinario, horario abierto y duplicados → asigna profesional y confirma, o contrapropone → cliente acepta → revalidación de slot de manera atómica → confirmación con notificación.

**Errores:** mascota transferida; cita duplicada por reintento; fecha fuera de horario; profesional desactivado; slot ocupado al aceptar; organización suspendida; usuario no verificado.

### F-B03-02 — Reprogramación clínica confirmada

Recepción/admin detecta ausencia → propone otro veterinario sin cambiar horario y reasigna + notifica; **o** propone fecha/hora nueva, exige aceptación → revalida y actualiza una única reserva confirmada → libera horario anterior en la misma transición. Cliente rechaza: personal mantiene/cierra situación según procedimiento, no atribuye aceptación tácita.

### F-B03-03 — Grooming

Cliente selecciona servicio publicado compatible y mascota propia → fecha/hora propuesta → recepción/admin verifica duración y encargado disponible → confirma con empleado asignado o contrapropone → el cliente acepta la alternativa → encargado de grooming marca inicio/completado y notas de servicio, con acceso solo a indicaciones operativas mínimas.

### F-B03-04 — Inasistencias y cancelación

No asistencia: recepción/admin registra `INASISTENCIA` con fecha/motivo; cliente puede ver estado. Solicitud pendiente: cliente cancela directamente. Confirmada: cliente solicita cancelación → recepción/admin decide con justificación → se libera slot si queda cancelada → notificación. **No se crea devolución monetaria automática**; ajustes financieros, si los hubiera, siguen B04/B05.

## 7. Matriz de responsabilidades

| Operación | Cliente | Recepción | Admin | Veterinario | Groomer |
|---|---|---|---|---|---|
| Solicitar cita/grooming mascota propia | Sí | No en su rol aislado | No en su rol aislado | No en su rol aislado | No en su rol aislado |
| Confirmar/contraproponer | No | Sí | Sí | Solo con rol adicional | Solo con rol adicional |
| Reasignar profesional | No | Sí | Sí | Solo con rol adicional | Solo con rol adicional |
| Aceptar contrapropuesta | Sí (propietario solicitante) | No | No | No | No |
| Ver agenda general | Solo propias | Sí | Sí | Solo agenda profesional propia | Solo agenda de tareas propias |
| Registrar atención clínica | No | No | Solo rol veterinario adicional | Sí | No |
| Registrar realización grooming | No | No | Solo con rol grooming adicional | Solo con rol grooming adicional | Sí |
| Cancelar pendiente propia | Sí | Gestión autorizada | Gestión autorizada | No | No |
| Resolver cancelación confirmada | Solicita | Sí (regla funcional V1) | Sí (regla funcional V1) | No | No |

## 8. Requisitos candidatos

| ID | Requisito |
|---|---|
| RF-B03-001 | Gestionar calendarios de disponibilidad, bloqueos y ausencias |
| RF-B03-002 | Publicar servicios con duración y precio referencial |
| RF-B03-003 | Permitir solicitudes de cita clínica sobre mascotas autorizadas |
| RF-B03-004 | Permitir propuestas de fecha/hora y contrapropuestas |
| RF-B03-005 | Exigir aceptación del cliente cuando cambia fecha/hora |
| RF-B03-006 | Revalidar disponibilidad al confirmar y aceptar contrapropuestas |
| RF-B03-007 | Impedir traslapes para un profesional en todos sus roles |
| RF-B03-008 | Asignar veterinario al confirmar cita clínica |
| RF-B03-009 | Permitir reasignación autorizada, notificación y trazabilidad |
| RF-B03-010 | Permitir solicitud/confirmación de grooming por roles autorizados |
| RF-B03-011 | Permitir registro de atención grooming por el encargado |
| RF-B03-012 | Permitir cancelar pendientes y pedir cancelar confirmadas |
| RF-B03-013 | Restringir edición de atenciones clínicas a veterinarios |
| RF-B03-014 | Permitir gestión autorizada de inasistencias y bloqueos |
| RF-B03-015 | Generar notificaciones según cambios válidos de agenda |
| RF-B03-016 | Conservar historial de propuestas, aceptación y cambios |

## 9. Pruebas de aceptación propuestas

| ID | Escenario | Resultado |
|---|---|---|
| AT-B03-01 | Cliente propone fecha sin personal libre | Solicitud pendiente; nunca confirma automáticamente |
| AT-B03-02 | Recepción confirma cita sin veterinario | Denegado |
| AT-B03-03 | Confirmación simultánea de misma franja para un veterinario | Una confirma; otra recibe conflicto/revisión |
| AT-B03-04 | Veterinario con ambos roles tiene consulta y grooming al mismo tiempo | Segunda confirmación denegada |
| AT-B03-05 | Cliente acepta hora propuesta pero fue ocupada | No confirma; propone nuevo horario |
| AT-B03-06 | Cliente rechaza contrapropuesta | No reserva el horario sugerido |
| AT-B03-07 | Cambio solo de veterinario a misma hora | Profesional nuevo disponible; notificación y traza |
| AT-B03-08 | Cambio de fecha sin aprobación del cliente | No cambia fecha confirmada en silencio |
| AT-B03-09 | Groomer intenta confirmar reserva sin rol recepción/admin | Denegado |
| AT-B03-10 | Groomer completa su tarea | Estado completo, sin modificar historia clínica |
| AT-B03-11 | Cliente cancela pendiente | Estado cancelado, no ocupa slot |
| AT-B03-12 | Cliente intenta cancelar confirmada unilateralmente | Se registra solicitud, no cancelación automática |
| AT-B03-13 | Trabajador se desactiva con citas futuras | No asignable; citas existentes visibles para reprogramar |
| AT-B03-14 | Agenda de tenant B consulta cita tenant A | Acceso denegado |
| AT-B03-15 | Reenvío de misma solicitud tras timeout | Una sola solicitud mediante idempotencia |

## 10. Reglas operativas cerradas y garantías pendientes de verificación

- **D-B03-01 (RESUELTO):** DEC-067 exige encargado disponible asignado y agenda unificada por persona.
- **D-B03-02 (CERRADO V1):** conservar horario original solo mientras siga siendo viable; si deja de serlo, marcar pendiente de reprogramación/cancelar expresamente; jamás afirmar cita inválida.
- **D-B03-03 (CERRADO V1):** recepción/admin resuelve cancelaciones de citas y grooming confirmados, con motivo y notificación; cliente solo solicita.
- **G:** pruebas de carrera y bloqueo de slot antes de declarar agenda lista para Codex.

**Condición de cierre:** toda cita confirmada tiene profesional activo y slot válido, cancelaciones y reprogramaciones conservan historial, y atención clínica nunca se edita por grooming/recepción.

## 11. Cierre funcional V1 — agenda, grooming y reprogramaciones

**DEC-067 (aprobación explícita):** recepción/administración solo confirma un servicio de grooming con **encargado activo y disponible asignado** durante todo el intervalo. El encargado ejecuta y registra, pero no confirma por ese rol aislado. La agenda consolidada por persona incluye tareas clínicas y grooming, por lo que dos funciones incompatibles nunca ocupan simultáneamente al mismo trabajador.

**Disponibilidad:** calendario local `America/Lima`, horario semanal, bloqueos/ausencias excepcionales y duración configurable de servicio. Disponibilidad se valida **nuevamente al confirmar**, bajo concurrencia, no solamente al mostrar opciones. Citas propuestas y contrapropuestas no consumen cupo; no hay confirmación por silencio. Intervalos `[inicio, fin)`.

**Reprogramación confirmada:** si el cliente debe aceptar un cambio de fecha u hora, el intervalo original sigue siendo la única cita confirmada **mientras permanezca disponible** y no exista cancelación. La nueva fecha no se bloquea como confirmada. Si el intervalo original dejó de ser viable (ausencia/indisponibilidad), recepción/admin deberá marcar explícitamente `PENDIENTE_REPROGRAMACION` o resolver una cancelación, notificar y evitar mostrar una confirmación ficticia. Tras aceptación del cliente se revalida el nuevo intervalo y se efectúa una sola transición auditada; si el cliente rechaza, no se aplica el nuevo horario.

**Cancelación:** cliente cancela sus solicitudes pendientes; en citas/servicios ya confirmados solicita cancelación; **recepción/admin** decide, libera agenda y notifica. Registrar inasistencia no genera automáticamente cobro, devolución ni atención clínica. Una cita `COMPLETADA` no se cancela retroactivamente; se rectifica la situación mediante actuación auditada si aplica.

**Demostración verificable:** competencia por mismo veterinario/encargado y por un empleado con dos roles: como máximo una confirmación válida. La prohibición también se aplica a reasignación y reprogramación.
