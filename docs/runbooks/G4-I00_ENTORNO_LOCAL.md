# G4-I00 — Entorno local y base exclusiva

## Estado de esta guía

Este procedimiento prepara una base local exclusiva. No se ejecutó durante G4-I00 porque el acceso administrador de PostgreSQL exige una contraseña que no debe proporcionarse a Codex, guardarse en un archivo ni aparecer en el historial.

## Roles mínimos

Abra un terminal local y ejecute el cliente de PostgreSQL 18 con solicitud interactiva de contraseña:

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -U postgres -W -d postgres
```

En la consola `psql`, cree los roles y la base exclusivos. Las instrucciones `\password` solicitan cada secreto de modo interactivo y no lo incorporan al historial del repositorio.

```sql
CREATE ROLE av_schema_owner LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
\password av_schema_owner
CREATE ROLE av_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
\password av_app
CREATE DATABASE arquitectura_veterinaria OWNER av_schema_owner;
```

No reutilice estos roles, la base ni contraseñas de otro proyecto. La cuenta de aplicación no debe poseer tablas, esquemas ni tener `BYPASSRLS`.

## Migración y privilegios de runtime

Inicie una sesión como `av_schema_owner` para que Flyway cree `tenancy` e `identity_auth` desde `database/migration/`. Después de la migración, otorgue al rol de runtime únicamente lo necesario:

```sql
GRANT CONNECT ON DATABASE arquitectura_veterinaria TO av_app;
GRANT USAGE ON SCHEMA tenancy, identity_auth TO av_app;
GRANT SELECT ON tenancy.tenant TO av_app;
GRANT SELECT, INSERT, UPDATE ON identity_auth.user_session TO av_app;
GRANT EXECUTE ON FUNCTION identity_auth.lookup_staff_identity(text, text) TO av_app;
```

La función de login es `SECURITY DEFINER`, parametrizada y con `search_path` fijo. Es la única lectura de credencial previa al contexto del tenant. El rol `av_app` queda sujeto a RLS para las lecturas y escrituras ordinarias.

## Datos de demostración

Como `av_schema_owner`, sobre la base exclusiva y después de la migración:

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -U av_schema_owner -W -d arquitectura_veterinaria -f database\demo\seed-synthetic.psql
```

El archivo pide dos contraseñas sintéticas en la consola, crea solo `veterinaria-aurora` y `veterinaria-bosque`, y usa el mismo correo sintético en ambos tenants. No se versiona ni imprime una contraseña.

## Variables de ejecución

Defina los nombres de conexión solo en su sesión local o mecanismo de secretos aprobado. Nunca los guarde en Git:

```text
AV_DB_URL=jdbc:postgresql://localhost:5432/arquitectura_veterinaria
AV_DB_USER=av_app
AV_DB_PASSWORD=<secreto local>
AV_MIGRATION_DB_USER=av_schema_owner
AV_MIGRATION_DB_PASSWORD=<secreto local>
AV_SECURE_COOKIE=false
```

En desarrollo local, el backend se inicia con `backend\mvnw.cmd spring-boot:run`; Angular usa el proxy `/api` a `http://localhost:8080`. Para una ejecución sin migración automática una vez preparada la base, añada `SPRING_FLYWAY_ENABLED=false` a la configuración local del proceso.

## Verificación obligatoria posterior

Antes de declarar el vertical integrado: ejecutar Flyway en la base exclusiva, cargar los datos sintéticos, iniciar backend y frontend, probar ambas sesiones, `GET /api/v1/tenant-profile/{slug}` propio y el rechazo 404 para el slug ajeno. Conservar evidencias sin cookies, CSRF ni contraseñas.
