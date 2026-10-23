# G4-I01 — historial V008/V009 anterior a V010 (no vigente)

> Este documento conserva el diagnóstico que precedió a V010. No describe el
> estado actual: V010 se aplicó una vez el 2026-10-22. Consultar
> `G4-I01_MATRIZ_SEGURIDAD_Y_PRUEBAS.md`,
> `G4-I01_MANTENIBILIDAD_IDENTIDAD_SQL.md` y el runbook Flyway para el estado
> vigente.

**Estado histórico:** al momento de este pre-audit, V008 y V009 estaban
aplicadas y verificadas en la base local exclusiva, y V010 todavía no existía.
El estado vigente de V010 se registra únicamente en los documentos indicados
arriba.

## V008 — corrección aplicada

| Control | Evidencia verificada |
|---|---|
| Archivo | `database/migration/V008__g4_i01_qualify_staff_invitation_token_columns.sql` |
| SHA-256 fuente | `BBD6A588BD7E4968734C1FD7E90A587C414CE881C10E70A107852D7DC411003E` |
| Historial Flyway | V001–V008, una fila exitosa por versión; V008 registra checksum Flyway `1084857806` |
| Cambio funcional | En `identity_auth.invite_staff`, `one_time_token` recibe el alias `token` y la condición usa `token.principal_id`; no se cambió la firma, retorno ni la lógica restante. |
| Migración | Flyway validó ocho migraciones y avanzó una sola vez de V007 a V008 sobre `arquitectura_veterinaria`. |
| Propietario y seguridad | `av_schema_owner`, `SECURITY DEFINER`, `search_path=pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp`; `PUBLIC` no ejecuta y `av_app` conserva el único `EXECUTE` necesario. |
| Privilegios directos | `av_app` no tiene `SELECT`, `INSERT`, `UPDATE` ni `DELETE` directos sobre las tablas de `identity_auth` y `tenant_admin` revisadas. |

La revisión independiente estática de ECC dio dictamen **APPLY**. Confirmó que
V008 contiene una sola redefinición de función, no incorpora SQL dinámico,
roles, tablas, permisos públicos ni una ruta SaaS, y que sus DDL son
transaccionales en PostgreSQL 18.

## Control contra migraciones no autorizadas

La configuración de aplicación ahora deja Flyway desactivado salvo opt-in
explícito (`AV_FLYWAY_ENABLED=false`) y mantiene el máximo por defecto en V009.
La aplicación sigue usando `AV_DB_USER=av_app`; el procedimiento de migración
exige `AV_MIGRATION_DB_USER=av_schema_owner`, URL local exacta y el target
autorizado. El runbook vigente está en
`docs/runbooks/G4-I01_CONTROL_FLYWAY.md`.

El primer intento de lanzar el procedimiento PowerShell no llegó a Maven
porque Windows bloqueó `.ps1`. No se eludió esa política: se añadieron
lanzadores `.cmd` acotados que usan `setlocal`,
verifica los roles y restringe la URL a
`jdbc:postgresql://localhost:5432/arquitectura_veterinaria` antes de habilitar
Flyway con el target autorizado.

## Pruebas ejecutadas después de V008

| Prueba | Resultado real |
|---|---|
| Contexto controlado con Flyway desactivado | 1/1 superada; confirmó que una prueba ordinaria no aplica migraciones. |
| Lanzador de V008 | 1/1 superada; Flyway validó ocho migraciones y aplicó solo V008. |
| Regresión de invitación, aceptación, roles múltiples y último administrador | 1/1 superada. |
| Suite B01 previa a ampliar los casos | 7 ejecutadas: 6 superadas y 1 omitida por faltar credenciales SaaS sintéticas en el proceso. |
| Regresión de reenvío tras V009 | 2/2 superadas: genera reemplazo, invalida el enlace anterior y conserva uso único. |
| Suite B01 posterior a V009 | 14 ejecutadas: 12 superadas, 1 omitida por faltar las credenciales SaaS del proceso y 1 error de recuperación. No es una suite verde. |

El adaptador JDBC también se corrigió para aceptar el formato canónico de
`jsonb` con espacios después de las comas. Conserva la validación estricta de
roles `[A-Z_]` y fue cubierto por la regresión de invitación con dos roles.

## V009 — corrección aplicada

| Control | Estado |
|---|---|
| Archivo | `database/migration/V009__g4_i01_use_statement_time_for_resend_and_recovery_tokens.sql` |
| SHA-256 fuente | `302838C105A76E659EA45719658BB1684A958DE466071BDA807C1EED4806AB49` |
| Motivo reproducido | Los servicios generan vencimientos exactamente a +24 h y +30 min. V004 los compara con `current_timestamp`, fijo al inicio de la transacción JDBC, por lo que `resend_client_email_verification` y `request_password_recovery` rechazan indebidamente esos límites. |
| Alcance | Solo redefine esas dos funciones. Añade `v_statement_time := statement_timestamp()` y sustituye exclusivamente las dos comparaciones de vigencia. |
| Contratos | Conserva 24 h para verificación reenviada y 30 min para recuperación; no cambia firmas, retornos, selects, mutaciones, tenant, rama SaaS, `SECURITY DEFINER`, `search_path`, propietario ni ACL. |
| Revisión ECC independiente | Dictamen estático favorable: dos funciones, sin SQL dinámico, sin DDL de tablas/roles, sin permisos nuevos; operación apta para transacción Flyway estándar. |
| Flyway | Validó nueve migraciones y avanzó una vez de V008 a V009. El historial registra checksum Flyway `-1078430691`, éxito y una sola aplicación. |
| Catálogo posterior | Ambas funciones conservan `av_schema_owner`, `SECURITY DEFINER`, `search_path=pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp`, `PUBLIC=false` y `av_app=true`. |
| Resultado temporal | El reenvío de verificación de cliente queda verificado en dos regresiones; la recuperación alcanza una ambigüedad diferente documentada abajo. |

## Diagnóstico de V010 — no preparada ni aplicada

La suite B01 posterior a V009 reveló `column reference "principal_id" is
ambiguous` en `identity_auth.request_password_recovery`. La función declara
`returns table (principal_id uuid, tenant_id uuid)`: esos nombres son variables
implícitas de PL/pgSQL. Su `UPDATE identity_auth.one_time_token` deja
`principal_id` sin calificar, por lo que choca con la columna de la tabla.

La revisión independiente de ECC confirmó que la corrección mínima futura sería
redefinir **solo** esa función, aplicando el patrón ya usado por V008:

```sql
update identity_auth.one_time_token token
   set invalidated_at = current_timestamp
 where token.purpose = 'PASSWORD_RECOVERY'
   and token.principal_id = v_principal_id
   and token.consumed_at is null
   and token.invalidated_at is null;
```

No debe cambiarse V009. Una V010 deberá conservar firma, retorno, límite de 30
minutos con `statement_timestamp()`, ramas SaaS/STAFF/CLIENT, aislamiento tenant,
`SECURITY DEFINER`, `search_path`, propietario y ACL. No se creó fuente V010 ni
se ejecutó ninguna operación persistente adicional.

## Bloqueo de cierre

No debe declararse G4-I01 cerrado todavía. V009 corrigió el límite temporal y
el reenvío, pero la recuperación de contraseña requiere una V010 autorizada.
La administración SaaS integrada continúa sin ejecución porque el proceso de
prueba no recibió `AV_DEMO_SAAS_EMAIL` ni `AV_DEMO_SAAS_PASSWORD`; no se
introdujo bypass.
