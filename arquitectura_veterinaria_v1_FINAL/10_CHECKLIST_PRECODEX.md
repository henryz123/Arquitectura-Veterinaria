# Arquitectura - Veterinaria | Puertas de arquitectura y preparación del plan Codex

**Estado:** propuesta de hitos. No es todavía la orden de ejecución ni el plan de agentes definitivo.

## Gate A — Análisis funcional V1 CERRADO documentalmente (B01–B06)

- [ ] Aprobar B02–B06 y propuestas V1-AP-01..04.
- [ ] Convertir PROP-V1 en DEC/RN controladas y conservar identificadores históricos.
- [ ] Aprobar matriz de roles y autorización por objeto/tenant.
- [ ] Revisar estados/guardas/transiciones y aceptación AT-B02..B06.
- [ ] Resolver diferencias con CU-001/RF-001 Bloque 1, VIS-001, ACT-001 mediante registro de cambios.

## Gate B — Análisis no funcional y legal

- [ ] Determinar requisitos cuantitativos: latencia de consultas, capacidad demo, sesiones simultáneas, tasas de disponibilidad razonables, tamaño de datos, RPO/RTO, retención/backup.
- [ ] Definir amenazas y mitigaciones: IDOR multi-tenant, BOLA, seguridad clínica, toma de control del tenant, secretos, recuperación, abuso, archivos y notificaciones.
- [ ] Diseñar privacidad de datos y manejo de derechos de titulares antes de datos reales.
- [ ] Resolver uso exclusivamente académico o futura operación real; no afirmar cumplimiento SUNAT por emitir constancias internas.
- [ ] Definir accesibilidad/responsividad, evidencias de UX y flujos de error.

## Gate C — Modelo y arquitectura

- [ ] DDD estratégico: bounded contexts, aggregate boundaries, lenguaje ubicuo y ownership de datos.
- [ ] Diseñar modelo entidad-relación y restricciones de tenant, owner vigente, agenda y stock.
- [ ] Seleccionar stack/backend/frontend/BD por criterios explícitos y ADR.
- [ ] Diseñar monolito modular, arquitectura hexagonal selectiva, interfaces y contracts.
- [ ] Diagramas C4 (contexto, contenedores, componentes) + arc42 + ADR.
- [ ] Diseñar API/validación de entrada, errores, idempotencia, authz, sesiones y auditoría.
- [ ] Estrategia para transacciones, notificaciones confiables y restauración de backups.

## Gate D — Infraestructura y demo

- [ ] Inspeccionar repositorio y Git existentes; no suponer su estado.
- [ ] Docker/Compose, variables/secretos seguros, base local, seeds sintéticos reproducibles.
- [ ] Dos o más veterinarias ficticias aisladas, clientes, mascotas y operaciones sintéticas coherentes.
- [ ] Selección de hosting de prueba; revisar recursos, límites y condiciones vigentes.
- [ ] Estrategia de migraciones, backups, logs, recuperación y rollback.
- [ ] Manual de instalación/uso y escenario de sustentación.

## Gate E — Plan maestro de Codex y orquestación

- [ ] Releer instrucciones maestras y protocolo integral v1.1 del usuario.
- [ ] Confirmar herramientas, skills, conectores y agentes **realmente instalados** antes de asignar tareas.
- [ ] Definir `AGENTS.md` o equivalente solo tras inspección del repositorio; fuente única de verdad para instrucciones.
- [ ] Descomponer entregables verticalmente: fundamentos identidades → propietarios/clínica → agenda → inventario/POS → ecommerce → transversal/UX → auditoría/demo.
- [ ] Para cada tarea: contexto/alcance/exclusiones, archivos afectados, precondiciones, contrato, casos de prueba, evidencia, criterio DoD, dependencias y estrategia de reversión.
- [ ] Decidir agentes necesarios según riesgo (arquitecto/dominio, seguridad, datos, backend, frontend, QA, auditor), **sin asumir paralelismo sobre mismos archivos**.
- [ ] Ordenar pruebas unitarias, integración, E2E, seguridad multi-tenant, concurrencia, restauración y regresión.
- [ ] Plan de integración por PR/diff: nadie declara terminado sin inspección de cambios y ejecución de pruebas aplicables.
- [ ] Sistema de reporte: completado/verificado/pendiente/bloqueado/riesgo/siguiente paso, hallazgos con evidencia.
- [ ] Aprobar explícitamente el arranque de desarrollo; antes de ello no emitir tareas de implementación.

## Evidencia que Codex deberá producir cuando esté autorizado

Repositorio inspeccionado y documentado; commits/diffs; tests y logs reales; diagrama y ADR actualizados; migraciones ejecutadas en entorno aislado; capturas reales de la demo; pruebas de aislamiento con ≥2 tenants; pruebas de carreras de stock/agenda; instrucciones reproducibles; fallos abiertos y decisiones que requieran intervención humana.

**No disponible todavía:** stack elegido, repositorio inspeccionado, prueba ejecutada, servidor desplegado y autorización para programar.

## Nota de actualización 21-10-2026

La puerta de **cierre funcional** se considera satisfecha por el paquete de seis bloques, las decisiones DEC-064..067 y los controles RO-01..16. Las puertas de arquitectura, privacidad/operación real, repositorio, agentes y pruebas posteriores siguen pendientes. **No iniciar implementaciones ni invocar Codex por la sola existencia de este documento.**
