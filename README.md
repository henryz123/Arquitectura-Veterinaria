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

El servicio PostgreSQL está activo, pero la creación de la base exclusiva requiere una sesión administrativa interactiva. Sigue el [runbook local](docs/runbooks/G4-I00_ENTORNO_LOCAL.md); no proporciones contraseñas en chat, archivos ni comandos.

## Instalación y verificación

En `frontend/`, usa Corepack/pnpm 11.25.0 y respeta `pnpm-workspace.yaml`, que bloquea scripts de construcción nativos de dependencias transitorias. Los binarios precompilados que Angular requiere se resuelven desde paquetes oficiales.

```powershell
corepack pnpm install
```

Con JDK 21 disponible en `JAVA_HOME`, desde la raíz:

```powershell
.\scripts\verify-g4-i00.cmd
```

La política de ejecución actual bloquea scripts `.ps1`; el lanzador `.cmd` evita modificar o eludir dicha política.

El backend no debe considerarse integrado hasta seguir el runbook, migrar la base exclusiva y ejecutar las pruebas PostgreSQL, de autenticación y de aislamiento tenant. Los comandos de ejecución se mantienen locales: no existe remoto Git ni despliegue autorizado.

## Estado G4-I00

- Compilación y pruebas unitarias backend: verificadas localmente.
- Compilación y prueba de servicio frontend: verificadas localmente.
- Validación de OpenAPI: ejecutada con Redocly antes de materializar el proveedor; el contrato no cambió después de esa validación.
- Integración PostgreSQL, E2E de navegador y verificación de migraciones: pendientes de una base exclusiva con acceso administrativo local.

No se declara B01 completa ni se avanza a I01–I06.
