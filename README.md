# Arquitectura - Veterinaria

SaaS académico para veterinarias independientes del Perú, definido por la especificación funcional V1 cerrada. Este repositorio conserva arquitectura, ADR y planificación antes de la primera implementación; no contiene una aplicación ejecutable todavía.

## Estado verificable

- Línea funcional: `arquitectura_veterinaria_v1_FINAL/`, 118 RF, 68 casos de uso y 131 criterios diseñados.
- Arquitectura: `docs/architecture/` y `docs/adr/`.
- Plan G3 y trazabilidad: `docs/implementation-plan/`.
- Ecosistema: `docs/audits/P1B_ECOSISTEMA_Y_HERRAMIENTAS.md`.
- Entorno: Node/Python/PostgreSQL presentes; JDK 21, Maven, Angular CLI y Docker aún no disponibles. No intente ejecutar comandos de aplicación desde esta línea base.

## No hacer todavía

No crear scaffolding, ejecutar migraciones, usar datos reales, activar Impeccable, publicar una URL, conectar GitHub ni hacer push sin la autorización y validación de la puerta correspondiente.

## Próximo paso controlado

Tras aprobar el commit documental, autorizar el primer incremento G4-I01 descrito en `docs/implementation-plan/02_INCREMENTOS_Y_RUTA_CRITICA.md`; primero debe resolverse el preflight de herramientas sin alterar el alcance funcional.
