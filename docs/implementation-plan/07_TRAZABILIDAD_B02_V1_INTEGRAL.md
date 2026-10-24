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
| RF-B02-007 | Verificado | `POST /pets/{petId}/owner-transfers`, verificación manual obligatoria, bloqueo de fila, responsable esperado y E2E Chrome. | Integrar el cambio de control al portal CLIENT al completar la vinculación digital. |
| RF-B02-008 | Parcial verificado | Política de rol, integración de creación/finalización y 403 para ADMIN sin VETERINARIO. | Correcciones clínicas, E2E con cuenta VETERINARIO y vacunas/recetas. |
| RF-B02-009 | Parcial verificado | Episodio con veterinario, fecha, estado y cita opcional; integración de borrador/finalización. | Recorrido UI con VETERINARIO y cobertura de cita real B03. |
| RF-B02-010 | Pendiente | No hay entidad ni pantalla de vacunación. | Evento, dosis y próximo recordatorio. |
| RF-B02-011 | Pendiente | No hay prescripción básica. | Instrucciones y trazabilidad de veterinario. |
| RF-B02-012 | Pendiente | No hay adendas para episodios finalizados. | Rectificación atribuible sin sobrescritura. |
| RF-B02-013 | Parcial verificado | Historia denegada a ADMIN sin VETERINARIO y cross-tenant; CLIENT no recibe historia. | Datos B02 permitidos para CLIENT y pruebas específicas de portal. |
| RF-B02-014 | Pendiente | No hay señalización ni revisión de duplicados. | Detección humana sin fusión automática. |
| RF-B02-015 | Verificado | Períodos persistidos, cierre del anterior, apertura del nuevo y `GET /pets/{petId}/ownership-periods` con E2E real. | Conservar la trazabilidad al integrar vinculación digital. |
| RF-B02-016 | Verificado | RLS, contexto JDBC confiable, prueba Aurora/Bosque, rechazo de propietario externo y 404 genérico por ID de otra veterinaria. | Repetir para los subflujos B02 restantes al implementarlos. |

## Actualización verificable — vínculo digital CLIENT–propietario (2026-10-24)

Esta actualización prevalece sobre los estados iniciales de RF-B02-003 a
RF-B02-006 y RF-B02-013 indicados arriba. No modifica la especificación V1.

| Requisito | Estado actualizado | Evidencia y límite actual |
|---|---|---|
| RF-B02-003 | Parcial verificado | CLIENT autenticado puede consultar fichas básicas vigentes tras una vinculación aprobada. Siguen pendientes alta/edición básica por CLIENT y E2E autenticado con la semilla controlada. |
| RF-B02-004 | Parcial verificado | `POST /client/owner-link-requests`, estado propio y pantalla CLIENT implementados. La solicitud no recibe propietario, correo ni coincidencias privadas. La semilla CLIENT de Aurora abortó antes de PostgreSQL por política de contraseña, por lo que falta el recorrido de navegador CLIENT real. |
| RF-B02-005 | Parcial verificado | Recepción/administración consulta la cola tenant-local y resuelve con propietario, método general, comprobación explícita, fecha, resultado y auditoría. La integración PostgreSQL lo prueba; el E2E STAFF verifica que la cola real carga, pero no puede completar la revisión sin una cuenta CLIENT sintética activa. |
| RF-B02-006 | Parcial verificado | V017 usa FKs tenant-locales, RLS, una solicitud pendiente por cuenta y unicidad de cuenta, propietario y solicitud aprobada. La integración verifica el rechazo de un segundo CLIENT para el mismo propietario. Falta el E2E completo CLIENT → STAFF → CLIENT. |
| RF-B02-013 | Parcial verificado | `GET /client/pets` proyecta solo ficha básica vigente y no incluye episodios, diagnósticos, narrativas ni prescripciones. La protección clínica STAFF existente permanece verificada; falta acceso CLIENT real de navegador. |
| RF-B02-016 | Verificado | RLS, FKs compuestas, consulta con contexto confiable y prueba de que administración de Bosque no puede resolver una solicitud de Aurora. |

### Evidencia del checkpoint

- V017 `b02 client owner linking`: Flyway la registró una sola vez con checksum `337349119`; SHA-256 del archivo `4C21E0DA91A39499F6EF13097FE1185BAE42D99D526F2C0C7465E85D358DCE80`.
- PostgreSQL confirma propietario `av_schema_owner` sin privilegios `PUBLIC`, RLS en ambas tablas y `av_app` limitado a `SELECT/INSERT` y solo el `UPDATE` indispensable sobre solicitudes; no recibe `DELETE` ni puede crear objetos en los esquemas involucrados.
- `mvnw.cmd '-Dtest=OwnerPetCareDatabaseIntegrationTest,B02AccessPolicyTest' test`: 9/9 correctas con Java 21 y Flyway desactivado. Las pruebas son transaccionales y se revierten.
- `npm.cmd test -- --watch=false`: 4 archivos y 9 pruebas correctas. `npm.cmd run build`: correcto. Redocly validó `docs/contracts/openapi-v1.yaml`.
- `npm.cmd run test:e2e:b02`: `B02_OWNER_PET_BROWSER_E2E=PASS`; comprueba persistencia STAFF, transferencia y la carga HTTP 200 de la cola de vinculaciones.
- Agent Browser verificó la estructura accesible de la pantalla pública CLIENT: `main`, encabezado, etiquetas nativas de correo/contraseña y acciones de registro/recuperación. La versión instalada no ofrece `agent-browser a11y`, por ello no se declara una auditoría automática de accesibilidad.
- La semilla CLIENT local se ejecutó con el procedimiento controlado y se revirtió completamente porque Aurora no cumple la política de contraseña. No se creó cuenta ni se expusieron secretos.

## Verificaciones del checkpoint

- `mvnw.cmd -Dtest=B02AccessPolicyTest,OwnerPetCareDatabaseIntegrationTest test`: 6/6 correctas con Java 21; las pruebas de integración son transaccionales y se revierten.
- `npm.cmd test -- --watch=false`: 4 archivos y 9 pruebas correctas.
- `npm.cmd run build`: Angular compiló correctamente.
- `npm.cmd run validate:contract`: Redocly validó `docs/contracts/openapi-v1.yaml`.
- `npm.cmd run test:e2e:b02`: Chrome real confirmó el flujo STAFF propietario -> mascota y el rechazo de historia clínica para el rol no veterinario.
- La transferencia real desde Angular confirmó dos períodos históricos, un único responsable vigente, CSRF, rechazo de historial clínico para ADMIN sin VETERINARIO y repetibilidad del fixture sintético.

## Riesgos y límite vigente

No hay cuenta sintética VETERINARIO activa en Aurora ni Bosque; por tanto, la pantalla clínica no se declara validada por navegador. La evidencia de integración sí confirma el flujo de veterinario contra PostgreSQL. Siguen pendientes vínculo digital, vacunas, recetas, adendas y exposición CLIENT de datos B02.
