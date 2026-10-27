# Trazabilidad B03 — agenda, citas y grooming

**Fecha de actualización:** 2026-10-25
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
