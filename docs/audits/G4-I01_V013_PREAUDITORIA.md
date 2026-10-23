# G4-I01 — preauditoría de V013

**Fecha:** 2026-10-23
**Migración auditada:** `database/migration/V013__g4_i01_fix_administrative_recovery_conflict_target.sql`
**SHA-256:** `4B5F8EF040FCE0A0A8B97C33517E622090500C058A45B2FF45FCE171D2BE584D`
**Base autorizada:** `arquitectura_veterinaria` local
**Resultado:** apta para aplicación controlada mediante el launcher V013.

## Alcance exacto y causa confirmada

V013 contiene una única sentencia `CREATE OR REPLACE FUNCTION`:
`tenant_admin.review_administrative_recovery_for_session(text, uuid, varchar,
varchar, boolean, text, timestamptz)`.

La versión actualmente aplicada por V012 usa `ON CONFLICT (tenant_id,
staff_principal_id, role_code)`. En una función PL/pgSQL que devuelve una
columna llamada `tenant_id`, esa referencia no calificada es ambigua. V013
reemplaza exclusivamente esa línea por:

```sql
on conflict on constraint role_assignment_tenant_id_staff_principal_id_role_code_key
```

La restricción existe en la base y declara exactamente `UNIQUE (tenant_id,
staff_principal_id, role_code)`. La comparación línea por línea de la función
V012 con V013 produjo únicamente estas dos líneas distintas.

## Controles de seguridad y aislamiento

| Control | Evidencia verificada | Resultado |
|---|---|---|
| Superficie | Una función `SECURITY DEFINER`; no crea tablas, roles, políticas ni rutas públicas. | Conforme. |
| Propietario | `av_schema_owner`; no es superusuario ni tiene `BYPASSRLS`. | Conforme. |
| Búsqueda de objetos | `search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp`; `av_app` no puede crear objetos en esos esquemas. | Conforme. |
| ACL | V013 vuelve a asignar el propietario, revoca `PUBLIC` y concede `EXECUTE` solo a `av_app`. La ACL vigente anterior tiene `PUBLIC=false`, `av_app=true`. | Conforme. |
| SQL dinámico | No hay SQL dinámico ni `format()`. Las únicas coincidencias de `execute` son la columna/acción de auditoría `EXECUTED` y el `GRANT EXECUTE` de ACL. | Conforme. |
| Acceso directo | `av_app` no posee `SELECT`, `INSERT` ni `UPDATE` directos sobre `principal`, `account_profile`, `role_assignment`, `one_time_token` ni `administrative_recovery`. | Conforme. |
| Ámbito tenant | El `tenant_id` procede de la recuperación bloqueada; la función no recibe un tenant ni un principal objetivo arbitrarios. Primero exige un administrador SaaS válido, bloquea recuperación y tenant, y asigna el rol solo al principal creado o encontrado dentro de esa recuperación. | Conforme. |
| RLS | `account_profile`, `role_assignment` y `tenant` tienen RLS habilitado; su dueño no tiene `FORCE RLS`, por lo que `SECURITY DEFINER` puede omitir estas políticas. La función compensa ese límite mediante autenticación SaaS y derivación interna del tenant, sin exponer acceso directo al rol de aplicación. | Riesgo residual conocido y acotado. |
| Idempotencia | Una recuperación ya `EJECUTADA` se rechaza y el conflicto de rol reactiva la misma asignación única, sin crear una segunda identidad por la misma recuperación. | Conforme a la semántica B01. |

## Atomicidad y recuperación

- PostgreSQL admite transacciones para `CREATE OR REPLACE FUNCTION`, `ALTER
  FUNCTION`, `REVOKE` y `GRANT`; V013 no contiene operaciones no
  transaccionales.
- El archivo se ejecutó completo con el rol de migración dentro de
  `BEGIN`/`ROLLBACK` y finalizó sin error. Después del rollback, el catálogo
  aún mostró el objetivo de conflicto de V012, lo que demuestra que esa prueba
  no persistió cambios.
- Antes de Flyway se generó un respaldo binario local fuera del repositorio:
  `arquitectura_veterinaria_pre_v013_20261023_141135.dump`, 147824 bytes,
  SHA-256 `F84EFB8AD896AD5903AD179F73DD69E05F4681CCF1CDD56F85151136CDD59E8E`.
  Su catálogo fue verificado con `pg_restore --list` sin revelar datos.
- La reversión operativa, si fuese necesaria, consiste en restaurar ese backup
  en una instancia local aislada y decidir una nueva migración correctiva; no
  se reescriben migraciones aplicadas ni se usa `flyway repair`.

## Estado previo a aplicación

- Flyway: V001–V012 aplicadas una sola vez, todas con `success=true`; V013
  ausente.
- Los SHA-256 locales V001–V012 coinciden con la línea base registrada; V012
  conserva `1C5BC424FD6139EEB306C15B41A13AADBB17E298C92FE5723503BD01B4507B08`.
- La aplicación se realizará solo con
  `scripts/invoke-authorized-flyway-v013.cmd`, que exige la base local, el rol
  de aplicación `av_app`, el rol de migración `av_schema_owner` y fija el
  target Flyway `013`.

## Dictamen

No se identificó un riesgo crítico sin tratamiento dentro del alcance de V013.
La aplicación está autorizada por la instrucción integral vigente, condicionada
a la verificación posterior de historial Flyway, catálogo de la función,
recuperación administrativa, aislamiento tenant y suite B01.

## Resultado posterior a la aplicación

- El launcher controlado se ejecutó con Java 21.0.12.1 y Flyway validó 13
  migraciones antes de aplicar V013 una sola vez.
- El historial registra V013 con checksum Flyway `-57543870` y `success=true`.
- El catálogo posterior confirma la función como `SECURITY DEFINER`, propiedad
  de `av_schema_owner`, con el mismo `search_path` seguro, `PUBLIC=false` y
  `av_app=true` para `EXECUTE`. Su definición contiene el objetivo de conflicto
  nombrado y ya no contiene la forma ambigua de V012.
- La suite de integración
  `B01AdministrationDatabaseIntegrationTest` terminó con 30 pruebas correctas,
  0 fallos y 0 errores. Esto incluye el caso de aprobación de recuperación que
  antes fallaba, controles de roles y objetivos cross-tenant.
- La comprobación posterior encontró cero recuperaciones o perfiles con el
  prefijo sintético de esa regresión; V013 permanece aplicada una vez.

### Incidencia de entorno no funcional

El `PATH` global resolvía una JRE 8, que impidió el primer intento antes de que
Spring/Flyway iniciara. No persistió ninguna migración. La ejecución correcta
seleccionó temporalmente el JDK 21.0.12.1 ya instalado para el proceso; no se
instaló ni reconfiguró Java en el sistema. Maven emitió advertencias de
metadatos autenticados de dos repositorios remotos, pero la caché local permitió
la validación y la aplicación. Ambas condiciones deben revisarse antes de una
automatización limpia en otra máquina.
