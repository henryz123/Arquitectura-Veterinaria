# G4-I01 — matriz de seguridad y pruebas

## Controles verificados

| Control | Resultado vigente | Evidencia |
|---|---|---|
| Historial Flyway | V001–V011 aplicadas una vez y exitosas; V011 checksum `-1337847367`. | Consulta local de `flyway_schema_history` posterior a Flyway. |
| Fuente V011 | SHA-256 `508A558B90F33C74B1382696214DC45D167E389DED59E0A7F10B63968700167E`. | Preflight, dry-run transaccional y archivo aplicado. |
| Roles | `av_app` y `av_schema_owner` no son superusuarios ni tienen `BYPASSRLS`. | `pg_roles` local. |
| Funciones V011 | `request_password_recovery` y `reset_password_with_recovery`: owner `av_schema_owner`, `SECURITY DEFINER`, `search_path=pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp`. | `pg_proc` posterior. |
| ACL V011 | `PUBLIC=false`, `av_app=true`; `av_app` sin DML/lectura directa de `principal` ni `one_time_token`. | `has_function_privilege` y `has_table_privilege`. |
| Integridad recuperación | Índice parcial único activo y cero grupos duplicados tras aplicar. | Catálogo PostgreSQL y regresión concurrente. |
| RLS y acceso indirecto | RLS/contexto se conservan; las identidades sensibles siguen expuestas solo mediante funciones parametrizadas. | Catálogo local y pruebas B01. |
| Bootstrap SaaS | Privado, `SECURITY INVOKER`, sin `PUBLIC` ni `av_app`; una sola inicialización. | Catálogo y regresión B01 previa. |
| Flyway ordinario | Desactivado por defecto; target defensivo continúa en `010`. | `application.yml` y runbook; V011 solo por launcher autorizado. |

## Resultados reales

| Grupo | Resultado | Limitación conocida |
|---|---|---|
| Flyway V011 | Validó 11 migraciones y aplicó V011 una vez, desde V010. | Advertencia no bloqueante de metadatos remotos Flyway (401); no sustituye pruebas de negocio. |
| Concurrencia recuperación | `COMMIT_2`, `COMMIT_5`, aislamiento, rollback, fallo/rollback, expiración, reutilización, deadlock, repetición y limpieza: PASS. | La invariante está deliberadamente limitada a `PASSWORD_RECOVERY`. |
| B01 | 17 pruebas: 17 superadas, sin omisiones. | No cubre por sí sola los 35 RF completos. |
| Backend completo | 23 pruebas: 23 superadas, sin omisiones; Flyway desactivado durante la suite. | Advertencia no bloqueante de Mockito sobre agente dinámico. |
| Build y prueba Angular | Última evidencia G4-I01: build superado y 1 prueba Angular superada. | V011 no modifica frontend; se revalida antes del commit de cierre. |
| HTTP / CSRF | Logout SaaS y STAFF: sin token 403, con token 204 y contexto posterior 401. | V011 no cambia las rutas HTTP. |
| OpenAPI | Redocly validó OpenAPI 3.1 y el contraste de rutas reportó 29 operaciones de controlador y 29 de contrato, sin diferencias. | El contrato cubre B01 implementado; B02–B06 siguen fuera de implementación. |
| E2E autenticado | Evidencia previa: Playwright Core ejecutó login STAFF, perfil protegido, logout CSRF y denegación posterior. | La reejecución 2026-10-23 quedó bloqueada: Angular no expuso el puerto local tras dos arranques temporales y un diagnóstico de 64 s; no se declara una nueva E2E superada. Agent Browser CLI sigue bloqueado al cierre. |

## Riesgos activos

1. `LocalNotificationMailbox` conserva enlaces opacos en memoria para el
   entorno sintético local; debe sustituirse o aislarse antes de URL pública.
2. Agent Browser 0.27.0 conserva procesos Chromium huérfanos y no aporta
   evidencia propia de accesibilidad o regresión visual. Playwright es la
   evidencia E2E disponible, no un reemplazo automático de una auditoría a11y.
3. G4-I01 continúa parcial: 16 de 35 requisitos B01 permanecen parciales o no
   verificados. La corrección V011 elimina la carrera, no cierra alcance B01 no
   probado.
4. No existe despliegue, URL pública ni remoto configurado; su preparación
   exige revisión de secretos, licencias, datos sintéticos y autorización.
