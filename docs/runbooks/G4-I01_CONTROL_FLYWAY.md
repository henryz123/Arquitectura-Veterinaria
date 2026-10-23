# G4-I01 — control de Flyway en base local exclusiva

## Regla principal

Este runbook aplica solo a `arquitectura_veterinaria`. La aplicación y pruebas
ordinarias mantienen Flyway desactivado; una migración necesita revisión de SQL,
autorización expresa y el rol `av_schema_owner`. No usar `repair`, `clean`,
superusuario ni una base ajena. Nunca modificar una fuente ya registrada.

## Estado actual

- V001–V010 están aplicadas exactamente una vez y Flyway validó las diez.
- V010: `V010__g4_i01_qualify_password_recovery_token_column.sql`.
- SHA-256 de V010: `AC3DF14997C5BD83E989E1AB97E4F4863636F850EC58AA185E979BCDD99CCFB3`.
- Checksum Flyway de V010: `72123246`.
- Flyway queda `false` por defecto y su target defensivo es `010`.

## Identidades locales

- Runtime: `AV_DB_USER=av_app`.
- Migración: `AV_MIGRATION_DB_USER=av_schema_owner`.
- URL permitida: `jdbc:postgresql://localhost:5432/arquitectura_veterinaria`.

Las contraseñas permanecen solo en el entorno local o `.env.local` ignorado. No
se pasan como argumentos, no se imprimen ni se añaden a scripts versionados.

## Pruebas ordinarias

```powershell
$env:JAVA_HOME = 'C:\Users\HNR05\.jdks\ms-21.0.12.1'
$env:AV_FLYWAY_ENABLED = 'false'
Set-Location backend
.\mvnw.cmd test
```

El target no autoriza una migración: solo limita un opt-in explícito.

## Nueva versión

Antes de V011 o posterior: comprobar historial, hash fuente, propietario, ACL,
RLS, transaccionalidad, suite afectada y autorización específica.
`scripts/invoke-authorized-flyway-v010.cmd` es evidencia histórica: fija target
`010`, exige roles/URL exactos y usa `setlocal`; no sirve para otra versión.

El launcher `.ps1` histórico no se usa si Windows aplica su política de
ejecución; no se debe eludir esa política. Los launchers `.cmd` acotados son el
procedimiento documentado para versiones ya autorizadas.
