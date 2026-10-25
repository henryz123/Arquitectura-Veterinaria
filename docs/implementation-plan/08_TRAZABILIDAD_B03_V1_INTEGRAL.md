# Trazabilidad B03 — agenda, citas y grooming

**Fecha de actualización:** 2026-10-25
**Fuente de verdad:** `arquitectura_veterinaria_v1_FINAL/B03_CITAS_Y_GROOMING.md` (inmutable).
**Checkpoint cubierto:** catálogo de servicios, horario operativo y disponibilidad profesional. B03 no está cerrado.

| Requisito | Estado | Evidencia verificable | Pendiente para cierre V1 |
|---|---|---|---|
| RF-B03-001 | Parcial verificado | V022 persiste horario semanal local y disponibilidad de profesionales con RLS; Angular permite registrar/listar ambos; E2E lo confirma. | Bloqueos, ausencias, edición y su efecto sobre reservas futuras. |
| RF-B03-002 | Parcial verificado | V022 y `/schedule` crean/listan servicios clínica/grooming con duración por 15 minutos, precio referencial, publicación y estado; E2E persiste un servicio real. | Edición visual, inactivación y consumo solo de servicios publicados desde CLIENT. |
| RF-B03-003 | Pendiente | No existe solicitud de cita desde CLIENT ni STAFF. | Validar mascota/responsabilidad, fecha y clave de idempotencia. |
| RF-B03-004 | Pendiente | Solo existe el modelo persistente de propuesta. | API/UI de contrapropuesta e historial visible. |
| RF-B03-005 | Pendiente | No existe aceptación CLIENT. | Revalidación al aceptar y rechazo sin reserva. |
| RF-B03-006 | Pendiente | El modelo de reserva está preparado; no hay confirmación. | Revalidación dentro de transacción, conflicto y rollback. |
| RF-B03-007 | Pendiente | V022 indexa reservas vigentes, pero aún no hay operación de confirmación. | Serialización por profesional y prueba concurrente de ambos roles. |
| RF-B03-008 | Parcial verificado | V020 y el servicio revalidan profesionales activos con rol `VETERINARIO`/`GROOMING`; UI no expone identidad sensible. | Obligación de veterinario al confirmar una cita clínica. |
| RF-B03-009 | Pendiente | Existe tabla de eventos de agenda sin escritor funcional. | Reasignación, aviso y traza para cambios autorizados. |
| RF-B03-010 | Parcial verificado | Catálogo y disponibilidad admiten `GROOMING`; el selector aplica rol vigente. | Solicitud, confirmación y contrapropuesta grooming. |
| RF-B03-011 | Pendiente | Existe tabla `grooming_attention`, sin API/UI. | Inicio, finalización y notas operativas por encargado. |
| RF-B03-012 | Pendiente | El modelo contempla estados, sin transiciones funcionales. | Cancelación directa pendiente y solicitud de cancelación confirmada. |
| RF-B03-013 | Pendiente | B02 mantiene la restricción clínica por veterinario. | Asociar atención clínica a una cita sin ampliar acceso de recepción/grooming. |
| RF-B03-014 | Pendiente | V022 persiste bloqueos, sin gestión ni inasistencia. | Gestión autorizada y efecto sobre reservas. |
| RF-B03-015 | Pendiente | El evento reserva `notification_emitted_at`, sin mecanismo de emisión. | Notificaciones conforme a cambios válidos. |
| RF-B03-016 | Pendiente | V022 conserva tablas de propuesta, reserva y evento. | Escritores, consulta protegida y evidencia de trazabilidad en flujos. |

## Evidencia del checkpoint

- V022 aplicada una sola vez; SHA-256 fuente `1CCDE1A6BDD3512371CC59D21BCE7D61182C22BCA3EC3FC5651285EB6391929D`, checksum Flyway `2011436639`.
- La auditoría de SQL, preflight reversible, ACL, RLS y catálogo se registran en `docs/audits/B03_V022_PREAUDITORIA_Y_VERIFICACION.md`.
- Backend: 6/6 pruebas de política y servicio B03 correctas.
- Angular: 11/11 pruebas correctas, build correcto con advertencia de presupuesto inicial no bloqueante.
- OpenAPI: Redocly válido.
- Playwright: `B03_SERVICES_AVAILABILITY_BROWSER_E2E=PASS`, incluida la separación Aurora/Bosque.

## Próxima operación concreta

Implementar `SolicitudAgenda` usando exclusivamente servicios publicados y mascotas autorizadas, luego confirmar una reserva mediante serialización por profesional y pruebas de conflicto antes de construir el calendario de citas.
