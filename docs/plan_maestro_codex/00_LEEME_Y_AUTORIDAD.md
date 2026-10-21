# Plan Maestro Codex V0.4 — Arquitectura - Veterinaria

**Estado:** plan operativo vigente tras P1B/G2/G3 documental; no es código ni autorización de implementar G4.

## Autoridad y versión vigente

- La fuente funcional inmutable es `arquitectura_veterinaria_v1_FINAL/`.
- Las instrucciones de operación son `instrucciones_maestras_chatgpt_v1_1.md`, `manual_protocolo_integral_chatgpt_v1_1.md` y la instrucción expresa más reciente del responsable.
- Este directorio es el único Plan Maestro operativo vigente. V0.2 se conserva íntegramente como historial en `docs/historial/plan_maestro_codex_v0_2/`; V0.3 queda preservado en el commit documental anterior. No se usan para reabrir decisiones aprobadas.
- `design-system/arquitectura-veterinaria/MASTER.md` es una dirección visual provisional, no un diseño UX ni una marca aprobados.

## Decisiones de planificación confirmadas

| Área | Decisión |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Maven y Maven Wrapper |
| Frontend | Angular 21 y TypeScript |
| Datos | PostgreSQL 18 |
| Arquitectura | Monolito modular, DDD estratégico y arquitectura hexagonal selectiva |
| Producto | Un único producto definitivo; no se crearán prototipos desechables |
| Calidad | Pruebas reproducibles y controles de seguridad antes de publicar cambios |
| Presupuesto inicial | S/ 0 |
| Presentación de avance | 26 de octubre de 2026: URL pública funcional y repositorio público, solo tras revisión de secretos y autorización |
| Entrega académica final | 18 de noviembre de 2026 |

La presentación de octubre es un avance funcional. No exige completar los 118 requisitos funcionales de V1. La meta acelerada de V1 en 5–6 días es condicional a evidencia real de pruebas e integración; ningún flujo se declarará completo sin esa evidencia.

## Orden de lectura

1. `docs/architecture/README.md` y `docs/adr/README.md`
2. `docs/implementation-plan/README.md`
3. `01_ESTADO_G1_A_Y_RIESGOS.md`
4. `02_PLAN_MAESTRO_Y_GATES.md`
5. `04_HOJA_DE_RUTA_DEMO_Y_ENTREGA.md`
6. `03_ORQUESTACION_Y_SKILLS.md`

## Límites inmediatos

La línea base Git ya existe y P1B/G2/G3 están documentados. No se autoriza remoto GitHub, instalaciones, hooks de Impeccable, scaffolding, migraciones, despliegue ni código de aplicación. La implementación requiere una tarea G4 expresa y preflight técnico conforme.
