# ADR-0001 — Monolito modular y ownership

**Estado:** aceptado para implementación posterior.
**Contexto V1:** los seis bloques comparten invariantes fuertes de tenant, agenda, inventario, comercio y auditoría.

## Decisión

Construir un único desplegable Spring Boot dividido en los módulos definidos en `docs/architecture/02_DDD_Y_MODULOS.md`. Cada módulo es dueño de sus agregados, tablas, casos de uso y pruebas. Las interacciones ocurren por contratos internos/eventos, no por acceso a repositorios ajenos.

## Alternativas descartadas

- Microservicios: aumentan coste operativo, despliegues, autenticación distribuida y riesgo para la ventana académica.
- Monolito sin módulos: facilita dependencias implícitas y mezcla de reglas sensibles.

## Consecuencias y reversión

Los módulos se prueban dentro de un proceso. La extracción futura exige un ADR, contrato estable y evidencia de un cuello de botella real; no se extrae por anticipación.
