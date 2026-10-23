# G4-I01 — evaluación de preparación de demostración

## Dictamen al 2026-10-23

**Preparación local parcial; no apta todavía para una URL pública.** El vertical
local demuestra autenticación STAFF, CLIENT y SaaS, perfil/espacio protegido,
logout con CSRF, administración SaaS acotada y recuperación de contraseña con
invariante concurrente verificada. No representa B02–B06 ni una entrega final.

## Evidencia local disponible

1. Backend: 23/23 pruebas superadas; B01: 17/17 superadas.
2. Recuperación: V011 aplicada una vez y regresión de concurrencia con dos y
   cinco `COMMIT` simultáneos superada, sin residuos.
3. HTTP: SaaS y STAFF devuelven 403 sin CSRF en logout, 204 con token y 401
   después de cerrar sesión.
4. E2E: la evidencia previa de Playwright Core cubre Angular → Spring Boot →
   PostgreSQL para login STAFF, perfil protegido, logout y denegación posterior
   de `/workspace`. La reejecución del 2026-10-23 quedó bloqueada porque Angular
   no expuso su puerto local después de dos arranques temporales y un diagnóstico
   de 64 s; no se registra como una nueva ejecución exitosa.
5. Contrato: OpenAPI 3.1 validado por Redocly y 29 operaciones de controlador
   contrastadas contra 29 operaciones contractuales, sin diferencias.

## Precondiciones locales

- Java 21, Node 22 y PostgreSQL 18 con la base exclusiva
  `arquitectura_veterinaria`.
- `.env.local` presente, ignorado por Git y limitado a credenciales sintéticas.
- Flyway desactivado para la operación ordinaria; V011 ya está registrada y no
  requiere reejecución.

## Recorrido verificable

1. Iniciar backend con `AV_FLYWAY_ENABLED=false`.
2. Iniciar Angular local con el proxy configurado hacia backend.
3. Usar solo las credenciales sintéticas locales a través del flujo STAFF.
4. Verificar espacio de trabajo y perfil del tenant propio.
5. Cerrar sesión con el token CSRF devuelto al iniciar; comprobar denegación de
   `/workspace` después del logout.

La E2E equivalente está en
`frontend/e2e/g4-i01-authenticated.e2e.mjs`. Los secretos se leen solo desde el
entorno del proceso; no se incluyen en este documento, capturas ni commits.

## Bloqueos antes del 26 de octubre

- No existe despliegue, URL pública ni remoto autorizados.
- G4-I01 mantiene 16 RF B01 parciales o no verificados; no deben presentarse
  como funcionalidades completas.
- Agent Browser CLI 0.27.0 se bloquea al cierre y no hay evidencia de su
  auditoría de accesibilidad o regresión visual. Playwright es evidencia E2E,
  no cierre de a11y.
- Resolver el arranque local de Angular y repetir la E2E antes de basar una
  presentación en una regresión de navegador reciente.
- Antes de publicar: revisión independiente de secretos, dependencias/licencias,
  datos sintéticos, configuración de producción, adaptador de notificaciones y
  aprobación expresa de despliegue/repositorio público.
