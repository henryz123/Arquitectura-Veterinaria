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
| RF-B02-014 | Parcial verificado | Señales tenant-locales, expediente append-only, resolución explícita y UI STAFF para detectar, registrar y descartar. La integración comprueba confirmación, inactivación condicionada y evaluación VETERINARIO previa cuando la mascota tiene clínica. | E2E VETERINARIO y ADMINISTRACIÓN de confirmación; no hay fusión automática. |
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

## Actualización verificable — registros clínicos inmutables (2026-10-24)

Esta actualización prevalece sobre los pendientes iniciales de RF-B02-008 a
RF-B02-012. No modifica la especificación V1.

| Requisito | Estado actualizado | Evidencia y límite actual |
|---|---|---|
| RF-B02-008 | Parcial verificado | Backend exige `VETERINARIO` para vacunas, indicaciones, adendas y sus lecturas. La integración rechaza ADMIN sin ese rol. Falta E2E de navegador con una cuenta VETERINARIO persistente local. |
| RF-B02-009 | Parcial verificado | Episodios espontáneos conservan autor, fecha y finalización; vacunas e indicaciones pueden asociarse a un episodio tenant-local. Falta integración futura con cita real de B03. |
| RF-B02-010 | Parcial verificado | V018, API Angular y prueba PostgreSQL cubren vacuna, dosis/lote opcionales, próxima dosis y la idempotencia de `operationId`. Falta E2E clínico con el rol correcto. |
| RF-B02-011 | Parcial verificado | V018 registra medicamento, presentación, dosis, vía, frecuencia, duración, instrucciones, fecha y veterinario. La UI declara que no es receta electrónica legal. Falta E2E clínico con el rol correcto. |
| RF-B02-012 | Parcial verificado | V018 conserva adendas atribuibles para episodios finalizados, captura la instantánea previa en SQL y niega `UPDATE`/`DELETE` a `av_app`. La integración comprueba que el episodio finalizado permanece finalizado. Falta E2E clínico con el rol correcto. |
| RF-B02-013 | Parcial verificado | CLIENT continúa limitado a fichas básicas y no recibe episodios, diagnósticos, indicaciones ni adendas. Personal sin VETERINARIO recibe denegación. Falta E2E CLIENT autenticado por la semilla local pendiente. |
| RF-B02-016 | Verificado | FKs compuestas, RLS, contexto JDBC y prueba Aurora/Bosque protegen los nuevos registros contra IDs manipulados entre tenants. |

### Evidencia del checkpoint V018

- SHA-256 `5985485A186E43983313F31D773FD12B9E78D0625A96A7F59D10BACB042D5E1B`; Flyway la registró una sola vez con checksum `1790675663`.
- Preflight transaccional y revisión independiente: sin `SECURITY DEFINER`, SQL dinámico ni permisos `PUBLIC`; `av_app` conserva únicamente `SELECT/INSERT`, mientras que RLS está activa en las tres tablas.
- `mvnw.cmd '-Dtest=OwnerPetCareDatabaseIntegrationTest,B02AccessPolicyTest' test`: 10/10 correctas con Java 21, Flyway desactivado y rollback de fixtures sintéticos.
- Angular: `npm.cmd test -- --watch=false` 4 archivos/9 pruebas y `npm.cmd run build` correctos. `npm.cmd run validate:contract` validó el contrato OpenAPI.
- `npm.cmd run test:e2e:b02`: `B02_OWNER_PET_BROWSER_E2E=PASS` tras V018 para el recorrido STAFF ya consolidado. No sustituye el E2E clínico VETERINARIO pendiente.
- Agent Browser comprobó la estructura pública CLIENT sin autenticación. La versión 0.27 no proporciona un subcomando `a11y`, por lo que la auditoría automática de accesibilidad no se marca como ejecutada.

## Actualizacion verificable - autoservicio CLIENT de ficha basica (2026-10-24)

Esta actualizacion prevalece sobre los limites anteriores de RF-B02-003 y RF-B02-013. No modifica la especificacion funcional V1.

