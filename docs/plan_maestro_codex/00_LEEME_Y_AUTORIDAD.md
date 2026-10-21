# Plan Maestro Codex V0.3 — Arquitectura - Veterinaria

**Estado:** plan de trabajo vigente para cerrar G1 y preparar G2; no es código, arquitectura definitiva ni autorización de implementación.

## Autoridad y versión vigente

- La fuente funcional inmutable es `arquitectura_veterinaria_v1_FINAL/`.
- Las instrucciones de operación son `instrucciones_maestras_chatgpt_v1_1.md`, `manual_protocolo_integral_chatgpt_v1_1.md` y la instrucción expresa más reciente del responsable.
- Este directorio es el único Plan Maestro operativo vigente. V0.2 se conserva íntegramente como historial en `docs/historial/plan_maestro_codex_v0_2/`; no debe usarse para elegir stack ni reabrir decisiones ya aprobadas.
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

1. `01_ESTADO_G1_A_Y_RIESGOS.md`
2. `02_PLAN_MAESTRO_Y_GATES.md`
3. `04_HOJA_DE_RUTA_DEMO_Y_ENTREGA.md`
4. `03_ORQUESTACION_Y_SKILLS.md`
5. `05_CHECKLIST_CIERRE_G1.md`
6. `06_DECISIONES_Y_REFERENCIAS.md`

## Límites inmediatos

G1-A autoriza únicamente documentación, revisión de seguridad y staging explícito. No autoriza commit, remoto GitHub, instalaciones, hooks de Impeccable, scaffolding, migraciones, despliegue ni código de aplicación. La implementación requiere G3 y autorización expresa posterior.
