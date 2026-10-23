# G4-I01 - plan alternativo para la presentacion local

## Regla

Si PostgreSQL, Spring Boot o Angular no inicia, no improvisar cambios de codigo,
migraciones, credenciales ni comandos de reparacion. Presentar solo evidencia ya
verificada y declarar el incidente local.

## Secuencia de contingencia

1. Abra `docs/evidence/g4-i01-presentacion-2026-10-24/01-login-aurora.png`.
   Es la pantalla de login real antes de completar datos.
2. Abra `02-workspace-aurora.png` y `03-workspace-bosque.png`. Explique que son
   capturas de un E2E autenticado, no disenos de referencia, y compare los dos
   slugs para explicar el aislamiento tenant.
3. Muestre `docs/status/G4-I01_PUNTO_REANUDACION_2026-10-23.md`: V001--V012,
   SHA-256 de V012, ausencia de V013, resultado 29/30 y defecto pendiente.
4. Muestre `docs/implementation-plan/06_TRAZABILIDAD_B01_G4_I01.md`: 26 RF con
   evidencia puntual, 9 parciales; no afirmar B01 terminado.
5. Muestre `docs/contracts/openapi-v1.yaml` y explique que Redocly lo valido
   localmente durante esta preparacion.
6. Muestre `docs/runbooks/G4-I01_CONTROL_FLYWAY.md` y explique que las doce
   migraciones ya registradas se conservan y que Flyway se mantiene desactivado
   durante la ejecucion ordinaria.
7. Muestre `docs/architecture/` y el arbol del repositorio para explicar
   Java/Spring, Angular y PostgreSQL sin afirmar que B02--B06 ya existen.

## Evidencia de prueba disponible

- E2E autenticado: `G4_I01_AUTHENTICATED_BROWSER_E2E=PASS` el 2026-10-23.
- Build Angular: correcto el 2026-10-23.
- Redocly/OpenAPI: valido el 2026-10-23.
- Agent Browser: arbol semantico confirma `main`, campos "Correo institucional"
  y "Contraseña", boton "Ingresar" y enlaces Aurora/Bosque.

No hay video de respaldo: Agent Browser no encontro `ffmpeg`, y no se instalo
software durante la congelacion. Las tres PNG verificadas sustituyen ese respaldo.
La visualizacion literal `sint\\u00e9tica` en los nombres de tenant proviene de la
semilla sintetica historica; es un defecto de presentacion no funcional y no se
modifica durante esta congelacion.
