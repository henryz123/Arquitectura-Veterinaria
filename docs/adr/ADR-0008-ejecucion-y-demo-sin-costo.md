# ADR-0008 — Ejecución reproducible y demo S/ 0

**Estado:** aceptado como arquitectura; no autoriza instalación ni despliegue.
**Contexto:** demo funcional objetivo 26/10/2026 y entrega 18/11/2026, sin prototipo separado.

## Decisión

La reproducción local usará Maven Wrapper, lockfile npm, PostgreSQL 18, datos sintéticos, migraciones Flyway y pruebas documentadas. El objetivo de demo es un monolito que sirve Angular en mismo origen. Render Free queda documentado solo como opción temporal condicionada: web gratuita se suspende por inactividad y PostgreSQL gratuito caduca a 30 días sin backups.

## Consecuencias

Docker, GitHub, CI, proveedor, variables de entorno, URL pública y cualquier cuenta requieren aprobación explícita. Railway se descarta como garantía S/ 0 por depender de créditos/trial. Una publicación no puede ocultar la caducidad, los límites de cuota ni el estado de pruebas.
