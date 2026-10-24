# V1 integral — progreso verificable

**Rama de trabajo:** `codex/v1-integral`
**Inicio de esta línea:** 2026-10-23
**Último checkpoint funcional anterior:** `f99249d278bc5793e90a5180391d4030641484e7`
(`feat(b02): add owner pet clinical core vertical`)
**Fuente funcional:** `arquitectura_veterinaria_v1_FINAL/` (inmutable)
**Autorización operativa vigente:** instrucción expresa de desarrollo integral
V1 recibida el 2026-10-23. Sustituye únicamente la congelación temporal
anterior; no autoriza remoto, push, despliegue público, datos reales ni cambios
fuera de V1.

## Checkpoint B02 — vinculación digital CLIENT–propietario (2026-10-24)

- V017 `b02 client owner linking` está aplicada una sola vez mediante el lanzador Flyway acotado. Su SHA-256 es `4C21E0DA91A39499F6EF13097FE1185BAE42D99D526F2C0C7465E85D358DCE80` y su checksum Flyway es `337349119`.
- La migración añade, sin funciones privilegiadas ni permisos `PUBLIC`, las tablas tenant-locales de solicitud y vínculo aprobado. Ambas usan RLS; `av_app` no puede borrar, ni crear objetos en los esquemas implicados, ni consultar directamente identidades o hashes.
- CLIENT puede solicitar una revisión sin recibir coincidencias privadas. Recepción o administración revisa la cola, registra método general, decisión, fecha y auditoría; una cuenta y un propietario solo pueden tener un vínculo aprobado compatible.
- El portal CLIENT muestra únicamente fichas básicas de mascotas con responsabilidad vigente. No expone episodios, diagnósticos, narrativas ni prescripciones. Tras una transferencia, la consulta deja de devolver la mascota al CLIENT anterior.
- La integración B02 pasó 9/9 con Java 21 y Flyway desactivado; Angular pasó 9/9, el build y Redocly fueron correctos. Playwright Chrome pasó la regresión STAFF y verificó además la carga de la cola de vinculaciones.
- La semilla CLIENT se volvió a ejecutar de forma idempotente, pero Aurora continúa sin cumplir la política local de contraseña. Se revirtió antes de crear datos, por lo que el E2E CLIENT completo sigue pendiente. No se rebajó la política ni se generaron contraseñas.
- B02 sigue **parcial**: faltan el recorrido CLIENT completo, cuenta VETERINARIO local para E2E clínico, vacunas, prescripciones, rectificaciones, alta/edición CLIENT y gestión de duplicados.

## Estado recuperado

- Git parte del commit consolidado `b333a1602ac1fd6541efcc11b8034a7a7ca06212`
  de `codex/g4-i01`; no hay remoto configurado.
- La especificación V1 conserva 19 archivos físicos y los 18 hashes declarados
  en su manifiesto.
- PostgreSQL local 18.6: `arquitectura_veterinaria` es la única base en alcance.
  Flyway registra V001–V016 una sola vez y correctamente.
- El único cambio preexistente no confirmado es
  `frontend/angular.json`: desactiva la analítica de Angular. Se conserva sin
  atribuirlo a esta línea y queda fuera de cualquier staging hasta confirmar su
  procedencia.
- El hook de Impeccable sigue aislado; no se ejecuta Impeccable ni se descargan
  motores.

## Tablero de ejecución

