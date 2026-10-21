# G2 — Operación, despliegue S/ 0 y recuperación

## Estado actual

Docker/Compose no están disponibles localmente, no existe repositorio remoto y no hay aplicación que desplegar. Esta guía es diseño operativo; no crea imagen, cuenta, URL, servicio ni coste.

## Topología de demostración propuesta

Un único servicio web ejecuta el monolito Spring Boot y sirve el bundle Angular en el mismo origen. PostgreSQL se mantiene como servicio separado y persistente. La topología reduce CORS/cookies cruzadas y no separa el producto en una aplicación demo distinta.

Render ofrece servicios web y PostgreSQL gratuitos con URL pública, pero su web gratuita se suspende tras inactividad y PostgreSQL gratuito expira a los 30 días, no dispone de backups y puede reiniciarse. Es apto solo como posible avance académico temporal, no como producción. Railway ofrece crédito/trial temporal, por tanto no es una garantía S/ 0. La selección y creación de cualquier cuenta queda fuera de esta actividad y exige autorización separada.

## Docker/Compose futuro — diseño, no archivos

| Servicio | Responsabilidad | Health check | Datos persistentes |
|---|---|---|---|
| `app` | Spring Boot + estáticos Angular | `/actuator/health` restringido a detalle seguro | ninguno local |
| `postgres` | PostgreSQL 18 de desarrollo/test | `pg_isready` | volumen de desarrollo |

Secuencia futura: verificar JDK/Node/Docker, construir artefactos con lockfiles, levantar PostgreSQL, ejecutar Flyway una sola vez con rol técnico, cargar fixture sintético explícito, comprobar health/API/UI, recoger logs sin secretos y detener con el procedimiento que conserva el volumen. Ninguna migración se ejecuta al importar un entorno productivo sin backup y revisión.

## Backup y restauración

- Desarrollo: antes de una migración no trivial, backup lógico con `pg_dump` desde credencial interactiva no registrada.
- Demo: exportación cifrada/ubicación local controlada de datos exclusivamente sintéticos; probar restauración en instancia aislada antes de publicar cambios relevantes.
- Recuperación: detener escrituras, preservar auditoría/logs, restaurar backup verificado a base nueva, validar versión Flyway y health, después cambiar tráfico con autorización.
- No se usa la inexistencia de backup gratuito como justificación para datos reales. Para el caso académico, el dataset es sintético y reconstruible desde fixtures aprobados.

## Checklist previo a cualquier URL pública

1. Permiso expreso para conectar GitHub y proveedor.
2. Revisión de secretos, dependencias/licencias y datos sintéticos.
3. Evidencia real de tests, E2E, accesibilidad, health y recuperación.
4. Configuración de dominio/origen, TLS, cookies, CORS, base y variables por mecanismo seguro.
5. Registro de límites del plan gratuito, caducidad de datos y procedimiento de desactivación sin cargos.

La demo objetivo del 26/10/2026 se declara como avance funcional solo si estas condiciones se realizan y registran; este documento no las acredita.
