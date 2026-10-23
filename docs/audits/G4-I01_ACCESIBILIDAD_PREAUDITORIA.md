# G4-I01 - comprobacion de accesibilidad previa a V012

## Alcance y limite de la evidencia

La comprobacion se realizo el 2026-10-23 contra el frontend Angular local en
`/login/veterinaria-aurora`, con backend local temporal y Flyway desactivado.
No se aplicaron migraciones ni se usaron datos reales.

No hay una herramienta axe disponible en el entorno:

| Componente | Estado comprobado |
|---|---|
| `@axe-core/playwright` | Ausente |
| `axe-core` | Ausente |
| `playwright` | Ausente |
| `playwright-core` | Disponible, version 1.58.2 |
| Agent Browser | Disponible, version 0.27.0, con `snapshot` pero sin comando `a11y` ni motor de reglas axe |

Por la prohibicion vigente de instalar dependencias, no se puede emitir un
resultado de auditoria automatica axe/WCAG. Esta comprobacion no se presenta
como equivalente a axe ni como certificacion de accesibilidad.

## Evidencia ejecutada

- GET de la pagina de acceso: HTTP 200.
- Arbol de accesibilidad de Agent Browser: un `main` con nombre por el H1,
  H1 unico, dos textbox con etiquetas visibles, boton `Ingresar` y navigation
  con nombre `Cambiar contexto de demostracion` y enlaces Aurora/Bosque.
- Consola de Agent Browser: sin errores registrados durante la carga.
- Red de la pagina: documento, CSS, JavaScript y recursos del desarrollo con
  HTTP 200; no se observaron 4xx/5xx en el recorrido no autenticado.
- El intento de campo invalido y envio sin credenciales no invoco la API; se
  mantuvo como comprobacion de formulario local.

## Teclado y foco manual asistido

La secuencia observada con `focus` y `Tab` fue:

```text
Correo institucional -> Contraseña -> Ingresar -> Aurora -> Bosque
```

El CSS declara `:focus-visible` para input, enlace y boton con `outline: 3px`
y offset de 2px. La pagina usa controles nativos, etiquetas `for`/`id`,
`aria-invalid`, `aria-describedby` y mensajes `role="alert"` cuando el
formulario marca los controles como tocados. Esta es evidencia estructural y
de recorrido; no reemplaza una lectura con lector de pantalla.

## Respuesta visual acotada

Se verifico que la pagina no tiene desbordamiento horizontal en tres anchos:

| Viewport | `scrollWidth` / `clientWidth` | Resultado |
|---|---:|---|
| 375 x 812 | 375 / 375 | Sin desbordamiento horizontal |
| 768 x 900 | 768 / 768 | Sin desbordamiento horizontal |
| 1440 x 900 | 1440 / 1440 | Sin desbordamiento horizontal |

No existe una linea base de capturas aprobada. Por tanto, la regresion visual
comparativa es **inconclusa**, no aprobada implicitamente.

## Dictamen y siguiente control

No se observo un defecto bloqueante en la unica pantalla evaluada, pero el
dictamen de accesibilidad global es **pendiente** por falta de motor axe,
lector de pantalla y cobertura de pantallas B02--B06. Antes de una URL publica
se requiere una de estas decisiones: autorizar una dependencia axe compatible
con Playwright, o proporcionar una herramienta de auditoria equivalente ya
instalada y ejecutar el conjunto de rutas que exista entonces. Ningun resultado
de este documento permite declarar al producto completo accesible.