| Requisito | Estado actualizado | Evidencia y limite actual |
|---|---|---|
| RF-B02-003 | Parcial verificado | CLIENT vinculado puede consultar/editar su ficha basica y crear/editar mascotas propias con Angular -> Spring Boot -> PostgreSQL. El servidor resuelve el propietario desde la sesion, no desde un ID de navegador. Falta E2E autenticado con semilla CLIENT local valida. |
| RF-B02-013 | Parcial verificado | Las respuestas CLIENT solo incluyen la proyeccion basica de mascota. Las nuevas rutas no exponen episodios, diagnosticos, prescripciones, adendas, transferencias ni cambios de estado. La integracion rechaza la historia clinica para CLIENT; falta E2E autenticado. |
| RF-B02-016 | Verificado | La integracion prueba que CLIENT de Bosque no consulta mascota de Aurora y que el CLIENT anterior no edita tras una transferencia. RLS y predicados tenant-locales siguen vigentes. |

### Evidencia del checkpoint de autoservicio

- Sin migracion adicional: V001-V018 no se modificaron ni se ejecutaron de nuevo.
- `mvnw.cmd '-Dtest=OwnerPetCareDatabaseIntegrationTest,B02AccessPolicyTest' test`: 11/11 correctas con Java 21, Flyway desactivado y rollback transaccional de fixtures.
- `npm.cmd test -- --watch=false`: 4 archivos / 9 pruebas correctas. `npm.cmd run build`: correcto. `npm.cmd run validate:contract`: OpenAPI valido.
- La comprobacion posterior de cuentas transitorias CLIENT/VETERINARIO de pruebas devolvio 0. No se imprimieron contrasenas, tokens ni enlaces.
- E2E CLIENT queda bloqueado de forma explicita: las dos variables de contrasena CLIENT locales no cumplen la politica. No se rebajo la politica, no se ejecuto la semilla y no se declara evidencia de navegador para este vertical.

## Actualización verificable — revisión humana de posibles duplicados (2026-10-24)

Esta actualización prevalece sobre el estado inicial de RF-B02-014. No modifica la
especificación funcional V1 ni introduce fusión automática, sustitución de
identificadores, transferencia automática ni eliminación de historia clínica.

| Requisito | Estado actualizado | Evidencia y límite actual |
|---|---|---|
| RF-B02-014 | Parcial verificado | `GET` de candidatos tenant-locales, creación/lista de expediente y resolución explícita se integran en Angular para recepción/administración. Playwright Chrome crea y descarta un expediente real. La integración PostgreSQL prueba que un propietario no se inactiva si mantiene responsabilidad o vínculo CLIENT, que una mascota con episodio exige evaluación VETERINARIO y que Bosque no puede resolver un expediente de Aurora. Falta E2E con cuenta VETERINARIO para registrar evaluación y confirmar la inactivación desde interfaz. |
| RF-B02-008 | Parcial verificado | La evaluación de posible duplicado de mascota exige rol `VETERINARIO`; administración sin permiso clínico no puede sustituirla. La integración cubre el rechazo y la secuencia aprobada. Falta recorrido de navegador con cuenta VETERINARIO sintética local. |
| RF-B02-013 | Parcial verificado | No se añaden rutas CLIENT para casos de duplicidad ni se expone información clínica, de otros propietarios o de otra veterinaria. CLIENT sigue limitado a la proyección básica ya documentada. |
| RF-B02-016 | Verificado | RLS, FKs tenant-locales, sesión resuelta en backend y el rechazo genérico de Bosque ante un `reviewId` de Aurora protegen este subflujo contra acceso cross-tenant. |

### Evidencia del checkpoint V019

- V019 `b02 human duplicate review`: SHA-256 `5F197536F4C0B7043B06E1C1246C50EB5C2CC61513D57DFB1B99378BC9BCF8FF`; Flyway la registró una sola vez con checksum `887362817`.
- Las cinco tablas de revisión son append-only, tienen RLS y propietario `av_schema_owner` sin superusuario ni `BYPASSRLS`. `PUBLIC` no tiene permisos y `av_app` conserva solamente `SELECT/INSERT`, sin `UPDATE` ni `DELETE`.
- No hay `SECURITY DEFINER`, funciones, SQL dinámico ni permisos globales nuevos. La pareja normalizada de registros impide dos expedientes para los mismos candidatos invirtiendo el orden. La decisión V1 es conservar un único expediente inmutable por pareja; no existe reapertura automática.
- Las operaciones de evaluación y resolución se serializan dentro de su transacción mediante bloqueo asesor por expediente. `ON CONFLICT DO NOTHING` transforma un reintento ya resuelto o duplicado en un resultado de dominio controlado, sin depender de una excepción SQL que invalide la transacción.
- `mvnw.cmd '-Dtest=OwnerPetCareDatabaseIntegrationTest,B02AccessPolicyTest' test`: 13/13 correctas con Java 21, Flyway desactivado y rollback transaccional. Incluye detección, descarte, rechazo de inactivación parcial, evaluación VETERINARIO, inactivación legítima, rechazo clínico sobre mascota inactiva y aislamiento Aurora/Bosque.
- `npm.cmd test -- --watch=false`: 4 archivos/9 pruebas correctas. `npm.cmd run build`: correcto. `npm.cmd run validate:contract`: OpenAPI válido.
- `npm.cmd run test:e2e:b02`: `B02_OWNER_PET_BROWSER_E2E=PASS`; el navegador real prueba candidatos, creación de expediente y descarte persistente. No sustituye el E2E VETERINARIO de evaluación/confirmación pendiente.

