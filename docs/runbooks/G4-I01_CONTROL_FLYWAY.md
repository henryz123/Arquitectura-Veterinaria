# G4-I01 — control de Flyway en base local exclusiva

## Regla principal

Este runbook aplica solo a `arquitectura_veterinaria`. La aplicación y pruebas
ordinarias mantienen Flyway desactivado. Una migración requiere revisión de SQL,
autorización expresa y el rol `av_schema_owner`. No usar `repair`, `clean`,
superusuario ni una base ajena. Nunca modificar una fuente ya registrada.

## Estado histórico al corte V012

> Actualizacion 2026-10-23: V012 ya esta aplicada una sola vez con checksum
> `-1132033441`; V013 no existe. Las viñetas historicas V001-V011 que siguen
> abajo describen el estado previo. El estado de reanudacion vigente esta en
> `docs/status/G4-I01_PUNTO_REANUDACION_2026-10-23.md`; no reutilizar el
> launcher V011 ni crear/aplicar V013 sin autorizacion nueva.

- V001–V011 están aplicadas exactamente una vez.
- V011: `V011__g4_i01_serialize_password_recovery_tokens.sql`.
- SHA-256 V011: `508A558B90F33C74B1382696214DC45D167E389DED59E0A7F10B63968700167E`.
- Checksum Flyway V011: `-1337847367`.
- `application.yml` mantiene Flyway en `false` y target defensivo `010`.
  El target por defecto no autoriza ni aplica V011.

## Estado vigente V1 integral

La línea activa aplica exclusivamente a `arquitectura_veterinaria` y se
registra en `docs/status/V1_INTEGRAL_PROGRESO.md`. A fecha 2026-10-23, Flyway
registra V001–V015 exactamente una vez. V013 corrigió la recuperación
administrativa, V014 normalizó dos nombres sintéticos históricos y V015 rota
un CSRF únicamente para una sesión opaca activa.

- V013: checksum `-57543870`.
- V014: checksum `505352357`.
- V015: `V015__rotate_csrf_token_for_active_session.sql`, SHA-256
  `7AFF333B71371063F8A31A5F252B4DDF0EA5754ED52AC3FFCD311DD71866BB19`,
  checksum `1029927636`.
- Antes de V016 o posterior: repetir historial, hashes fuente, propietarios,
  ACL, RLS, atomicidad, dependencias, suite afectada y revisión del SQL. No
  reutilizar launchers antiguos ni usar `repair` o `clean`.

Las secciones posteriores a esta nota son antecedentes de V011/V012 y no
deben interpretarse como estado ni autorización actual.

## Identidades locales

- Runtime: `AV_DB_USER=av_app`.
- Migración: `AV_MIGRATION_DB_USER=av_schema_owner`.
- URL permitida: `jdbc:postgresql://localhost:5432/arquitectura_veterinaria`.

Las contraseñas permanecen solo en el entorno local o `.env.local` ignorado. No
se pasan como argumentos, no se imprimen ni se añaden a scripts versionados.

## Pruebas ordinarias

```powershell
$env:JAVA_HOME = '<ruta-local-a-jdk-21>'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:AV_FLYWAY_ENABLED = 'false'
Set-Location backend
.\mvnw.cmd test
```

## Evidencia de V011 ya aplicada

`scripts/invoke-authorized-flyway-v011.cmd` fue el launcher acotado empleado
para la autorización ya consumida: exige URL, roles y target `011`, activa
Flyway solo en su proceso y ejecuta la prueba de migración autorizada. No debe
reutilizarse para migraciones nuevas ni interpretarse como permiso permanente.

El dry-run previo se ejecutó con `BEGIN`, carga de V011 y `ROLLBACK`; confirmó
que el `DO`, índice único parcial, dos redefiniciones, `ALTER OWNER`, `REVOKE`
y `GRANT` son transaccionales en PostgreSQL 18 local.

## Nueva versión

Antes de V013 o posterior: comprobar historial, hashes fuente, propietarios,
ACL, RLS, atomicidad, dependencias, suite afectada y autorización específica.
No se permite ejecutar una versión futura dentro de G4-I01.

V012 fue evaluada como propuesta fuera de la ruta de Flyway en
`docs/audits/G4-I01_V012_SQL_PROPUESTO.sql`, SHA-256
`1C5BC424FD6139EEB306C15B41A13AADBB17E298C92FE5723503BD01B4507B08`.
Despues de la autorizacion se copio sin edicion a la ruta canonica y se aplico
una sola vez. No copiar, ejecutar ni preparar una version posterior sin una
nueva autorizacion; antes de una eventual V013 se repetiran historial, roles,
ACL, `search_path`, integridad V001--V012 y el control de base local exclusiva.

Antes de V012 se identificaron dos defectos de funciones aplicadas:
`identity_auth.replace_staff_roles` (V004) y
`tenant_admin.review_administrative_recovery_for_session` (V005). V012 corrigio
el primero; el segundo conserva una ambiguedad residual de `tenant_id` y es el
unico defecto documentado para una eventual V013. No se corrige editando una
fuente ya registrada ni mediante `repair`.
