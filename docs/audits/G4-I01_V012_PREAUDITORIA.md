# G4-I01 - preauditoria de V012

## Estado y alcance

**Estado historico: preauditoria previa a la aplicacion.** V012 fue aplicada
posteriormente una sola vez en la base local exclusiva. Este documento conserva
la revision anterior y no sustituye el estado postaplicacion registrado en
`docs/status/G4-I01_PUNTO_REANUDACION_2026-10-23.md`. La fuente propuesta se
conservo fuera de `database/migration/` hasta la autorizacion expresa:

`docs/audits/G4-I01_V012_SQL_PROPUESTO.sql`

SHA-256 de los bytes revisados:

`1C5BC424FD6139EEB306C15B41A13AADBB17E298C92FE5723503BD01B4507B08`

Al momento de esta preauditoria, esos mismos bytes quedaban pendientes de
copiar sin edicion a
`database/migration/V012__g4_i01_qualify_remaining_identity_output_columns.sql`.
En ese momento no existia aun una V012 en la ruta de Flyway ni se habia
ejecutado SQL de V012, ni siquiera dentro de una transaccion de prueba. V012
fue aplicada despues una sola vez; el estado vigente se registra en
`docs/status/G4-I01_PUNTO_REANUDACION_2026-10-23.md`.

La revision parte de la base local exclusiva `arquitectura_veterinaria`, con
V001 a V011 ya registradas. Las fuentes V001--V011 se mantienen inmutables.
V011 conserva SHA-256
`508A558B90F33C74B1382696214DC45D167E389DED59E0A7F10B63968700167E`.

## Defectos reproducidos

| Funcion aplicada | Origen | Primera referencia ambigua | Evidencia controlada |
|---|---|---|---|
| `identity_auth.replace_staff_roles(text, uuid, jsonb)` | V004 | `normalized_email` compite entre la columna de `account_profile` y el nombre de salida de `RETURNS TABLE`. | La prueba JDBC recibe `BadSqlGrammarException` cuyo error de PostgreSQL contiene `normalized_email`. |
| `tenant_admin.review_administrative_recovery_for_session(text, uuid, varchar, varchar, boolean, text, timestamptz)` | V005 | `principal_id` compite entre `account_profile.principal_id` y el nombre de salida de `RETURNS TABLE`. | La prueba JDBC recibe `BadSqlGrammarException` cuyo error de PostgreSQL contiene `principal_id`. |

Las dos pruebas diagnosticas son transaccionales y hacen rollback. Confirman la
causa, no el exito funcional. No se corrigieron V004 ni V005 ni se utilizo
`flyway repair`.

## Inventario PL/pgSQL y analisis de ambiguedad

Se analizaron las 32 funciones PL/pgSQL del catalogo local y sus fuentes. El
escaneo conservador encontro siete referencias sin calificar que podrian
coincidir con una salida de `RETURNS TABLE`:

- una pertenece a `replace_staff_roles` y es el defecto de V004;
- cinco pertenecen a `review_administrative_recovery_for_session` y quedan
  cubiertas por la misma correccion de V012;
- una pertenece a `complete_tenant_registration`, en un destino `SET
  tenant_id = v_tenant_id`; no es una referencia de lectura ambigua.

El catalogo no tiene `plpgsql_check`; por ello esta es una combinacion de
revision estatica y ejecucion controlada de los dos caminos afectados, no una
certificacion automatica de todas las ramas de PL/pgSQL.

## Contenido exacto propuesto

V012 redefine **solo** estas dos funciones y conserva sus firmas, tipos de
retorno, reglas de negocio, SQLSTATE, auditoria y limites temporales:

1. `identity_auth.replace_staff_roles(text, uuid, jsonb)` retorna
   `(normalized_email varchar, account_status varchar, roles jsonb)`.
2. `tenant_admin.review_administrative_recovery_for_session(text, uuid,
   varchar, varchar, boolean, text, timestamptz)` retorna `(principal_id
   uuid, tenant_id uuid, applicant_email varchar, executed boolean)`.

Todas las referencias de columnas de las dos funciones usan alias concretos
(`profile`, `role_row`, `session_row`, `recovery_row`, `tenant_row` y
`token_row`). El escaneo de la propuesta encontro cero referencias candidatas
sin calificar. La propuesta no contiene `EXECUTE`, `format(...)`, `SET ROLE`,
`GRANT ... PUBLIC`, DDL de tablas/esquemas/RLS, SQL dinamico ni bootstrap SaaS.

### Privilegios y aislamiento

Ambas funciones propuestas permanecen `SECURITY DEFINER`, con:

```sql
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
```

