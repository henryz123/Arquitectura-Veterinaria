[CmdletBinding()]
param(
    [switch]$Backend,
    [switch]$Frontend
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

if (-not $Backend -and -not $Frontend) {
    $Backend = $true
    $Frontend = $true
}

if ($Backend) {
    if (-not $env:JAVA_HOME) {
        throw 'JAVA_HOME debe apuntar a un JDK 21 antes de verificar el backend.'
    }
    Push-Location (Join-Path $projectRoot 'backend')
    try {
        & .\mvnw.cmd test
        if ($LASTEXITCODE -ne 0) { throw 'Las pruebas del backend fallaron.' }
    } finally {
        Pop-Location
    }
}

if ($Frontend) {
    $ng = Join-Path $projectRoot 'frontend\node_modules\.bin\ng.cmd'
    if (-not (Test-Path -LiteralPath $ng)) {
        throw 'No existe Angular CLI local. Ejecute la instalación de dependencias indicada en README.md.'
    }
    Push-Location (Join-Path $projectRoot 'frontend')
    try {
        & $ng build
        if ($LASTEXITCODE -ne 0) { throw 'La compilación del frontend falló.' }
        & $ng test --watch=false
        if ($LASTEXITCODE -ne 0) { throw 'Las pruebas del frontend fallaron.' }
    } finally {
        Pop-Location
    }
}
