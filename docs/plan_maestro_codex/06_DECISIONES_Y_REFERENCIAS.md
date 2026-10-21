# Decisiones, pendientes y referencias — V0.3

## Decisiones confirmadas

- V1 funcional es inmutable; sus 6 bloques, 68 casos de uso, 118 requisitos funcionales y 131 criterios de aceptación no se reinterpretan en este plan.
- Java 21, Spring Boot 4.1.1, Maven/Maven Wrapper, Angular 21 con TypeScript y PostgreSQL 18 son las bases técnicas aprobadas.
- El diseño estructural será monolito modular, DDD estratégico y arquitectura hexagonal selectiva.
- Solo existe un producto definitivo. La demostración y la entrega final usan datos sintéticos y pruebas reproducibles.
- El presupuesto inicial es S/ 0. No se aprueba gasto, proveedor de hosting, dominio o servicio externo por inferencia.
- El 26/10/2026 es presentación de avance; el 18/11/2026 es entrega académica final.

## Pendientes que sí requieren G2 o autorización posterior

- ADR de módulos, tenancy, autenticación/autorización, persistencia, API, eventos y despliegue.
- NFR cuantitativos, amenaza/privacidad, retención, backup/restauración, disponibilidad y observabilidad.
- Modelo físico PostgreSQL, contratos Angular/Spring Boot, UX definitivo y `DESIGN.md`.
- Proveedor o mecanismo de URL pública S/ 0, dominio, CI y publicación GitHub pública.
- P1B para el motor de Impeccable; Taste, Emil adicionales e img2threejs solo si un caso concreto los justifica.

## Referencias de procedencia

- Funcional: `arquitectura_veterinaria_v1_FINAL/`.
- Operación: `AGENTS.md`, `PRODUCT.md`, `instrucciones_maestras_chatgpt_v1_1.md`, `manual_protocolo_integral_chatgpt_v1_1.md`.
- Historial: `docs/historial/plan_maestro_codex_v0_2/`.
- Herramientas que deben revisarse antes de instalar o ejecutar: ECC (`affaan-m/ECC`), Impeccable, Agent Browser, UI UX Pro Max, Design Motion Principles, Taste, Emil Skills e img2threejs.

La presencia de una URL upstream no autoriza descargar, ejecutar ni incorporar su contenido. El primer staging excluye `.agents/` hasta completar revisión de procedencia y licencia.
