# G4-I00 — Precheck técnico y contractual

**Fecha:** 2026-10-21
**Estado:** preparación y compilaciones locales verificadas; integración PostgreSQL y E2E pendientes por acceso administrativo local no expuesto.

## Hechos verificados

- `HEAD` correspondía a `254b18c8a2a5ceaa903700fc38ce9df2f84fd70e` y el árbol estaba limpio al iniciar G4-I00. El trabajo se aisló después en la rama local `codex/g4-i00`; no hay remoto configurado.
- Los 19 archivos físicos de la especificación funcional V1 se conservaron intactos; el manifiesto cubre 18 archivos de contenido. Se recalculará antes del commit local.
- PowerShell encuentra primero JRE `1.8.0_51`, pero el JDK Microsoft `21.0.12.1` existe en `C:\Users\HNR05\.jdks\ms-21.0.12.1`. Java, `javac` y Maven Wrapper 3.10.0 se verificaron con ese JDK por proceso.
- Node es `22.20.0`; PostgreSQL es 18.6 por ruta absoluta y el servicio `postgresql-x64-18` está activo. La conexión administrativa sin contraseña no se intentó de nuevo y no se solicitaron secretos.
- Angular 21 compila y su prueba de servicio pasa con pnpm 11.25.0. npm 10.9.3 falló internamente al resolver pares de dependencias y su caché global devolvió `EPERM`; no se actualizó npm global ni se borró la caché.
- Docker/Compose no está instalado. `agent-browser.cmd` respondió `0.27.0`; su navegador todavía no se verificó porque no existe aún integración ejecutable.
- Redocly CLI 2.67.1 validó formalmente `docs/contracts/openapi-v1.yaml` con resultado `Woohoo! Your API description is valid.` antes de materializar proveedor/consumidor. El intento posterior offline no pudo usar la caché global de npm; el contrato no se modificó después de la validación exitosa.
- `.codex/hooks.json` sigue ausente y el respaldo de Impeccable permanece aislado; no se invocó su launcher ni se descargó su motor.

## Correcciones documentales y técnicas aplicadas

1. G4-I00 registra un vertical limitado de identidad/tenant como parte de I00, sin declarar I01 ni B01 completos.
2. El contrato de login requiere `tenantSlug` de ruta como selector no confiable. La sesión validada, no la ruta, determina tenant y roles.
3. Se añadieron contexto mínimo de sesión y perfil propio para demostrar recurso propio y rechazo de slug ajeno sin adelantar B02.
4. `database/migration/` es la única fuente Flyway versionada; Maven copia su contenido a la salida generada de compilación.
5. La función de lookup de identidad usa `SECURITY DEFINER` con ruta de búsqueda fijada y el rol de runtime queda separado del propietario del esquema. RLS se habilita sin `FORCE` para permitir esa búsqueda previa a una sesión; `av_app` no debe ser propietario ni tener `BYPASSRLS`.
6. Los scripts nativos transitorios de pnpm permanecen explícitamente bloqueados. Angular usó binarios precompilados oficiales de Windows x64 y compiló sin aprobar esos scripts.

## Evidencia de pruebas locales

| Componente | Comando | Resultado |
|---|---|---|
| Backend | `backend\mvnw.cmd test` con JDK 21 | BUILD SUCCESS; 4 pruebas, 0 fallos, 0 errores. |
| Frontend | `frontend\node_modules\.bin\ng.cmd build` | Compilación Angular 21 satisfactoria. |
| Frontend | `frontend\node_modules\.bin\ng.cmd test --watch=false` | Vitest: 1 archivo y 1 prueba superados. |
| OpenAPI | Redocly 2.67.1 antes del proveedor | Sintaxis válida; no hubo cambios posteriores al contrato. |
| Verificación integrada | `scripts\verify-g4-i00.cmd` con JDK 21 en `JAVA_HOME` | Exit code 0; repitió backend 4/4, build Angular y frontend 1/1. |

Las pruebas unitarias ejecutadas cubren normalización de correo, hash opaco de tokens, límite local de intentos, cookie con credenciales y conservación del CSRF solo en memoria. No constituyen evidencia funcional de requisitos V1.

## Cobertura candidata, no declarada como cierre

Solo tras integración real se evaluarán `RF-AUT-01`, `RF-AUT-04`, `RF-AUT-05`, `RF-AUT-10`, `RF-AUT-13` y la porción aplicable de `RF-B06-001/002`. No se declara B01 completa ni se atribuyen otros requisitos sin evidencia.

## Bloqueos o decisiones no resueltas

- La creación de base y roles exclusivos requiere acceso administrativo local a PostgreSQL que no se expondrá ni versionará. Mientras no exista, migraciones ejecutadas, pruebas de integración, autenticación real y rechazo cross-tenant real se declaran bloqueados, no superados.
- Docker no está instalado. G4-I00 permite continuar por la ruta local sin modificar WSL, virtualización ni políticas de seguridad de Windows.
- Agent Browser, E2E, health check integrado y pruebas de ruta protegida quedan bloqueados hasta disponer de backend conectado a la base exclusiva y datos sintéticos.
- El binario local de Redocly quedó incompleto por descarga lenta del registro, aunque la validación formal anterior fue exitosa y el contrato no cambió. La dependencia se fija en `frontend/package.json` para una instalación limpia posterior.
- La política de ejecución actual bloqueó `scripts/verify-g4-i00.ps1`. No se alteró ni se eludió; `scripts/verify-g4-i00.cmd` ofrece la misma verificación sin requerir cambios de seguridad.
