@echo off
setlocal EnableExtensions

if /I not "%AV_DB_USER%"=="av_app" (
  echo ERROR: AV_DB_USER debe identificar exactamente al rol av_app.
  exit /b 1
)
if /I not "%AV_DB_URL%"=="jdbc:postgresql://localhost:5432/arquitectura_veterinaria" (
  echo ERROR: AV_DB_URL debe identificar exactamente la base local autorizada arquitectura_veterinaria.
  exit /b 1
)
if /I not "%AV_MIGRATION_DB_USER%"=="av_schema_owner" (
  echo ERROR: AV_MIGRATION_DB_USER debe identificar exactamente al rol av_schema_owner.
  exit /b 1
)
if "%AV_DB_URL%"=="" goto :missing_environment
if "%AV_DB_PASSWORD%"=="" goto :missing_environment
if "%AV_MIGRATION_DB_PASSWORD%"=="" goto :missing_environment

set "AV_FLYWAY_ENABLED=true"
set "AV_FLYWAY_TARGET=012"

pushd "%~dp0..\backend"
call mvnw.cmd -Dtest=AuthorizedFlywayMigrationIntegrationTest test
set "FLYWAY_EXIT=%ERRORLEVEL%"
popd

if not "%FLYWAY_EXIT%"=="0" (
  echo ERROR: Flyway no completo V012. Revise el historial antes de cualquier nuevo intento.
  exit /b %FLYWAY_EXIT%
)
exit /b 0

:missing_environment
echo ERROR: faltan variables locales de conexion. No se solicitaran ni registraran secretos.
exit /b 1