La propuesta vuelve a fijar el propietario a `av_schema_owner`, revoca
`PUBLIC` de forma explicita y concede `EXECUTE` solo a `av_app`. La lectura
actual del catalogo confirma que los roles `av_app` y `av_schema_owner` no son
superusuarios ni disponen de `BYPASSRLS`.

`SECURITY DEFINER` puede eludir politicas RLS cuando el propietario de tablas
es elegible para ello; por esa razon la seguridad no descansa en RLS dentro de
estas funciones. Los limites se mantienen dentro de cada operacion:

- reemplazo de roles exige sesion de administrador de la misma veterinaria,
  filtra por `tenant_id` obtenido de dicha sesion y conserva la regla de ultimo
  administrador activo;
- revision de recuperacion exige administrador global, bloquea el registro de
  recuperacion, valida legitimidad y expiracion de la invitacion, solo crea o
  reutiliza una cuenta `STAFF` elegible del tenant de la solicitud y no crea
  administradores globales;
- el backend conserva la autorizacion de usuario final; el `EXECUTE` de
  `av_app` no concede acceso HTTP ni sustituye las comprobaciones de rol.

La revision independiente de solo lectura reprodujo el hash indicado y no
encontro ampliacion de privilegios ni regresion de aislamiento. Registro su
riesgo heredado no bloqueante: la expiracion de la invitacion de recuperacion
continua comparandose con `current_timestamp`, igual que el contrato previo;
V012 no amplia alcance para cambiar reglas temporales ya aprobadas.

## Atomicidad y condicion previa de ejecucion

`CREATE OR REPLACE FUNCTION`, `ALTER FUNCTION`, `REVOKE` y `GRANT` son
sentencias transaccionales en PostgreSQL. La fuente propone una unica
migracion, por lo que la definicion y las ACL no deberian quedar a medio
camino. Aun asi, **esa propiedad no se ha ejecutado ni se infiere como prueba
en esta base**: antes de cualquier aplicacion se debe hacer la comprobacion
autorizada contra `arquitectura_veterinaria` con el rol de migracion y el
launcher acotado, sin superusuario.

## Regresiones preparadas para despues de aplicar

No se sustituyeron ahora los diagnosticos por pruebas verdes ficticias. Los
dos casos existentes se fortalecieron para afirmar la referencia ambigua
exacta. Despues de una aplicacion autorizada, se convertiran a pruebas de
comportamiento y se ejecutara esta matriz:

| Caso reproducible | Resultado exigido |
|---|---|
| Reemplazo de `RECEPCION` por `CAJERO` de un STAFF del tenant actor | Retorno con correo/estado/roles, cambio efectivo y revocacion de sus sesiones. |
| Reemplazo contra principal de otro tenant | Denegado; no cambia roles ni sesiones ajenas. |
| Retiro del ultimo `ADMIN_VETERINARIA` | Falla con `23514`; las asignaciones originales permanecen. |
| JSON vacio, tipo invalido o rol fuera de catalogo | Falla con `42501`; no hay filas nuevas. |
| Recuperacion administrativa aprobada con atestacion, token hash y vigencia validos | Una sola invitacion STAFF y transicion a `EJECUTADA`, con las dos entradas de auditoria. |
| Recuperacion sin atestacion, con token invalido, tenant suspendido o cuenta no invitada | Denegada; no crea ni promueve identidades. |
| Recuperacion repetida, usada o concurrente | Una sola ejecucion observable; sin dos invitaciones activas. |
| Borde de invitacion de 24 h, enlace de correo de 24 h y recuperacion de contrasena de 30 min | Se aceptan dentro de sus limites y se rechazan vencidos, sin sustituir los contratos aprobados. |
| Autenticacion y sesion tras aceptar invitacion de recuperacion | Solo STAFF del tenant correcto puede iniciar sesion; CLIENT/SaaS y tenant ajeno son denegados. |
| Rollback de cada prueba de integracion | No quedan recuperaciones, tokens, roles ni sesiones sinteticas residuales. |

Las pruebas de permisos posteriores inspeccionaran tambien propietario,
`search_path`, ACL efectiva, ausencia de `PUBLIC`, imposibilidad de lectura
directa de hashes por `av_app` y continuidad de V001--V011.

## Decision requerida

La recomendacion es **no aplicar aun**. Para pasar a aplicacion se requiere
una autorizacion explicita que cite el SHA-256 anterior y permita: (1) copiar
exactamente esos bytes a la ruta canonica V012, (2) repetir las precondiciones
de historial, hash, roles y ACL, y (3) ejecutar Flyway una unica vez sobre la
base local exclusiva. Sin esa decision, V012 permanece solo como propuesta.
