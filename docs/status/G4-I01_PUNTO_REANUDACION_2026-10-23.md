# G4-I01 - punto de reanudacion congelado

## Proposito y limite

Este documento conserva el estado verificable de G4-I01 despues de aplicar
V012 y antes de la presentacion academica local del 24 de octubre de 2026. No
autoriza migraciones, desarrollo adicional, publicacion, despliegue ni cambios
en la especificacion funcional V1.

La rama de trabajo es `codex/g4-i01`. El ultimo commit anterior al trabajo sin
confirmar fue `1023be40d03a167883f4164ef35c1f0e309a6893`
(`docs(g4-i01): preaudit V012 and demo readiness`). No hay remoto configurado.

## Base inmutable y base de datos

- La especificacion funcional conserva 19 archivos fisicos y los 18 hashes de
  contenido declarados por `15_MANIFIESTO_SHA256.json`.
- PostgreSQL local: 18.6; base exclusiva: `arquitectura_veterinaria`.
- `av_app` y `av_schema_owner` no son superusuarios ni tienen `BYPASSRLS`.
- Flyway registra una sola aplicacion correcta de cada V001 a V012. Los
  checksums registrados y la fuente asociada son:

  | Version | Fuente | Checksum Flyway |
  |---|---|---:|
  | V001 | `V001__g4_i00_identity_tenant.sql` | `-1591591157` |
  | V002 | `V002__g4_i01_saas_identity_and_administration.sql` | `446394828` |
  | V003 | `V003__g4_i01_allow_global_opaque_sessions.sql` | `1092241323` |
  | V004 | `V004__g4_i01_principal_security_definer_operations.sql` | `-1500295617` |
  | V005 | `V005__g4_i01_harden_inherited_security_definer_privileges.sql` | `2127328539` |
  | V006 | `V006__g4_i01_use_statement_time_for_bounded_tokens.sql` | `-1462244583` |
  | V007 | `V007__g4_i01_qualify_staff_invitation_profile_columns.sql` | `-448126826` |
  | V008 | `V008__g4_i01_qualify_staff_invitation_token_columns.sql` | `1084857806` |
  | V009 | `V009__g4_i01_use_statement_time_for_resend_and_recovery_tokens.sql` | `-1078430691` |
  | V010 | `V010__g4_i01_qualify_password_recovery_token_column.sql` | `72123246` |
  | V011 | `V011__g4_i01_serialize_password_recovery_tokens.sql` | `-1337847367` |
  | V012 | `V012__g4_i01_qualify_remaining_identity_output_columns.sql` | `-1132033441` |
- V013 no existe en `database/migration/` ni en `flyway_schema_history`.

## V012 aplicada y alcance real

La fuente canonica aplicada es
`database/migration/V012__g4_i01_qualify_remaining_identity_output_columns.sql`.
Su SHA-256 es:

`1C5BC424FD6139EEB306C15B41A13AADBB17E298C92FE5723503BD01B4507B08`

Es byte a byte igual a la propuesta historica auditada. Redefinio solo:

1. `identity_auth.replace_staff_roles(text, uuid, jsonb)`.
2. `tenant_admin.review_administrative_recovery_for_session(text, uuid,
   varchar, varchar, boolean, text, timestamptz)`.

Ambas conservan owner `av_schema_owner`, `SECURITY DEFINER`, el `search_path`
seguro `pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp`, revocacion
de `PUBLIC` y `EXECUTE` limitado a `av_app`.

## Evidencia ejecutada despues de V012

| Verificacion | Resultado real |
|---|---|
| Launcher Flyway V012 | 1/1 prueba correcta; valida 12 migraciones y aplica solo V012. |
| Regresion focal de roles | 3/3 correctas: roles multiples, revocacion de sesion, ultimo administrador y objetivo cross-tenant. |
| `B01AdministrationDatabaseIntegrationTest` | 30 casos: 29 correctos y 1 error conocido. |
| Limpieza de pruebas | Cero recuperaciones y perfiles de los prefijos sinteticos de la regresion. |

Las pruebas ordinarias tuvieron Flyway desactivado. No se declara B01 ni
G4-I01 completos.

## Defecto pendiente y propuesta conceptual de V013

La aprobacion de recuperacion administrativa sigue fallando de forma
reproducible con `column reference "tenant_id" is ambiguous` en
`tenant_admin.review_administrative_recovery_for_session`.

La sentencia problematica aplicada por V012 es:

```sql
on conflict (tenant_id, staff_principal_id, role_code)
```

El nombre de salida `tenant_id` de `RETURNS TABLE` colisiona con esa referencia
de columna de PL/pgSQL. La correccion propuesta para una futura V013 es
redefinir **solo** esta funcion y emplear:

```sql
on conflict on constraint role_assignment_tenant_id_staff_principal_id_role_code_key
```

V013 no ha sido creada, auditada, aplicada ni autorizada. No modificar V012 ni
usar `flyway repair`.

Antes de solicitar V013 se debe: verificar historia y hashes V001-V012,
auditar el SQL exacto y su SHA-256, revisar owner/ACL/`search_path`/RLS,
confirmar atomicidad mediante `BEGIN`/`ROLLBACK`, revisar por un tercero y
ejecutar las regresiones de recuperacion, tenant, roles, CSRF, sesiones y
concurrencia despues de Flyway.

## Requisitos B01 pendientes al congelar

Nueve requisitos siguen parciales: RF-SAA-02, RF-SAA-05, RF-SAA-06,
RF-SAA-11, RF-SAA-13, RF-AUT-11, RF-USR-04, RF-CLI-03 y RF-CLI-04.
RF-SAA-13 depende directamente de la correccion futura; los demas requieren
regresiones temporales, decisiones/proyecciones o los bloques B02-B05. No se
implementaran esos bloques como parte de la presentacion.

## Retomar despues de la presentacion

1. Autorizar separadamente la auditoria y eventual aplicacion de V013.
2. Convertir la regresion de recuperacion administrativa en verde y ejecutar
   B01, seguridad, concurrencia, OpenAPI, backend, frontend y E2E completos.
3. Actualizar trazabilidad B01 solo con evidencia nueva.
4. Antes de GitHub el 30 de octubre: repetir revision de secretos, licencias,
   datos sinteticos, historial Git y configuracion de publicacion. No crear
   remoto ni publicar hasta una autorizacion posterior.
