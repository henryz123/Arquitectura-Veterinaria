# G4-I01 — mantenibilidad y seguridad de identidad SQL (V001–V010)

## Alcance y método

Auditoría estática de las fuentes V001–V010 y contraste del catálogo local de
`arquitectura_veterinaria` el 2026-10-22. Se verificaron propietarios, ACL,
`search_path`, RLS, historial Flyway y regresiones B01. No se modificaron
migraciones registradas, no se ejecutó `flyway repair` y no se creó V011.

`plpgsql_check` no está instalado; la revisión de ambigüedades es léxica y
funcional, no una certificación de análisis estático exhaustivo.

## Hallazgos cerrados por V010

| Hallazgo | Evidencia | Estado |
|---|---|---|
| Salidas `RETURNS TABLE` que colisionan con columnas | V007 y V008 calificaron `profile.principal_id` y `token.principal_id` en `invite_staff`. V010 califica columnas de `one_time_token` en `request_password_recovery`. | Corregido en versiones inmutables sucesivas. |
| Recuperación de contraseña | V010 redefine únicamente `identity_auth.request_password_recovery`; conserva firma, ramas SAAS/STAFF/CLIENT, uso único, invalidación previa, `statement_timestamp()` y el límite de 30 minutos. | Verificado por regresión B01. |
| Propiedad y ACL V010 | SHA-256 `AC3DF14997C5BD83E989E1AB97E4F4863636F850EC58AA185E979BCDD99CCFB3`; checksum Flyway `72123246`; una sola fila exitosa. | Verificado en catálogo. |
| Funciones privilegiadas | 39 funciones en los esquemas revisados; 38 `SECURITY DEFINER`, todas de `av_schema_owner`, ninguna ejecutable por `PUBLIC`, todas con `pg_catalog` y `pg_temp` explícito. `bootstrap_saas_administrator` es `SECURITY INVOKER`. | Verificado en catálogo. |
| SQL dinámico y resolución global | No se halló `SET ROLE`, `plpgsql.variable_conflict` ni `clock_timestamp()` en V001–V010. Las apariciones de `EXECUTE` son ACL o comentarios, no sentencias dinámicas. | Sin hallazgo estático. |
| Residuos de prueba | Tras B01, los perfiles `recovery-scope-%@demo.invalid` fueron cero. | Rollback comprobado por consulta posterior. |

## Controles de aislamiento observados

- `av_schema_owner` y `av_app` no son superusuarios ni tienen `BYPASSRLS`.
- `av_app` no puede crear objetos en esquemas confiables ni leer/actualizar
  directamente `principal`, `one_time_token` o `saas_administrator`.
- RLS está habilitado en `tenancy.tenant`, `account_profile`,
  `role_assignment`, `staff_account` y `user_session`. Las tablas sensibles
  sin RLS no son accesibles directamente por `av_app`; se exponen solo por
  funciones parametrizadas y acotadas.
- La regresión `scopesPasswordRecoveryToTheRequestedTenantWhenEmailsCoincide`
  verificó dos CLIENT con igual correo en Aurora y Bosque: recuperar Aurora no
  altera la contraseña de Bosque.

## Riesgos y trabajo pendiente

| Severidad | Riesgo | Evidencia y tratamiento propuesto |
|---|---|---|
| Cerrado para G4-I01 | Administración SaaS no validada extremo a extremo | Bootstrap privado único bajo `av_schema_owner`; estado/admin `1/1`, segundo intento rechazado, sesión SaaS, suspensión/reactivación y ACL comprobadas. |
| Media | Carrera de tokens activos | `one_time_token` no tiene restricción única activa por propósito/principal. Dos conexiones `av_app` concurrieron y revirtieron sin residuo; falta decidir una invariante confirmada antes de diseñar V011. |
| Media | Límite de 24 h en transacciones largas | Dos registros CLIENT en una transacción exterior reprodujeron el efecto heredado de `current_timestamp` en `register_client`; la prueba se preparó con 23 h para aislar V010. Evaluar solo con autorización. |
| Cerrado para G4-I01 | CSRF HTTP | Spring Security desactiva su módulo estándar y `CsrfSessionFilter` aplica el token ligado a sesión. HTTP real verificó 403 sin token, 204 con token y 401 tras logout para SaaS y STAFF. |
| Media | OpenAPI y E2E | SnakeYAML validó sintaxis y referencias internas, pero no sustituye un validador OpenAPI semántico. Agent Browser conserva una sesión Chromium huérfana y no completó login UI. No se consideran superados. |

## Reglas para cambios posteriores

1. No modificar V001–V010 ni reparar Flyway para ocultar diferencias.
2. Calificar columnas que compartan nombre con parámetros o salidas `RETURNS TABLE`.
3. Mantener owner, `SECURITY DEFINER` cuando corresponda, `search_path`,
   `REVOKE PUBLIC` y `GRANT av_app` en la misma migración transaccional.
4. Una eventual V011 requiere auditoría, autorización expresa y prueba de
   concurrencia/tenant aplicable.
