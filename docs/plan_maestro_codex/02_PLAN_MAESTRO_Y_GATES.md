# Plan Maestro y puertas de control — V0.3

## Misión

Construir un único SaaS académico para veterinarias independientes del Perú, conforme a la especificación funcional V1, con datos exclusivamente sintéticos, controles de tenant y una demostración verificable. La arquitectura aprobada de planificación es un monolito modular con DDD estratégico y arquitectura hexagonal selectiva; su detalle se define en G2 mediante ADR y artefactos revisados.

## Puertas

| Puerta | Alcance | Evidencia mínima | Estado |
|---|---|---|---|
| G0 | Auditoría P0 de solo lectura | inventario del entorno, Git y herramientas | completada previamente |
| G1-A | Documentación, seguridad y staging controlado | diff, integridad V1, revisión de secretos, hook aislado, índice revisado | en curso; sin commit |
| G1-B | Diagnóstico selectivo del motor Impeccable | procedencia, rollback, prueba no destructiva y aprobación específica | no iniciado |
| G2 | Arquitectura técnica definitiva sin código de aplicación | ADR, NFR, amenazas, modelo de datos, C4/arc42, contratos, UX, pruebas y operación | pendiente |
| G3 | Plan de implementación autorizado | backlog vertical, DoD, estrategia TDD/seguridad/E2E y permiso expreso para codificar | pendiente |
| G4 | Incrementos verificables | diffs, pruebas reproducibles, revisión de seguridad y evidencia real | pendiente |
| G5 | Presentación pública de avance | URL funcional, repositorio público revisado, datos sintéticos, flujos verticales reales y evidencia | objetivo 26/10/2026 |
| G6 | Entrega académica V1 | cobertura funcional realmente demostrada, documentación y controles de cierre | objetivo 18/11/2026 |

## G2: decisiones técnicas ya cerradas y decisiones aún pendientes

Las tecnologías base ya están aprobadas: Java 21, Spring Boot 4.1.1, Maven y Maven Wrapper, Angular 21 con TypeScript y PostgreSQL 18. G2 no volverá a comparar Next.js/NestJS ni a tratar el stack base como abierto.

G2 aún debe definir, con decisiones revisables y sin escribir la aplicación:

- límites de módulos, dependencias permitidas, lenguaje ubicuo, agregados e invariantes;
- estrategia de tenancy, autorización, autenticación, sesión y auditoría;
- modelo relacional físico, índices, transacciones, concurrencia e idempotencia;
- contratos API, manejo de errores y compatibilidad frontend/backend;
- NFR medibles, amenazas, privacidad, retención, backup/restauración y observabilidad;
- UX, accesibilidad, diseño visual final y `DESIGN.md`;
- procedimiento de ejecución local, CI, datos sintéticos, pruebas y publicación S/ 0.

## Reglas de implementación futura

1. No hay aplicación separada de demostración: los incrementos se incorporan al producto definitivo.
2. Cada incremento comienza con criterios funcionales rastreables a V1 y pruebas RED → GREEN → REFACTOR cuando corresponda.
3. No se publica una URL, rama o repositorio sin revisar secretos, dependencias, licencias, datos sintéticos y resultados de prueba reales.
4. No se paralelizan escrituras sobre los mismos contratos, migraciones, esquema o documentos de autoridad.
5. La arquitectura hexagonal se aplica donde protege límites de integración o volatilidad; no se convierte en complejidad ceremonial dentro de cada módulo.
