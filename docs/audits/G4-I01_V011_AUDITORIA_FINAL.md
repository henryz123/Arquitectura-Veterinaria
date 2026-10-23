# G4-I01 — auditoría final de V011

## Alcance y resultado

V011 corrige exclusivamente la concurrencia de tokens de recuperación de
contraseña en la base local `arquitectura_veterinaria`. No modifica V001–V010,
no crea V012, no amplía otros propósitos de token y no cambia RLS, CSRF ni las
rutas HTTP.

| Control previo | Resultado |
|---|---|
| Archivo | `V011__g4_i01_serialize_password_recovery_tokens.sql` |
| SHA-256 | `508A558B90F33C74B1382696214DC45D167E389DED59E0A7F10B63968700167E` |
| Preflight | Cero duplicados activos de recuperación y cero tokens de recuperación sin principal. |
| Atomicidad | Dry-run `BEGIN` → carga V011 → `ROLLBACK` superado; el índice, funciones y ACL se incluyeron en una sola transacción. |
| Flyway | V011 aplicada una vez con checksum `-1337847367`; V001–V010 conservan sus hashes fuente esperados. |
| Revisión independiente | Aprobable: bloqueo uniforme `principal → one_time_token`, `statement_timestamp()` y ausencia de SQL dinámico/`SET ROLE`/`PUBLIC EXECUTE`. |

## Funciones y defensa

1. `identity_auth.request_password_recovery(account_type, tenant_slug, email,
   ttl)`: resuelve el principal en el contexto permitido, bloquea su fila estable
   antes de invalidar/emitir y conserva el máximo funcional de 30 minutos.
2. `identity_auth.reset_password_with_recovery(token, password)`: adquiere el
   bloqueo del principal antes de consumir y vuelve a validar el token al
   actualizar, para no invertir el orden de bloqueos.
3. `uq_one_time_token_password_recovery_active_principal`: índice único parcial
   por `principal_id` cuando `purpose='PASSWORD_RECOVERY'`, `consumed_at IS NULL`
   e `invalidated_at IS NULL`.

Ambas funciones son de `av_schema_owner`, `SECURITY DEFINER`, `PUBLIC=false` y
`av_app=true` para `EXECUTE`; su `search_path` es
`pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp`. `av_app` no tiene
SELECT/INSERT/UPDATE directo sobre `identity_auth.principal` ni
`identity_auth.one_time_token`. Los dos roles permanecen sin superusuario ni
`BYPASSRLS`.

## Resultado posterior

La regresión histórica y el guion exhaustivo V011 obtuvieron máximo un token
utilizable por principal, aislamiento Aurora/Bosque, rollback íntegro, rechazo
de enlace anterior/reutilizado, vigencia exacta y cero fixtures residuales. Los
detalles reproducibles están en
`G4-I01_CONCURRENCIA_RECUPERACION.md`.

## Riesgo residual

El índice no concurrente puede bloquear escrituras en una base compartida. Esa
posibilidad no es crítica para la base local exclusiva autorizada; requiere una
ventana operativa y revisión nueva antes de cualquier despliegue.
