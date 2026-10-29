# Guía de demostración académica final — 30 de octubre de 2026

## Propósito y límite

Esta guía presenta los flujos locales que se verificaron desde Chromium con datos sintéticos:

- acceso separado de CLIENT y STAFF;
- alta y consulta de mascota desde CLIENT y STAFF;
- atención clínica mediante una cuenta VETERINARIO autorizada;
- inventario: categoría, producto y movimiento de ingreso.

La demostración usa únicamente `arquitectura_veterinaria` en la máquina local. No muestra contraseñas, tokens, datos personales reales, pagos, facturación, B05 ni B06. El POS no es parte del recorrido principal: no existe una cuenta sintética CAJERO preparada para presentarlo.

## Preparación (dos ventanas de PowerShell)

Abra ambas ventanas en la raíz del proyecto:

```powershell
Set-Location 'C:\Users\HNR05\Documents\Arquitectura-Veterinaria'
```

### Ventana 1 — Spring Boot

Este bloque carga las variables locales sin imprimir sus valores y mantiene Flyway desactivado. No aplique migraciones durante la demostración.

```powershell
$dotenv = @{}
Get-Content .env.local | ForEach-Object {
  if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
    $value = $matches[2].Trim()
    if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
      $value = $value.Substring(1, $value.Length - 2)
    }
    $dotenv[$matches[1]] = $value
  }
}
foreach ($key in @('AV_DB_URL', 'AV_DB_USER', 'AV_DB_PASSWORD', 'AV_MIGRATION_DB_USER', 'AV_MIGRATION_DB_PASSWORD')) {
  if (-not $dotenv[$key]) { throw "Falta la variable local requerida: $key" }
  Set-Item -Path "Env:$key" -Value $dotenv[$key]
}
$env:AV_FLYWAY_ENABLED = 'false'
$env:JAVA_HOME = 'C:\Users\HNR05\.jdks\ms-21.0.12.1'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location .\backend
.\mvnw.cmd spring-boot:run
```

Espere la disponibilidad local en `http://127.0.0.1:8080/actuator/health` (HTTP 200).

### Ventana 2 — Angular

```powershell
Set-Location 'C:\Users\HNR05\Documents\Arquitectura-Veterinaria\frontend'
npm.cmd run start -- --host 127.0.0.1 --port 4200
```

Abra `http://127.0.0.1:4200`. Si 8080 o 4200 ya están ocupados, identifique su PID antes de actuar; no finalice procesos ajenos.

## Cuentas sintéticas

Escriba las contraseñas directamente en el navegador; no las copie en consola ni capturas.

| Contexto | Ruta | Correo | Contraseña local |
|---|---|---|---|
| STAFF Aurora | `/login/veterinaria-aurora` | `administracion@demo.invalid` | `AV_DEMO_AURORA_PASSWORD` |
| CLIENT Aurora | `/client/login/veterinaria-aurora` | `AV_DEMO_CLIENT_AURORA_EMAIL` | `AV_DEMO_CLIENT_AURORA_PASSWORD` |
| VETERINARIO Aurora | `/login/veterinaria-aurora` | `AV_DEMO_VETERINARIO_AURORA_EMAIL` | `AV_DEMO_VETERINARIO_AURORA_PASSWORD` |

Las tres referencias de contraseña son nombres de variables de `.env.local`, archivo ignorado por Git. No se incluyen valores en esta guía.

## Guion recomendado (5–7 minutos)

### 1. Portal CLIENT — 2 minutos

1. Abra `http://127.0.0.1:4200/client/login/veterinaria-aurora` e inicie sesión con CLIENT Aurora.
2. Muestre la pantalla **Inicio**: identifica la veterinaria y comunica que el acceso se limita a información propia autorizada.
3. Seleccione **Mis mascotas** y abra la ficha sintética existente `Luna`. Si no estuviera, use **Registrar mascota** y cree una ficha sintética con nombre, especie y edad o fecha de nacimiento.
4. Destaque que el cliente puede actualizar información básica de su mascota, pero no ve diagnósticos, prescripciones ni historia clínica completa.
5. Cierre sesión. Intentar abrir `/workspace` con la sesión CLIENT redirige al inicio de sesión STAFF: es una comprobación visible de separación de contextos.

### 2. Portal STAFF e inventario — 2 minutos

1. Inicie sesión STAFF Aurora y confirme que el encabezado muestra **Inicio** y el rol de la sesión, no una vista genérica de colaborador.
2. Entre en **Clientes y mascotas**, seleccione el propietario vinculado y muestre la misma mascota `Luna` persistida en PostgreSQL.
3. Entre en **Inventario**. Muestre la categoría `Higiene y cuidado`, el producto `Champú Canino Suave 250 ml` y su movimiento de ingreso inicial. Si el entorno parte sin esa semilla, créelos desde los formularios visibles.
4. Recargue la página de inventario para demostrar que no son datos fijos de interfaz.

### 3. Atención clínica — 1 a 2 minutos

1. Cierre la sesión STAFF e inicie sesión con VETERINARIO Aurora, sin reutilizar el administrador para una operación clínica.
2. En **Clientes y mascotas**, seleccione la mascota vinculada y registre o abra la atención sintética **Control veterinario preventivo**.
3. Muestre la consulta del historial desde el rol clínico. Aclare que este acceso está limitado por rol y tenant; el portal CLIENT no ofrece la historia clínica completa.

### 4. Cierre — menos de un minuto

1. Cierre sesión y vuelva a CLIENT Aurora para mostrar que las rutas STAFF no permanecen accesibles.
2. Recargue una ficha o inventario para enfatizar persistencia real.
3. Indique el alcance con precisión: B01–B04 tienen recorridos demostrables seleccionados; POS, pedidos web, pagos reales, SUNAT y operaciones de B05/B06 no se presentan como terminados.

## Evidencia técnica disponible

La estabilización local ejecutó correctamente:

- Angular: 9 archivos de prueba y 29 pruebas.
- Build de Angular.
- E2E Chromium: separación CLIENT/STAFF, CLIENT→STAFF con mascota persistida, registro CLIENT, atención VETERINARIO e inventario.

Las pruebas usan datos sintéticos y no sustituyen una auditoría de producción. Las advertencias actuales de presupuesto CSS son visuales y no bloquean el build: `client-space-page.component.scss` (4.88 kB frente a 4 kB) e `inventory-catalog-page.component.scss` (4.46 kB frente a 4 kB).

## Cierre seguro

Pulse `Ctrl+C` una vez en cada ventana de Angular y Spring Boot. No detenga PostgreSQL ni ejecute Flyway para cerrar la demostración.