| Fase | Estado | Resultado verificable siguiente |
|---|---|---|
| Recuperación y control de línea base | Completada | Línea de trabajo creada, respaldo recuperable validado y V013 aplicada con control Flyway. |
| B01 — Administración, identidad y acceso | Parcial | Las interfaces CLIENT de registro, verificación, reenvío y recuperación están conectadas a las rutas B01 existentes. La semilla CLIENT se detiene antes de PostgreSQL: las dos contraseñas locales no cumplen todavía la regla de mayúscula ASCII; por ello falta el E2E autenticado CLIENT. |
| B02 — Propietarios, mascotas y clínica | Parcial | Vertical propietario → mascota → episodio clínico → historial y transferencia de responsable implementado con PostgreSQL, RLS y Angular. El recorrido de navegador STAFF verifica propietario, mascota, transferencia histórica y restricción clínica; la atención desde navegador exige una cuenta sintética VETERINARIO aún no disponible. Vinculación digital, vacunas, recetas, rectificaciones y mascotas CLIENT siguen pendientes. |
| B03 — Citas y grooming | Pendiente | Agenda real, transiciones y prevención de solapes. |
| B04 — Inventario y POS | Pendiente | Productos, movimientos y venta con stock auditable y no negativo. |
| B05 — Tienda y pedidos | Pendiente | Catálogo, carrito, pedido y descuento de stock exactamente una vez al confirmar. |
| B06 — Transversal | Pendiente | Notificaciones, auditoría básica y controles que conectan los verticales anteriores. |

## Migraciones y evidencia

| Versión | Estado actual | Observación |
|---|---|---|
| V001–V012 | Aplicadas y validadas por Flyway | Sus archivos locales permanecen inmutables. |
| V013 | Aplicada una vez | Redefinió exclusivamente `tenant_admin.review_administrative_recovery_for_session`; SHA-256 fuente `4B5F8EF040FCE0A0A8B97C33517E622090500C058A45B2FF45FCE171D2BE584D`, checksum Flyway `-57543870`. |
| V014 | Aplicada una vez | Normalizó solo los dos nombres históricos de tenants sintéticos; SHA-256 fuente `60BAACFE40B1F0B545E093CF7283EFA0CC8E80743BCB033CE7B740F033D45E65`, checksum Flyway `505352357`. |
| V015 | Aplicada una vez | Rota el hash CSRF exclusivamente para una sesión activa autorizada; SHA-256 fuente `7AFF333B71371063F8A31A5F252B4DDF0EA5754ED52AC3FFCD311DD71866BB19`, checksum Flyway `1029927636`. |
| V016 | Aplicada una vez | Crea el núcleo B02 tenant-local (`owner_pet_care`), RLS y privilegios mínimos de `av_app`; SHA-256 fuente `8F8686A56F21930BDF20E56D9F5172FCAF06EF7C1A06EF2023F2434EE4D7B560`, checksum Flyway `893800378`. |

## Evidencia nueva de esta línea

- Recuperación inicial: historial Flyway, constraint de roles, propietarios,
  `search_path`, ACL de funciones y capacidades de roles comprobados el
  2026-10-23.
- V013 se validó primero en `BEGIN`/`ROLLBACK`, se respaldó la base con un dump
  local verificable y después Flyway la aplicó una sola vez.
- V014 se preejecutó en `BEGIN`/`ROLLBACK` (dos filas normalizables, cero tras
  la preejecución y dos restablecidas después del rollback). El respaldo
  recuperable previo quedó fuera de Git y fue validado con `pg_restore --list`;
  tras Flyway, no quedan nombres con la secuencia literal y no hubo cambios de
  privilegios ni de RLS.
- `B01AdministrationDatabaseIntegrationTest`: 30 correctas, 0 fallos, 0
  errores con Flyway desactivado durante la prueba. La regresión de identidad
  y CSRF añadió 3/3 casos correctos: rotación, invalidación del token anterior,
  conservación de la sesión y rechazo tras revocación. No quedaron residuos
  de cuentas ni tenants sintéticos de prueba.
- Vertical B01 de administración de tenant: la pantalla Angular autenticada
  consulta el perfil y las cuentas STAFF reales del tenant y conecta invitación,
  reemplazo de roles y desactivación a los endpoints protegidos existentes.
