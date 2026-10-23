# G4-I01 — control de Flyway en base local exclusiva

## Regla principal

Este runbook aplica solo a `arquitectura_veterinaria`. La aplicación y pruebas
ordinarias mantienen Flyway desactivado. Una migración requiere revisión de SQL,
autorización expresa y el rol `av_schema_owner`. No usar `repair`, `clean`,
superusuario ni una base ajena. Nunca modificar una fuente ya registrada.

## Estado vigente

- V001–V011 están aplicadas exactamente una vez.
- V011: `V011__g4_i01_serialize_password_recovery_tokens.sql`.
- SHA-256 V011: `508A558B90F33C74B1382696214DC45D167E389DED59E0A7F10B63968700167E`.
- Checksum Flyway V011: `-1337847367`.
- `application.yml` mantiene Flyway en `false` y target defensivo `010`.
  El target por defecto no autoriza ni aplica V011.

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

Antes de V012 o posterior: comprobar historial, hashes fuente, propietarios,
ACL, RLS, atomicidad, dependencias, suite afectada y autorización específica.
No se permite ejecutar una versión futura dentro de G4-I01.

Al cierre de G4-I01 se identificaron dos defectos de funciones aplicadas que
requieren precisamente ese proceso: `identity_auth.replace_staff_roles` (V004)
y `tenant_admin.review_administrative_recovery_for_session` (V005) tienen
referencias ambiguas a parámetros de salida. No se corrigen editando una fuente
ya registrada ni mediante `repair`.
