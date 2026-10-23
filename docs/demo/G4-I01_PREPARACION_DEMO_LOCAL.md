# G4-I01 — preparación de demostración local

## Estado demostrable

El vertical disponible demuestra autenticación de personal de una veterinaria
sintética, perfil protegido y cierre de sesión con CSRF. No representa los
módulos B02–B06 ni una URL pública.

## Precondiciones locales

- Java 21 y Node 22 activos.
- PostgreSQL 18 local con la base `arquitectura_veterinaria` exclusiva.
- `.env.local` presente, ignorado por Git y con credenciales sintéticas.
- Flyway permanece desactivado de forma ordinaria. Validar migraciones sólo con
  `scripts/invoke-authorized-flyway-v010.cmd` bajo autorización.

## Recorrido verificado

1. Ejecutar backend local con `AV_FLYWAY_ENABLED=false`.
2. Ejecutar Angular local con su proxy hacia el backend.
3. Abrir la ruta de acceso de Aurora y usar únicamente la cuenta sintética local.
4. Verificar llegada al espacio de trabajo y carga del perfil de la veterinaria.
5. Cerrar sesión; el navegador debe volver al acceso y negar una visita posterior
   a `/workspace`.

La E2E automatizada equivalente está en
`frontend/e2e/g4-i01-authenticated.e2e.mjs`. Lee secretos sólo de variables de
proceso y reutiliza Chromium local. No añadir credenciales, capturas con datos
sensibles ni URL pública a este documento.

## Límites antes de presentación pública

- No hay despliegue ni repositorio remoto autorizado en G4-I01.
- El defecto de concurrencia de recuperación documentado en
  `docs/audits/G4-I01_CONCURRENCIA_RECUPERACION.md` debe resolverse y verificarse
  antes de exponer una URL pública.
- Revisar secretos, licencia, datos sintéticos, OpenAPI y Agent Browser antes de
  cualquier publicación.
