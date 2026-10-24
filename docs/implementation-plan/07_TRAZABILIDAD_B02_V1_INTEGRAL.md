# Trazabilidad B02 - vertical integral inicial

**Fecha:** 2026-10-23
**Fuente de verdad:** `arquitectura_veterinaria_v1_FINAL/B02_PROPIETARIOS_MASCOTAS_CLINICA.md` (inmutable).
**Alcance de este checkpoint:** propietario presencial, mascota con responsable principal, episodio clínico y aislamiento tenant. No cambia la especificación V1.

| Requisito | Estado | Evidencia actual | Pendiente para cierre V1 |
|---|---|---|---|
| RF-B02-001 | Verificado | `POST/GET /owners`, formulario Angular y E2E STAFF; permite alta presencial sin cuenta. | Edición administrativa. |
| RF-B02-002 | Verificado | `POST/GET /owners/{ownerId}/pets`, ficha Angular, asociación persistente y E2E. | Edición y estados posteriores de mascota. |
| RF-B02-003 | Pendiente | No existe todavía ficha B02 en portal CLIENT. | Alta/edición básica propia con controles tenant. |
| RF-B02-004 | Pendiente | No se implementó solicitud de vinculación. | Solicitud sin revelar coincidencias privadas. |
| RF-B02-005 | Pendiente | No se implementó revisión manual. | Decisión, comprobador, fecha y resultado auditado. |
| RF-B02-006 | Pendiente | No existe vínculo digital B02. | Impedir vínculos contradictorios y repeticiones. |
| RF-B02-007 | Pendiente | Solo existe la responsabilidad inicial. | Transferencia atómica e historial inalterable. |
| RF-B02-008 | Parcial verificado | Política de rol, integración de creación/finalización y 403 para ADMIN sin VETERINARIO. | Correcciones clínicas, E2E con cuenta VETERINARIO y vacunas/recetas. |
| RF-B02-009 | Parcial verificado | Episodio con veterinario, fecha, estado y cita opcional; integración de borrador/finalización. | Recorrido UI con VETERINARIO y cobertura de cita real B03. |
| RF-B02-010 | Pendiente | No hay entidad ni pantalla de vacunación. | Evento, dosis y próximo recordatorio. |
| RF-B02-011 | Pendiente | No hay prescripción básica. | Instrucciones y trazabilidad de veterinario. |
| RF-B02-012 | Pendiente | No hay adendas para episodios finalizados. | Rectificación atribuible sin sobrescritura. |
| RF-B02-013 | Parcial verificado | Historia denegada a ADMIN sin VETERINARIO y cross-tenant; CLIENT no recibe historia. | Datos B02 permitidos para CLIENT y pruebas específicas de portal. |
| RF-B02-014 | Pendiente | No hay señalización ni revisión de duplicados. | Detección humana sin fusión automática. |
| RF-B02-015 | Pendiente | Período inicial de responsabilidad persistido. | Historial de transferencias y consulta de cambios. |
| RF-B02-016 | Verificado | RLS, contexto JDBC confiable, prueba Aurora/Bosque y 404 genérico por ID de otra veterinaria. | Repetir para los subflujos B02 restantes al implementarlos. |

## Verificaciones del checkpoint

- `mvnw.cmd -Dtest=B02AccessPolicyTest,OwnerPetCareDatabaseIntegrationTest test`: 6/6 correctas con Java 21; las pruebas de integración son transaccionales y se revierten.
- `npm.cmd test -- --watch=false`: 4 archivos y 9 pruebas correctas.
- `npm.cmd run build`: Angular compiló correctamente.
- `npm.cmd run validate:contract`: Redocly validó `docs/contracts/openapi-v1.yaml`.
- `npm.cmd run test:e2e:b02`: Chrome real confirmó el flujo STAFF propietario -> mascota y el rechazo de historia clínica para el rol no veterinario.

## Riesgos y límite vigente

No hay cuenta sintética VETERINARIO activa en Aurora ni Bosque; por tanto, la pantalla clínica no se declara validada por navegador. La evidencia de integración sí confirma el flujo de veterinario contra PostgreSQL. No se han implementado ni probado vínculo digital, transferencias, vacunas, recetas, adendas o exposición CLIENT de datos B02.