## Actualización verificable — CLIENT autenticado y ficha básica (2026-10-24)

Esta actualización prevalece sobre los límites anteriores de RF-B02-003 a
RF-B02-006 y RF-B02-013. No modifica la especificación funcional V1.

| Requisito | Estado actualizado | Evidencia y límite actual |
|---|---|---|
| RF-B02-003 | Verificado | CLIENT autenticado de Aurora actualiza solamente su perfil básico, crea una mascota propia y edita su ficha desde Angular. El servidor resuelve el propietario desde sesión y vínculo aprobado, y la prueba Chrome verifica la persistencia. |
| RF-B02-004 | Verificado | CLIENT solicita revisión manual desde la pantalla privada sin recibir coincidencias ni datos de terceros. El E2E espera el `201` real y conserva solo la referencia opaca de la solicitud. |
| RF-B02-005 | Verificado | ADMINISTRACIÓN resuelve desde `/care` la solicitud real, selecciona el propietario local, declara el método general y confirma la verificación manual. CLIENT recibe acceso recién después de la decisión. |
| RF-B02-006 | Verificado | La integración mantiene las restricciones de unicidad y el E2E verifica que la cuenta obtiene un único vínculo aprobado antes de acceder a perfil y mascotas. La semilla repetida conserva ambas cuentas sintéticas sin duplicación. |
| RF-B02-013 | Verificado | La proyección CLIENT no incluye historia clínica y `GET /pets/{id}/clinical-episodes` devuelve `403`. Tras una transferencia, `GET /client/pets/{id}` devuelve `404` al responsable anterior; un CLIENT de Bosque recibe `404` para el identificador Aurora. |
| RF-B02-016 | Verificado | El recorrido navegador comprueba aislamiento en dos contextos de Chrome Aurora/Bosque, además de las RLS, FKs y pruebas de integración ya existentes. |

### Evidencia del checkpoint CLIENT

- Se corrigieron dos expresiones de vigencia en `database/demo/seed-client-synthetic.psql`: en una transacción única, el registro CLIENT usa `current_timestamp + interval '24 hours'`, que coincide con el límite de la función existente. No se modificó V001–V019 ni se amplió ningún privilegio.
- La semilla transaccional activó las cuentas sintéticas con los flujos de registro y verificación existentes. Una segunda ejecución confirmó idempotencia: ambas cuentas se conservaron sin cambios.
- `npm.cmd run test:e2e:client-b02`: `CLIENT_B02_BROWSER_E2E=PASS` con Chrome real. Comprueba vínculo manual, sesión tras recarga, actualización de perfil, creación/edición de mascota, denegación clínica, logout, revocación tras transferencia y aislamiento Aurora/Bosque.
- Angular mantiene 4 archivos/9 pruebas, build correcto y OpenAPI válido. El recorrido usa Angular → Spring Boot → PostgreSQL; no sustituye la validación clínica VETERINARIO pendiente.

## Actualización verificable — edición administrativa presencial (2026-10-24)

Esta actualización completa la edición básica pendiente de RF-B02-001; no modifica la especificación funcional ni crea una migración.

| Requisito | Estado actualizado | Evidencia y límite actual |
|---|---|---|
| RF-B02-001 | Verificado | `PUT /owners/{ownerId}` usa la sesión STAFF y el contexto tenant confiable, permite a ADMIN_VETERINARIA o RECEPCION corregir nombre, contacto y dirección de una ficha visible. Conserva estado, origen, mascotas, responsabilidad e historia clínica; genera `OWNER_UPDATED` en auditoría. La integración prueba la actualización y devuelve `404` genérico a Bosque para el identificador Aurora. |
| RF-B02-016 | Verificado | La actualización pasa por RLS y por la política de rol de registro existente. La regresión B02 confirma que una sesión de otra veterinaria no puede modificar ni recuperar la ficha. |

