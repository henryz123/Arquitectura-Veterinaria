# G4-I00 — Cierre técnico verificable

**Fecha:** 2026-10-21
**Estado:** vertical técnico de sesión laboral y aislamiento tenant verificado localmente en una base PostgreSQL exclusiva. No declara B01 completa ni habilita I01–I06.

## Alcance comprobado

- Dos tenants sintéticos: `veterinaria-aurora` y `veterinaria-bosque`, con la misma dirección de correo sintética y cuentas separadas.
- Inicio de sesión laboral opaco, contexto de sesión, perfil del tenant propio, denegación del slug ajeno, cierre de sesión y CSRF.
- Aislamiento en PostgreSQL con `av_app`, privilegios mínimos y RLS en `tenancy.tenant`, `identity_auth.staff_account` e `identity_auth.user_session`.

No se implementaron módulos I01–I06, reglas clínicas, POS, agenda, portal cliente, despliegue ni integración externa.

## Hechos de entorno y seguridad

- La rama local es `codex/g4-i00`; no hay remoto configurado ni se realizó `push`.
- JDK Microsoft 21.0.12.1 se usó por proceso desde `C:\Users\HNR05\.jdks\ms-21.0.12.1`; la resolución normal de PowerShell puede encontrar antes una JRE 8, por lo que `JAVA_HOME` debe fijarse para Maven y el backend.
- Node es 22.20.0, Angular 21 usa pnpm 11.25.0 y PostgreSQL es 18.6 mediante `C:\Program Files\PostgreSQL\18\bin\psql.exe`.
- La base exclusiva `arquitectura_veterinaria` pertenece a `av_schema_owner`; `av_app` no es superusuario, no crea roles/bases ni tiene `BYPASSRLS`.
- `.env.local` permanece ignorado. Las contraseñas sintéticas, cookies, tokens opacos y valores CSRF no se imprimieron ni se versionaron.
- `.codex/hooks.json` sigue ausente; el respaldo aislado de Impeccable mantiene SHA-256 `57EC2302C0EACFD0DE2E7940A9584E7F8221E9B555C5A23FD06DD5A6B156188F`. No se ejecutó Impeccable ni se descargó su motor.

## Correcciones realizadas durante la integración

1. Se evitó el usuario en memoria y la contraseña de desarrollo generada automáticamente por Spring Boot declarando un `UserDetailsService` que rechaza autenticación interactiva. El backend reiniciado no registró ese aviso.
2. La primera autenticación real devolvió HTTP 500. La prueba de integración reprodujo la causa: el driver PostgreSQL no puede inferir el tipo JDBC de `Instant` para `expires_at`. `JdbcIdentityRepository` ahora enlaza `Timestamp.from(expiresAt)`.
3. Se añadió `StaffAuthenticationDatabaseIntegrationTest`: consume la contraseña sintética únicamente desde el entorno local, crea la sesión dentro de la transacción de prueba y revierte al finalizar.

## Evidencia ejecutada

| Control | Resultado real |
|---|---|
| Flyway contra la base exclusiva | PostgreSQL 18.6; V001 validada, esquema `public` en versión `001` y `MIGRATION_COUNT=1`. No se repitió V001. |
| Privilegios de `av_app` | La función `lookup_staff_identity` devolvió una identidad sintética; una inserción de sesión bajo contexto tenant produjo `BEGIN`, `INSERT 0 1`, `ROLLBACK`. |
| RLS | RLS activa en las tres tablas protegidas. Sin contexto, `av_app` no ve tenants ni sesiones; con cada contexto local solo ve su tenant. La lectura directa de `staff_account` fue denegada a `av_app`. |
| API Aurora | Login 201, contexto 200, perfil propio 200, perfil Bosque 404, logout sin CSRF 403, logout con CSRF 204 y contexto posterior 401. |
| API Bosque | Login 201, perfil propio 200, perfil Aurora 404 y logout con CSRF 204. |
| Credencial incorrecta | HTTP 401. |
| Estado posterior de sesiones | Una sesión revocada por tenant; cero sesiones activas tras los cierres. |
| Backend | `backend\mvnw.cmd test` con JDK 21: 5 pruebas, 0 fallos, 0 errores. |
| Frontend | Build Angular 21 correcto; Vitest: 1 archivo y 1 prueba superados. |
| Agent Browser 0.27.0 | Login Aurora y Bosque renderizados localmente; árbol de accesibilidad con `main`, encabezado H1, etiquetas de ambos campos, botón y navegación. La validación vacía mostró los dos mensajes esperados. Se capturaron evidencias no autenticadas a 1440, 768 y 375 px dentro de `frontend/.angular/` ignorado. |

## Límites y resultados no declarados

- Agent Browser no incluye el comando `a11y` en la versión instalada (`Unknown command: a11y`). El árbol de accesibilidad es una comprobación estructural, no una auditoría WCAG completa. No existe aún una línea base visual, así que las capturas no constituyen una regresión visual aprobada.
- El login autenticado del navegador no se automatizó: el CLI exigiría pasar o persistir una contraseña. Se mantuvo la política de secretos y se verificó el mismo flujo de autenticación contra la API real. La validación manual de UI autenticada queda pendiente si se requiere evidencia adicional.
- El binario local de Redocly no está presente, por lo que la validación OpenAPI actual no es ejecutable. La validación histórica exitosa se mantiene como antecedente; el contrato no fue modificado durante G4-I00.
- Maven emitió avisos 401 al consultar metadatos opcionales de repositorios Flyway/Redgate y el conjunto de pruebas avisó sobre la futura restricción de carga dinámica de Mockito. Ninguno impidió las compilaciones ni pruebas; deben resolverse antes de una automatización CI limpia.
- Docker/Compose no está instalado. La ruta local aprobada fue suficiente para G4-I00; no se instaló ni se modificó Windows.

## Integridad y continuidad

La especificación funcional V1 continúa inmutable. Antes de cualquier commit se recalcularán sus manifiestos y los de los planes históricos/vigentes, se revisará el diff y se ejecutará el análisis de secretos. El siguiente incremento requiere autorización expresa y no puede interpretarse como inicio de I01.
