# V1 integral — progreso verificable

**Rama de trabajo:** `codex/v1-integral`
**Inicio de esta línea:** 2026-10-23
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
  Flyway registra V001–V012 una sola vez y correctamente; V013 no está aplicada.
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
| B01 — Administración, identidad y acceso | En curso | El defecto SQL de recuperación administrativa está resuelto y la suite backend está verde; faltan las interfaces reales autorizadas. |
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

## Evidencia nueva de esta línea

- Recuperación inicial: historial Flyway, constraint de roles, propietarios,
  `search_path`, ACL de funciones y capacidades de roles comprobados el
  2026-10-23.
- V013 se validó primero en `BEGIN`/`ROLLBACK`, se respaldó la base con un dump
  local verificable y después Flyway la aplicó una sola vez.
- `B01AdministrationDatabaseIntegrationTest`: 30 correctas, 0 fallos, 0
  errores con Flyway desactivado durante la prueba; no quedaron residuos del
  caso sintético de recuperación administrativa.
- La ejecución requiere seleccionar el JDK 21 local por proceso: el `PATH`
  global todavía resuelve una JRE 8. Es un riesgo de reproducibilidad pendiente,
  no una modificación del sistema.

## Próxima operación concreta

Inspeccionar los flujos y rutas Angular existentes de B01, contrastarlos con los
endpoints ya implementados y entregar el primer vertical administrativo real con
pruebas de navegador, sin alterar el cambio preexistente de `angular.json`.
