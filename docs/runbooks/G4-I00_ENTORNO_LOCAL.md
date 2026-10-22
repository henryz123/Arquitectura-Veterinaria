# G4-I00 — Entorno local y base exclusiva

## Estado de esta guía

Este procedimiento se ejecutó y verificó localmente para G4-I00 sobre la base exclusiva `arquitectura_veterinaria`. Sigue siendo la referencia para reproducir el entorno; no contiene ni debe recibir contraseñas, cookies, tokens o CSRF.

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
REVOKE ALL ON DATABASE arquitectura_veterinaria FROM PUBLIC;
GRANT CONNECT, TEMPORARY ON DATABASE arquitectura_veterinaria TO av_schema_owner, av_app;
REVOKE ALL ON SCHEMA tenancy, identity_auth FROM PUBLIC;
GRANT USAGE ON SCHEMA tenancy, identity_auth TO av_app;
GRANT SELECT ON tenancy.tenant TO av_app;
GRANT SELECT, INSERT, UPDATE ON identity_auth.user_session TO av_app;
GRANT EXECUTE ON FUNCTION identity_auth.lookup_staff_identity(text, text) TO av_app;
```

La función de login es `SECURITY DEFINER`, parametrizada y con `search_path` fijo. Es la única lectura de credencial previa al contexto del tenant. El rol `av_app` queda sujeto a RLS para las lecturas y escrituras ordinarias.

## Datos de demostración

Como `av_schema_owner`, sobre la base exclusiva y después de la migración, ejecute el script existente una única vez. Introduzca las dos contraseñas sintéticas en la consola local cuando el script las solicite; no las pase como argumentos ni las copie en el historial:

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -U av_schema_owner -W -d arquitectura_veterinaria -f database\demo\seed-synthetic.psql
```

El archivo pide dos contraseñas sintéticas en la consola, crea solo `veterinaria-aurora` y `veterinaria-bosque`, y usa el mismo correo sintético en ambos tenants. No se versiona ni imprime una contraseña.

## Variables de ejecución

Defina los nombres de conexión solo en la sesión local o en `.env.local` ignorado. Nunca los guarde en Git ni los imprima:

```text
AV_DB_URL=jdbc:postgresql://localhost:5432/arquitectura_veterinaria
AV_DB_USER=av_app
AV_DB_PASSWORD=<secreto local>
AV_MIGRATION_DB_USER=av_schema_owner
AV_MIGRATION_DB_PASSWORD=<secreto local>
AV_SECURE_COOKIE=false
```

Para iniciar el backend con la versión correcta de Java en la consola actual:

```powershell
$env:JAVA_HOME = 'C:\Users\HNR05\.jdks\ms-21.0.12.1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location backend
.\mvnw.cmd spring-boot:run
```

Angular usa el proxy `/api` a `http://localhost:8080`:

```powershell
Set-Location frontend
.\node_modules\.bin\ng.cmd serve --host 127.0.0.1 --port 4200
```

`AV_SECURE_COOKIE=false` solo corresponde a HTTP local. Para una futura URL HTTPS autorizada deberá ser `true`. Para una ejecución local posterior sin Flyway automático, añada `SPRING_FLYWAY_ENABLED=false` únicamente al proceso después de comprobar la versión de esquema.

## Verificación obligatoria posterior

Antes de declarar el vertical integrado, ejecute desde la raíz, con las variables locales disponibles:

```powershell
.\scripts\verify-g4-i00.cmd
```

Compruebe además: una sola V001 exitosa, ambas cuentas sintéticas, login de Aurora y Bosque, perfil propio 200, slug ajeno 404, logout sin CSRF 403, logout con CSRF 204 y cero sesiones activas después del cierre. Conserve solo códigos y conteos: nunca cookies, hashes, CSRF ni contraseñas.
