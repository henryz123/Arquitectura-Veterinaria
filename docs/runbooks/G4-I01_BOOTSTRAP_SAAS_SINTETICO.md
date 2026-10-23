# G4-I01 — bootstrap privado de administrador SaaS sintético

## Estado actual y límite

El bootstrap autorizado se ejecutó una única vez el 2026-10-22 sobre la base
local exclusiva. El catálogo posterior muestra una fila en
`tenant_admin.saas_bootstrap_state` y una en `tenant_admin.saas_administrator`.
No se registran correo, contraseña, hash, token ni identificador de esa cuenta
en este documento. Aurora y Bosque no sustituyen una cuenta SaaS global.

La única ruta aprobada es
`tenant_admin.bootstrap_saas_administrator(varchar, varchar)`. Es
`SECURITY INVOKER`, exige `session_user = av_schema_owner`, toma un bloqueo
advisory transaccional y rechaza estado o administrador previo. `PUBLIC` y
`av_app` no tienen `EXECUTE`; `av_schema_owner` es el único ejecutor permitido.
El segundo intento controlado fue rechazado con el estado esperado y los
conteos se conservaron en `1/1`.

## Procedimiento aplicado y no repetible

1. El responsable definió correo y contraseña **sintéticos** solo en
   `.env.local` ignorado; no se imprimieron ni versionaron.
2. El operador conectó como `av_schema_owner` y envió ambos valores solo por
   entrada estándar local para generar un hash BCrypt y llamar la función en una
   transacción explícita.
3. Se verificaron una sola fila de estado y una cuenta global, ACL `PUBLIC=false`
   y `av_app=false`, y ausencia de SELECT directo de `av_app` sobre identidad,
   SaaS o enlaces.
4. B01 validó sesión SaaS, suspensión Aurora, aislamiento de Bosque,
   reactivación y denegación de administración laboral al actor SaaS.

No volver a ejecutar este procedimiento. Si la cuenta deja de ser utilizable o
se plantea recuperar administración, abrir un cambio de control: el bootstrap
no es una ruta de recuperación ni habilita crear un segundo administrador.