### Evidencia del checkpoint de edición

- No se alteraron V001–V019, el esquema, roles ni privilegios. La actualización SQL parametrizada modifica solo los cinco datos básicos editables y `updated_at`; conserva `origin` y no admite cambios de estado, responsable o contenido clínico.
- `mvnw.cmd -Dtest=OwnerPetCareDatabaseIntegrationTest,B02AccessPolicyTest test`: 13/13 correctas con Java 21, PostgreSQL local y Flyway desactivado. La regresión fue primero roja por ausencia de la operación y verde tras implementar el servicio.
- Angular: 4 archivos/9 pruebas correctas, build correcto y Redocly validó el contrato con la operación `PUT`.
- `npm.cmd run test:e2e:b02`: `B02_OWNER_PET_BROWSER_E2E=PASS` con Chrome real. Selecciona una ficha STAFF, entra en modo edición, persiste `PUT`, comprueba la dirección resultante y continúa las regresiones de mascota, transferencia, períodos e historia clínica denegada al administrador sin rol VETERINARIO.

## Actualización verificable — edición básica STAFF de mascota (2026-10-24)

Esta actualización completa la edición básica pendiente de RF-B02-002; no crea una migración ni altera la relación de responsabilidad.

| Requisito | Estado actualizado | Evidencia y límite actual |
|---|---|---|
| RF-B02-002 | Verificado | `PUT /pets/{petId}` está limitado a ADMIN_VETERINARIA o RECEPCION del tenant. Solo actualiza nombre, especie, raza, sexo, edad/fecha y señas; conserva responsable principal, condición y contenido clínico. La integración comprueba persistencia y `404` cross-tenant; Chrome edita la ficha desde `/care` y verifica la respuesta persistida. |
| RF-B02-016 | Verificado | El servicio establece contexto JDBC confiable antes de bloquear y actualizar la fila tenant-local. Las pruebas cubren el rechazo de Bosque para el identificador Aurora y el E2E conserva el responsable esperado después del `PUT`. |

### Evidencia del checkpoint de mascota

- No se modificaron V001–V019, RLS, roles ni permisos. La consulta SQL es parametrizada y excluye `current_owner_id`, `status` e historia clínica; el servicio detecta cualquier cambio inesperado de responsable o condición antes de cerrar la transacción.
- `mvnw.cmd -Dtest=OwnerPetCareDatabaseIntegrationTest,B02AccessPolicyTest test`: 13/13 correctas con Java 21, PostgreSQL local y Flyway desactivado. La regresión se introdujo primero en rojo y confirmó edición, conservación de responsable y aislamiento.
- Angular: 4 archivos/9 pruebas correctas, build correcto y Redocly validó `PUT /pets/{petId}`.
- `npm.cmd run test:e2e:b02`: `B02_OWNER_PET_BROWSER_E2E=PASS` con Chrome real. La interfaz edita una mascota STAFF, valida la marca persistida y continúa con transferencia, períodos históricos e historia clínica restringida.

## Verificaciones del checkpoint

- `mvnw.cmd -Dtest=B02AccessPolicyTest,OwnerPetCareDatabaseIntegrationTest test`: 6/6 correctas con Java 21; las pruebas de integración son transaccionales y se revierten.
- `npm.cmd test -- --watch=false`: 4 archivos y 9 pruebas correctas.
- `npm.cmd run build`: Angular compiló correctamente.
- `npm.cmd run validate:contract`: Redocly validó `docs/contracts/openapi-v1.yaml`.
- `npm.cmd run test:e2e:b02`: Chrome real confirmó el flujo STAFF propietario -> mascota y el rechazo de historia clínica para el rol no veterinario.
- La transferencia real desde Angular confirmó dos períodos históricos, un único responsable vigente, CSRF, rechazo de historial clínico para ADMIN sin VETERINARIO y repetibilidad del fixture sintético.

## Riesgos y límite vigente

No hay cuenta sintética VETERINARIO activa en Aurora ni Bosque; por tanto, la pantalla clínica, vacunas, indicaciones, adendas y confirmación clínica de duplicados no se declaran validadas por navegador. La evidencia de integración sí confirma el flujo de veterinario contra PostgreSQL. La edición administrativa presencial de propietario ya cuenta con vertical completo y evidencia de navegador.
