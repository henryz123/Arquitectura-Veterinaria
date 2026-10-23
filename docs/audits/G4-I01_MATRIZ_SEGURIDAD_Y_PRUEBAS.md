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
| Flyway V010 | 10 migraciones validadas; V010 aplicada una vez desde V009. | No sustituye pruebas de negocio. |
| B01 | 17 pruebas: 17 superadas, sin omisiones. Incluye bootstrap autenticable, suspensión, reactivación y denegación de administración tenant a SaaS. | Las operaciones transaccionales de la suite se revierten. |
| Backend completo | 23 pruebas: 23 superadas, sin omisiones. | Flyway permaneció desactivado durante la suite. |
| Build Angular | `npm.cmd run build` superado; bundle inicial 276.55 kB. | Requirió ejecución local fuera del sandbox por `spawn EPERM`. |
| OpenAPI | Sintaxis YAML y 29 referencias locales superadas con SnakeYAML; `npm run validate:contract` no pudo iniciar porque falta el binario Redocly. | Falta validación semántica por un validador OpenAPI instalado/autorizado; no se instaló nada. |
| E2E autenticado | Bloqueado. Agent Browser 0.27.0 tiene doce Chromium huérfanos sin ventana ni puerto; no se ejecutó login UI ni snapshot. | Requiere autorización separada para limpiar exactamente esa sesión o usar CDP temporal. |
| CSRF HTTP | Superado. SaaS y STAFF: logout sin token 403, con token 204 y contexto posterior 401. | Ejecución HTTP real contra backend local con Flyway desactivado. |
| Concurrencia de recuperación | Superado como ensayo reversible: dos conexiones `av_app` completaron en 2.34 s y ambas revirtieron; cero tokens activos residuales. | No demuestra una política de unicidad al confirmar dos transacciones; ese riesgo continúa. |

## Riesgos activos

1. La cuenta SaaS existe exclusivamente por el bootstrap privado; no se debe
   crear otra por HTTP ni mediante `av_app`.
2. La carrera de dos tokens activos no está eliminada: el ensayo reversible no
   equivale a una restricción de unicidad al hacer `COMMIT`.
3. La validación semántica OpenAPI y el E2E autenticado con Agent Browser siguen
   bloqueados por herramientas/sesión local; no se declaran superados.
4. G4-I01 permanece parcial: 16 de los 35 RF B01 siguen parciales o sin
   verificar.
