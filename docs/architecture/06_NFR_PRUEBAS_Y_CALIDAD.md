# G2 — NFR, pruebas y puertas de calidad

## Objetivos verificables, no resultados

| Categoría | Objetivo de aceptación para V1 | Cómo se comprobará |
|---|---|---|
| Seguridad | 100% de rutas privadas con prueba negativa de tenant/rol/propiedad pertinente | integración Spring + PostgreSQL |
| Correctitud | 100% de RF implementados enlazados a al menos una prueba y evidencia G4 | matriz de trazabilidad |
| API | p95 de endpoint CRUD normal menor de 800 ms con dataset sintético definido; críticos medidos por separado | prueba de carga acotada y reporte |
| Integridad | 0 saldos negativos, 0 doble movimiento y 0 doble reserva en escenarios concurrentes establecidos | pruebas concurrentes reales |
| Accesibilidad | WCAG 2.2 AA en flujos de demo; sin violaciones críticas conocidas | revisión manual + `agent-browser a11y` |
| Disponibilidad demo | health check y restauración documentada antes de URL pública | evidencia de despliegue autorizada |
| Observabilidad | cada petición tiene `traceId`; acciones sensibles generan auditoría sin secretos | integración y revisión de logs |

Los umbrales solo se declaran alcanzados tras ejecutar comandos reproducibles y conservar evidencia sintética. No se extrapolan desde pruebas unitarias.

## Pirámide y ownership de pruebas

1. **Unidad:** agregados, políticas, validadores, mapeadores y casos de uso. Ejecutan sin red ni base de datos.
2. **Integración:** repositorios, RLS, Flyway, transacciones, bloqueos, serialización y adaptadores contra PostgreSQL 18 aislado.
3. **Contrato:** OpenAPI, errores, compatibilidad Angular/Spring y `Idempotency-Key`.
4. **E2E:** navegador Chromium mediante Agent Browser para los flujos autorizados, datos sintéticos y evidencia capturada.
5. **Seguridad y regresión:** casos negativos, secretos, dependencias, accesibilidad, visual y carga proporcional al riesgo.

Cada tarea sigue RED → GREEN → REFACTOR cuando comporte lógica nueva. La evidencia guarda comando, fecha, entorno, salida resumida, trazas no sensibles y RF/CA V1 cubiertos. Un fallo bloquea el incremento correspondiente; no se sustituye con una captura manual.

## Puertas por incremento

| Puerta | Condición |
|---|---|
| Inicio | fuente V1, ADR y contrato aplicable leídos; tarea con archivos permitidos y rollback |
| Antes de merge local | formato/lint disponible, pruebas unitarias e integración pertinentes, revisión de diff y secretos |
| Integración vertical | contrato actualizado, datos sintéticos, autorización, tratamiento de errores y UI conectada a API |
| Demo pública | E2E Chromium, a11y, smoke de salud, escaneo de secretos/licencias, backup/restauración ensayada y autorización de publicación |

## Limitaciones presentes

No existe código, configuración de test, navegador comprobado para Agent Browser, Docker ni CI. Por ello no hay pruebas ejecutadas en G2/G3 y ninguna lista de verificación se marca como aprobada por anticipado.
