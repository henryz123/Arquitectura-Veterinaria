# Arquitectura - Veterinaria

Monolito modular académico para veterinarias independientes en Perú. La especificación funcional V1 permanece inmutable en `arquitectura_veterinaria_v1_FINAL/`; G4-I00 solo materializa un vertical técnico limitado de sesión laboral y aislamiento de tenant con datos sintéticos.

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

La base exclusiva local y sus roles mínimos ya se verificaron para G4-I00. Para reproducirla o revisarla, sigue el [runbook local](docs/runbooks/G4-I00_ENTORNO_LOCAL.md); no proporciones contraseñas en chat, archivos ni comandos.

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

Los comandos de ejecución se mantienen locales: no existe remoto Git ni despliegue autorizado. No reutilices una base de otro proyecto ni copies secretos a los comandos.

## Estado G4-I00

- Flyway V001, privilegios de `av_app`, RLS y datos sintéticos: verificados en la base exclusiva.
- Autenticación API, CSRF, logout y rechazo cross-tenant: verificados con Aurora y Bosque.
- Compilación y pruebas backend: 5/5; compilación frontend y prueba de servicio: correctas.
- Agent Browser verificó la pantalla no autenticada y sus controles estructurales. Su comando `a11y` no está disponible y el login UI autenticado no se automatizó para no persistir ni pasar contraseñas.
- La validación actual de OpenAPI queda pendiente: el binario local de Redocly no existe y no se instaló nada.

No se declara B01 completa ni se avanza a I01–I06.
