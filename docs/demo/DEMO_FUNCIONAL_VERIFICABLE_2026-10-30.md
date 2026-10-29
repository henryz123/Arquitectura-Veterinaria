# Demostración local verificable — 30 de octubre de 2026

## Estado de esta guía

Validado por Codex con Angular, Spring Boot, PostgreSQL y Chrome locales el 29 de octubre de 2026. La confirmación final de la demostración corresponde al responsable al repetir los recorridos en su navegador. No se afirma que B01–B06 estén completos.

## Preparación local

1. Inicie Spring Boot con las variables de `.env.local` y `AV_FLYWAY_ENABLED=false`, según `docs/runbooks/G4-I01_DEMO_LOCAL_2026-10-24.md`.
2. Inicie Angular en `127.0.0.1:4200` con el proxy a `127.0.0.1:8080`.
3. Confirme `http://127.0.0.1:8080/actuator/health` y `http://127.0.0.1:4200/client/login/veterinaria-aurora` con HTTP 200.
4. Introduzca las contraseñas exclusivamente desde las variables locales indicadas. No las copie en capturas, historial, argumentos o documentos.

## Recorrido 1 — CLIENT registra y vuelve a consultar una mascota

| Campo | Instrucción |
|---|---|
| URL | `http://127.0.0.1:4200/client/login/veterinaria-aurora` |
| Rol requerido | `CLIENT`, vinculado y aprobado en Aurora |
| Cuenta local | `AV_DEMO_CLIENT_AURORA_EMAIL` y `AV_DEMO_CLIENT_AURORA_PASSWORD` |
| Datos sintéticos | Nombre único, por ejemplo `Mascota Demo Manual 20261030`; especie `Canino`; raza `Mestizo`; sexo `Hembra`; edad estimada `24`; seña `Recorrido académico local`. |

1. Inicie sesión y espere el espacio privado de Aurora.
2. Verifique el mensaje de vinculación aprobada y la sección **Registrar ficha básica**.
3. Deje vacías fecha de nacimiento y edad estimada una vez: debe aparecer el mensaje que exige indicar una de las dos opciones. No se envía una petición ni se crea una mascota.
4. Complete la edad estimada y los demás datos de la tabla; pulse **Registrar mascota**.
5. Resultado esperado: mensaje de confirmación y una nueva tarjeta bajo **Fichas básicas autorizadas**.
6. En DevTools > Network, la petición es `POST /api/v1/client/pets` con respuesta `201`.
7. Recargue completamente la página. La tarjeta debe permanecer visible; el navegador vuelve a consultar `GET /api/v1/client/pets`.
8. Resultado observado por Codex: Chrome completó el registro, obtuvo `201`, recuperó el registro tras recargar y leyó su ficha con `GET /api/v1/client/pets/{id}` `200`.

Evidencia local no versionada: `client-pet-demo-2026-10-29.png` en el directorio de visualizaciones de Codex. Muestra una mascota sintética nueva tras la recarga, sin historia clínica expuesta.

## Recorrido 2 — ADMIN registra y comprueba inventario aislado

| Campo | Instrucción |
|---|---|
| URL | `http://127.0.0.1:4200/login/veterinaria-aurora` y luego `/inventory` |
| Rol requerido | `ADMIN_VETERINARIA` en Aurora |
| Cuenta local | correo sintético de administración Aurora; contraseña `AV_DEMO_AURORA_PASSWORD` |
| Datos sintéticos | SKU único, por ejemplo `DEMO-STOCK-20261030`; nombre `Producto demo inventario`; precio `21.00`; stock inicial `5`; motivo `Saldo académico 20261030`. |

1. Inicie sesión como administración Aurora y abra **Inventario**.
2. Registre el producto marcado como controlado por existencias y complete el alta inicial de `5` unidades con un motivo.
3. Compruebe que el producto aparece en la tabla de saldos y que el movimiento aparece en el historial.
4. Recargue la página: el saldo y el movimiento deben conservarse.
5. Opcional de aislamiento: inicie sesión en Bosque con `AV_DEMO_BOSQUE_PASSWORD`; el SKU y sus movimientos de Aurora no deben aparecer.
6. Resultado observado por Codex: la ruta persistió el saldo, lo recuperó tras recarga y Bosque recibió una lista sin los registros Aurora.

Evidencia local no versionada: `inventory-demo-2026-10-29.png` en el directorio de visualizaciones de Codex.

## Recorrido adicional — Grooming con rol asignado

| Campo | Instrucción |
|---|---|
| URL | `http://127.0.0.1:4200/login/veterinaria-aurora`, después `/grooming` |
| Rol requerido | `GROOMING` |
| Cuenta local | `AV_DEMO_GROOMER_AURORA_EMAIL` y `AV_DEMO_GROOMER_AURORA_PASSWORD` |

La pantalla muestra exclusivamente las reservas de grooming asignadas. El recorrido automatizado comprobó una tarea completada y que no aparece historia clínica. La corrección visual revisada mantiene la misma navegación, tarjetas, agenda y estado visible que el espacio de trabajo.

Evidencia local no versionada: `grooming-demo-fixed-2026-10-29.png` en el directorio de visualizaciones de Codex.

## Restricciones verificadas y no demostrables con la semilla actual

- La semilla local contiene CLIENT activo en Aurora/Bosque, STAFF `ADMIN_VETERINARIA` en Aurora/Bosque y cuentas separadas con `GROOMING` y `VETERINARIO` en Aurora.
- No hay cuenta ni rol activo `COLABORADOR` o `RECEPCION`. `COLABORADOR` tampoco es un código de rol admitido por la V1. Por ello no se afirma una prueba de panel para ese rol ni una confirmación realizada por recepción.
- La consulta B02 de revisiones autorizadas fue comprobada con ADMIN Aurora: ambas colas devolvieron HTTP 200 en Chrome. El acceso de ADMIN sin `VETERINARIO` a historia clínica devuelve 403 de forma intencional.
- El POS autenticado queda fuera del guion porque aún faltan `AV_DEMO_CASHIER_AURORA_EMAIL` y `AV_DEMO_CASHIER_AURORA_PASSWORD`.

## Alternativa segura de presentación

1. Mostrar los dos recorridos principales desde el navegador; no depender de los artefactos como sustituto de la ejecución.
2. Si falla un servicio local, mostrar únicamente las capturas de CLIENT, inventario y Grooming que corresponden a los recorridos ejecutados, junto con `docs/architecture/09_GUIA_DE_INTEGRACION_Y_EJECUCION_PREVISTA.md` para explicar Angular → Spring Boot → PostgreSQL.
3. Declarar el alcance real: B01–B03 y B04 tienen verticales operativos y evidencias parciales; B05 y B06 no se presentan como terminados.