- Vertical B01 de administración SaaS: inicio de sesión separado, guardia de
  rol `ADMIN_SAAS` y consola Angular conectada a organizaciones, solicitudes
  RUC y recuperaciones administrativas reales. Las formas de suspensión,
  reactivación y revisión requieren el motivo y las confirmaciones que exige
  la API; no se ejecutaron mutaciones E2E contra la semilla para conservarla
  estable. Su comportamiento de servidor queda cubierto por la suite B01.
- Regresión Playwright autenticada: correcta el 2026-10-23 para Aurora y
  Bosque, con carga del panel, lista de STAFF, CSRF en cierre de sesión,
  aislamiento entre tenants, inicio y cierre de sesión SaaS y viewport de
  390 px sin desplazamiento horizontal del documento. Las credenciales
  sintéticas se leyeron solo en memoria.
- Frontend de la consola SaaS: `ng test --watch=false` completó 4 archivos y
  7 pruebas; `ng build` completó con 342.53 kB de salida inicial. La evidencia
  E2E protegida se conserva solo en la carpeta ignorada `.angular/`.
- Agent Browser 0.27.0: inspección pública correcta de la pantalla de acceso
  en `127.0.0.1`; el árbol de accesibilidad detectó `main`, encabezado,
  etiquetas nativas de correo/contraseña, botón de ingreso y cambio de
  contexto. La versión instalada no expone un subcomando `a11y`; no se declara
  una auditoría automática de accesibilidad como ejecutada.
- Vertical CLIENT: la ruta independiente de autenticación usa el endpoint
  contextual de cliente, la guardia niega una sesión STAFF y `/client-space`
  proyecta solo el espacio propio autorizado. Angular actualizó 9/9 pruebas y
  el build; Agent Browser confirmó el formulario con etiquetas nativas. Falta
  una cuenta CLIENT sintética activa para un E2E autenticado sin exponer enlaces
  opacos, además de las interfaces públicas de registro y recuperación.
- El contrato OpenAPI ya no declara como bloqueadas las rutas de reemplazo de
  roles ni de revisión de recuperación administrativa: ambas son compatibles
  con V012 y V013 y tienen cobertura de integración B01.
- V015 superó preflight `BEGIN`/`ROLLBACK` y Flyway. La función
  `identity_auth.rotate_csrf_token` conserva owner `av_schema_owner` no
  superusuario ni `BYPASSRLS`, `search_path` seguro, `PUBLIC` sin ejecución,
  `av_app` con `EXECUTE` mínimo y sin `SELECT`/`UPDATE` directo sobre
  `identity_auth.user_session`. No usa SQL dinámico.
- La restauración Angular llama primero al contexto autenticado y luego a
  `POST /api/v1/auth/session-csrf-token` con una cabecera de intención
  same-origin. La respuesta es `no-store`; el token queda solo en memoria.
  Playwright verificó en STAFF y SaaS la recarga, la cabecera, `no-store`, el
  logout y el rechazo de una renovación tras revocar la sesión. Agent Browser
  verificó la pantalla SaaS, sus etiquetas nativas y el árbol accesible básico.
- La ejecución requiere seleccionar el JDK 21 local por proceso: el `PATH`
  global todavía resuelve una JRE 8. Es un riesgo de reproducibilidad pendiente,
  no una modificación del sistema.
- Riesgo residual de CSRF: la rotación actual mantiene un token válido por
  sesión, por lo que una recarga posterior invalida el token de otra pestaña
  abierta. La pestaña que recarga queda protegida y operativa; una mejora
  futura podría coordinar pestañas sin persistir tokens ni debilitar CSRF.

## Próxima operación concreta

Cuando el responsable actualice las dos contraseñas CLIENT locales para que
incluyan una mayúscula ASCII, ejecutar la semilla idempotente y completar el
E2E autenticado CLIENT. En paralelo, preparar una credencial sintética local de
VETERINARIO mediante el flujo aprobado y continuar B02 con vinculación digital,
vacunas, recetas y rectificaciones.

