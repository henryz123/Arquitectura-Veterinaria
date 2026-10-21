# ADR-0006 — Eventos internos, auditoría y notificaciones

**Estado:** aceptado para implementación posterior.
**Contexto V1:** los módulos intercambian hechos; fallo de correo no revierte negocio y operaciones sensibles son auditables.

## Decisión

Usar eventos internos de contrato y una outbox transaccional para notificaciones. Una transacción persiste primero el hecho de negocio, su auditoría y el mensaje de salida; un adaptador posterior intenta entregar y registra resultado. Auditoría conserva actor, recurso, correlación, resultado y metadatos mínimos, nunca secretos.

## Consecuencias

No se introduce un broker externo en V1. La entrega puede ser diferida y debe mostrarse como tal, no fingir correo enviado. La outbox y auditoría requieren pruebas de fallo posterior a commit.
