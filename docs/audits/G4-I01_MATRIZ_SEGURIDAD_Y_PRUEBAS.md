# G4-I01 — matriz de seguridad y pruebas

## Controles verificados

| Control | Resultado | Evidencia 2026-10-22 |
|---|---|---|
| Historial Flyway | V001–V010: diez filas exitosas, una por versión; V010 checksum `72123246`. | Launcher V010 y catálogo local. |
| Fuente V010 | SHA-256 `AC3DF14997C5BD83E989E1AB97E4F4863636F850EC58AA185E979BCDD99CCFB3`. | Hash previo y revisión ECC independiente. |
| Roles | `av_app` y `av_schema_owner` no son superusuarios ni tienen `BYPASSRLS`. | `pg_roles`. |
| Definición V010 | `request_password_recovery`: `av_schema_owner`, `SECURITY DEFINER`, `search_path=pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp`. | `pg_proc` posterior. |
| ACL V010 | `PUBLIC=false`, `av_app=true`; sin SELECT/UPDATE directo de `av_app` sobre identidad o SaaS. | `has_function_privilege` y `has_table_privilege`. |
| Aislamiento de recuperación | Dos CLIENT sintéticos del mismo correo mantienen credenciales independientes en Aurora/Bosque. | Regresión B01 transaccional. |
| RLS y acceso indirecto | RLS activo en tablas contextuales; identidad sensible solo por funciones acotadas para `av_app`. | Catálogo local. |
| Bootstrap SaaS | Función privada `SECURITY INVOKER`; `PUBLIC` y `av_app` sin EXECUTE; una única inicialización SaaS existe. | V004 SHA-256 `C4431C604864C75742F00F75BAFE28FB219196A7526B9A01AD7A100A0D21E2FF`, conteos `1/1` y segundo intento rechazado. |
| Flyway ordinario | Desactivado por defecto; target defensivo `010`. | `application.yml` y runbook. |

## Resultados reales

| Grupo | Resultado | Limitación |
|---|---|---|
| Flyway V010 | Flyway validó formalmente las 10 migraciones; esquema local en V010 y sin migración nueva necesaria. | No sustituye pruebas de negocio. |
| B01 | 17 pruebas: 17 superadas, sin omisiones. Incluye bootstrap autenticable, suspensión, reactivación y denegación de administración tenant a SaaS. | Las operaciones transaccionales de la suite se revierten. |
| Backend completo | 23 pruebas: 23 superadas, sin omisiones. | Flyway permaneció desactivado durante la suite. |
| Build Angular | `corepack pnpm run build` superado; bundle inicial 276.55 kB. | Requirió ejecución local fuera del sandbox por `spawn EPERM`. |
| OpenAPI | Redocly CLI 2.67.1 validó formalmente `openapi-v1.yaml` como OpenAPI 3.1. | El documento sigue con drift: 23 endpoints B01 reales no están descritos y sesión SaaS es incompleta. |
| E2E autenticado | Superado con Playwright Core 1.58.2 y Chromium instalado por Agent Browser: login STAFF, perfil protegido, logout CSRF y denegación posterior. | Agent Browser CLI 0.27.0 se bloquea al cerrar, por lo que su auditoría propia no se declara superada. |
| CSRF HTTP | Superado. SaaS y STAFF: logout sin token 403, con token 204 y contexto posterior 401. | Ejecución HTTP real contra backend local con Flyway desactivado. |
| Concurrencia de recuperación | Defecto reproducido con fixture aislado y `COMMIT`: dos conexiones `av_app` dejaron 2 tokens activos y ambos se consumieron secuencialmente; limpieza confirmada. | Riesgo crítico. No se creó V011 ni se modificó V001–V010. |

## Riesgos activos

1. La cuenta SaaS existe exclusivamente por el bootstrap privado; no se debe
   crear otra por HTTP ni mediante `av_app`.
2. La carrera de dos tokens activos está reproducida al hacer `COMMIT`: ambos
   enlaces de recuperación fueron utilizables en el fixture aislado.
3. El contrato OpenAPI es sintácticamente válido, pero no está alineado con
   todos los endpoints B01 reales ni representa completamente la sesión SaaS.
4. Agent Browser 0.27.0 continúa bloqueándose al cerrar sesiones locales;
   Playwright compatible aportó la E2E, pero el CLI no tiene evidencia propia
   de accesibilidad o regresión visual.
5. G4-I01 permanece parcial: 16 de los 35 RF B01 siguen parciales o sin
   verificar.