## Checkpoint CLIENT público 2026-10-23

- Angular incorpora registro de cliente por tenant, verificación/reenvío de
  correo y recuperación/restablecimiento de contraseña. Todos usan los
  endpoints B01 existentes; no se agregó una API de depuración para revelar
  enlaces opacos.
- El adaptador local de enlaces dirige verificación CLIENT a
  `/client/verify-email` y recuperación a `/client/reset-password`. Los
  enlaces siguen existiendo solo en memoria del proceso para pruebas de
  integración; no se registran ni se exponen por HTTP.
- `ng test --watch=false`: 4 archivos y 9 pruebas correctas. `ng build`:
  correcto (367.12 kB de salida inicial). Redocly validó el contrato OpenAPI.
  La integración B01 afectada se ejecutó con Java 21 y Flyway desactivado,
  sin fallos.
- Agent Browser comprobó los formularios de registro y recuperación en
  `127.0.0.1`, incluidos `main`, encabezados, etiquetas visibles, campos
  nativos y acciones navegables. La versión 0.27.0 no tiene subcomando
  automático `a11y`.
- Se incorporó un fixture CLIENT separado, transaccional e idempotente, que
  utiliza las funciones de registro y verificación ya auditadas. Las cuatro
  variables CLIENT existen en `.env.local`, pero ambas contraseñas fallan la
  regla de mayúscula ASCII; el preflight se detuvo antes de PostgreSQL, no creó
  cuentas y no registró secretos.

## Checkpoint B02 núcleo 2026-10-23

- V016 establece tablas `owner_profile`, `pet`, `pet_ownership_period`,
  `clinical_episode` y `care_audit_event` dentro de `owner_pet_care`. No crea
  funciones privilegiadas ni permisos `PUBLIC`; todas las tablas están bajo
  RLS y sus consultas se ejecutan con el contexto tenant confiable de la
  sesión.
- La interfaz `/care` permite a ADMIN_VETERINARIA o RECEPCION registrar y
  consultar propietarios, crear mascotas con responsable actual y abrir su
  ficha. Solo VETERINARIO puede crear, finalizar o leer el historial clínico;
  la pantalla muestra la denegación de forma explícita a los demás roles.
- La integración PostgreSQL verificó episodios clínicos por veterinario,
  finalización con campos mínimos, ausencia de lectura desde Bosque y el
  rechazo de un administrador sin rol VETERINARIO. La regresión E2E STAFF
  verificó persistencia real de propietario/mascota, asociación y respuesta
  403 para el historial clínico.
- RF-B02-001, RF-B02-002 y RF-B02-016 tienen evidencia implementada y
  verificada para el núcleo; RF-B02-008, RF-B02-009 y RF-B02-013 están
  parcialmente verificados. Los demás RF-B02 permanecen pendientes y no se
  presentan como completados.

## Checkpoint B02 transferencia 2026-10-24

- `POST /api/v1/pets/{petId}/owner-transfers` exige recepción o administración,
  verificación manual, motivo, responsable esperado y un nuevo propietario del
  mismo tenant. Bloquea la mascota, cierra el período actual, abre el siguiente
  y registra auditoría en una transacción JDBC.
- `GET /api/v1/pets/{petId}/ownership-periods` muestra trazabilidad solo a los
  roles de registro autorizados. La transferencia no expone historia clínica ni
  modifica los autores de episodios.
- La regresión B02 pasó 6/6 con Java 21; Angular pasó 9/9 y `ng build`. Redocly
  validó el contrato; Playwright Chrome confirmó la transferencia real,
  exactamente un responsable vigente y la denegación clínica para ADMIN.
- RF-B02-007 y RF-B02-015 quedan verificados. B02 continúa parcial: faltan
  vinculación digital, vacunación, prescripción, rectificaciones y consultas
  permitidas desde CLIENT.
