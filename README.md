# Arquitectura - Veterinaria

Monolito modular académico para veterinarias independientes en Perú. La especificación funcional V1 permanece inmutable en `arquitectura_veterinaria_v1_FINAL/`. G4-I00 aportó un vertical técnico de sesión laboral y aislamiento de tenant; G4-I01 está en curso y no se declara cerrado mientras la suite B01 no sea verde.

## Estructura

- `backend/`: Spring Boot 4.1.1, Java 21 y Maven Wrapper.
- `frontend/`: Angular 21 y TypeScript, gestionado por pnpm 11.25.0.
- `database/migration/`: fuente única de esquema Flyway.
- `database/demo/`: carga interactiva de datos sintéticos, fuera de Flyway.
- `infra/`: estado de infraestructura reproducible y límites de Docker.
- `docs/`: arquitectura, ADR, contratos, auditorías y runbooks.
- `scripts/`: verificaciones locales sin secretos.

## Requisitos verificados

- JDK Microsoft 21.0.12.1, mediante `C:\Users\HNR05\.jdks\ms-21.0.12.1`.
- Maven Wrapper 3.10.0.
- Node.js 22.20.0.
- PostgreSQL 18.6, cliente en `C:\Program Files\PostgreSQL\18\bin\psql.exe`.

La base exclusiva local y sus roles mínimos ya se verificaron para G4-I00/G4-I01. Para el entorno y la semilla sintética sigue el [runbook local](docs/runbooks/G4-I00_ENTORNO_LOCAL.md). Para inspeccionar o autorizar una migración, sigue el [control Flyway de G4-I01](docs/runbooks/G4-I01_CONTROL_FLYWAY.md); no proporciones contraseñas en chat, archivos ni comandos.

## Instalación y verificación

En `frontend/`, usa Corepack/pnpm 11.25.0 y respeta `pnpm-workspace.yaml`, que bloquea scripts de construcción nativos de dependencias transitorias. Los binarios precompilados que Angular requiere se resuelven desde paquetes oficiales.

```powershell
corepack pnpm install
```

Con JDK 21 y las variables `AV_DB_*`, `AV_MIGRATION_DB_*` y `AV_DEMO_*` disponibles solo en el entorno local, desde la raíz:

```powershell
.\scripts\verify-g4-i00.cmd
```

La política de ejecución actual bloquea scripts `.ps1`; el lanzador `.cmd` evita modificar o eludir dicha política.

Los comandos de ejecución se mantienen locales: no existe remoto Git ni despliegue autorizado. No reutilices una base de otro proyecto ni copies secretos a los comandos. Las pruebas ordinarias mantienen Flyway desactivado; una migración solo se ejecuta con autorización, rol de migración y target explícitos.

## Estado G4-I00 y G4-I01

- Flyway V001, privilegios de `av_app`, RLS y datos sintéticos: verificados en la base exclusiva.
- Autenticación API, CSRF, logout y rechazo cross-tenant: verificados con Aurora y Bosque.
- Compilación y pruebas backend: 23/23; compilación Angular: correcta (bundle inicial 276.55 kB).
- Bootstrap SaaS privado: ejecutado una sola vez bajo `av_schema_owner`; `PUBLIC` y `av_app` siguen sin `EXECUTE`. B01 y HTTP cubrieron sesión SaaS, suspensión/reactivación, aislamiento tenant, CSRF y logout.
- Agent Browser 0.27.0 está bloqueado por una sesión Chromium huérfana: no se declaró un E2E UI autenticado ni una auditoría `a11y` como superados. Su versión no ofrece el subcomando `a11y`.
- El contrato OpenAPI pasó sintaxis YAML y 29 referencias internas con SnakeYAML. Falta un validador OpenAPI semántico: el binario local de Redocly no existe y no se instaló nada.
- Flyway V008, V009 y V010 se aplicaron una vez mediante procedimientos autorizados. V010 corrigió la ambigüedad de `principal_id` en recuperación de contraseña; no existe V011 ni se habilitó Flyway para ejecución ordinaria.
- La trazabilidad de los 35 requisitos B01 está en [G4-I01](docs/implementation-plan/06_TRAZABILIDAD_B01_G4_I01.md): 19 verificados puntualmente, 9 parciales y 7 sin verificar. Esto no equivale a B01 completa.

No se declara B01 completa ni se avanza a G4-I02–I06.
