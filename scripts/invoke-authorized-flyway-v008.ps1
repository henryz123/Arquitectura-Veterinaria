[CmdletBinding()]
param(
    [switch]$ApplyV008
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if (-not $ApplyV008) {
    throw 'Este lanzador solo ejecuta V008 tras una autorización expresa. Use -ApplyV008.'
}

if ($env:AV_DB_USER -ne 'av_app') {
    throw 'AV_DB_USER debe identificar exactamente al rol de aplicación av_app.'
}
if ($env:AV_DB_URL -ne 'jdbc:postgresql://localhost:5432/arquitectura_veterinaria') {
    throw 'AV_DB_URL debe identificar exactamente la base local autorizada arquitectura_veterinaria.'
}
if ($env:AV_MIGRATION_DB_USER -ne 'av_schema_owner') {
    throw 'AV_MIGRATION_DB_USER debe identificar exactamente al rol de migración av_schema_owner.'
}
foreach ($name in 'AV_DB_URL', 'AV_DB_PASSWORD', 'AV_MIGRATION_DB_PASSWORD') {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "Falta la variable local requerida $name. No se solicitarán ni registrarán secretos."
    }
}

$saved = @{}
foreach ($name in 'AV_FLYWAY_ENABLED', 'AV_FLYWAY_TARGET') {
    $saved[$name] = [Environment]::GetEnvironmentVariable($name)
}

$projectRoot = Split-Path -Parent $PSScriptRoot
try {
    $env:AV_FLYWAY_ENABLED = 'true'
    $env:AV_FLYWAY_TARGET = '008'

    Push-Location (Join-Path $projectRoot 'backend')
    try {
        & .\mvnw.cmd '-Dtest=AuthorizedFlywayMigrationIntegrationTest' test
        if ($LASTEXITCODE -ne 0) {
            throw 'Flyway no completó V008; revise el historial antes de cualquier nuevo intento.'
        }
    } finally {
        Pop-Location
    }
} finally {
    foreach ($name in $saved.Keys) {
        if ($null -eq $saved[$name]) {
            Remove-Item "Env:$name" -ErrorAction SilentlyContinue
        } else {
            Set-Item "Env:$name" $saved[$name]
        }
    }
}
