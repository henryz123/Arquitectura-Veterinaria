# V1 integral — progreso verificable

**Rama de trabajo:** `codex/v1-integral`
**Inicio de esta línea:** 2026-10-23
**Último checkpoint funcional:** `300819814dcd0c021c2b5c12206ca1020f97132f`
(`feat(b01): add client access workspace`)
**Fuente funcional:** `arquitectura_veterinaria_v1_FINAL/` (inmutable)
**Autorización operativa vigente:** instrucción expresa de desarrollo integral
V1 recibida el 2026-10-23. Sustituye únicamente la congelación temporal
anterior; no autoriza remoto, push, despliegue público, datos reales ni cambios
fuera de V1.

## Estado recuperado

- Git parte del commit consolidado `b333a1602ac1fd6541efcc11b8034a7a7ca06212`
  de `codex/g4-i01`; no hay remoto configurado.
- La especificación V1 conserva 19 archivos físicos y los 18 hashes declarados
  en su manifiesto.
- PostgreSQL local 18.6: `arquitectura_veterinaria` es la única base en alcance.
  Flyway registra V001–V015 una sola vez y correctamente.
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
| B01 — Administración, identidad y acceso | En curso | Las interfaces CLIENT de registro, verificación, reenvío y recuperación están conectadas a las rutas B01 existentes. Falta ejecutar la semilla y el E2E autenticado CLIENT: sus cuatro variables locales sintéticas no están configuradas. |
| B02 — Propietarios, mascotas y clínica | Pendiente | Vertical navegador → API → PostgreSQL con aislamiento por tenant y permisos clínicos. |
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

Configurar exclusivamente en `.env.local` las cuatro variables CLIENT sintéticas
requeridas por `scripts/invoke-client-synthetic-seed.ps1`, ejecutar la semilla
transaccional y completar el E2E autenticado CLIENT. En paralelo, iniciar el
vertical B02 propietario → mascota → atención → historial.

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
  utiliza las funciones de registro y verificación ya auditadas. Su preflight
  se detuvo antes de PostgreSQL porque faltan las cuatro variables CLIENT
  locales; no se crearon cuentas ni se registraron secretos.
