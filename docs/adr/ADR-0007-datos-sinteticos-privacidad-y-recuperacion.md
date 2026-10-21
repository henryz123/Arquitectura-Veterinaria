# ADR-0007 — Datos sintéticos, privacidad y recuperación

**Estado:** aceptado para implementación posterior.
**Contexto V1:** demostración académica, sin datos clínicos reales ni cumplimiento SUNAT declarado.

## Decisión

Versionar solo fixtures sintéticos identificables como tales. Secretos y datos reales quedan fuera de código, logs, capturas y repositorio. Antes de migraciones significativas se define backup lógico y se ensaya restauración aislada. La retención legal, consentimiento y manejo de datos reales no forman parte de V1 académica.

## Consecuencias

Una demo puede reconstruirse sin afectar personas reales. La inexistencia de backups en un plan gratuito impide su uso con datos reales y debe advertirse explícitamente.
