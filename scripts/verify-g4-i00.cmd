@echo off
setlocal

if "%JAVA_HOME%"=="" (
  echo ERROR: JAVA_HOME debe apuntar a un JDK 21 antes de verificar el backend.
  exit /b 1
)

if not defined AV_DB_URL goto :missing_environment
if not defined AV_DB_USER goto :missing_environment
if not defined AV_DB_PASSWORD goto :missing_environment
if not defined AV_MIGRATION_DB_USER goto :missing_environment
if not defined AV_MIGRATION_DB_PASSWORD goto :missing_environment
if not defined AV_DEMO_AURORA_PASSWORD goto :missing_environment

pushd "%~dp0..\backend"
call mvnw.cmd test
set VERIFY_EXIT=%errorlevel%
popd
if not "%VERIFY_EXIT%"=="0" exit /b %VERIFY_EXIT%

pushd "%~dp0..\frontend"
call node_modules\.bin\ng.cmd build
set VERIFY_EXIT=%errorlevel%
popd
if not "%VERIFY_EXIT%"=="0" exit /b %VERIFY_EXIT%

pushd "%~dp0..\frontend"
call node_modules\.bin\ng.cmd test --watch=false
set VERIFY_EXIT=%errorlevel%
popd
if not "%VERIFY_EXIT%"=="0" exit /b %VERIFY_EXIT%

echo G4-I00 local verification passed.
exit /b 0

:missing_environment
echo ERROR: faltan variables locales AV_DB_*, AV_MIGRATION_DB_* o AV_DEMO_AURORA_PASSWORD.
exit /b 1
