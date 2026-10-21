# Plan maestro de arquitectura, orquestación e implementación V1 — V0.2

## Misión
Producir una V1 académica demostrable en español para múltiples veterinarias peruanas independientes, una sede por veterinaria, con roles acumulables, clínica editable solo por veterinario, agendas sin solapes, inventario transaccional y comercio sin cobro online/SUNAT, con datos exclusivamente sintéticos.

## Líneas base
- Funcional V1: `arquitectura_veterinaria_v1_FINAL/` INMUTABLE; 6 bloques, 68 CU, 118 RF, 131 CA; su integridad se verificó por Codex.
- Método: `instrucciones_maestras_chatgpt_v1_1.md`, `manual_protocolo_integral_chatgpt_v1_1.md`.
- Repositorio: estado descrito por auditoría P0; verificar otra vez antes de escribir.
- Dirección visual: `design-system/arquitectura-veterinaria/MASTER.md` es provisional, no es aprobación de marca.

## Puertas estrictas
| Puerta | Alcance | Entrega para revisión | Quién autoriza |
|---|---|---|---|
| G0 completada | auditoría solo lectura | P0, inventario/hallazgos | responsable; informe aportado |
| G1 | remediación segura, baseline Git, AGENTS/PRODUCT, hooks/skills | diff, archivos, logs, smoke herramientas, commit hash cuando autorizado | responsable |
| G2 | requisitos de calidad, stack, DDD/C4/arc42/ADR, datos, permisos, UX, despliegue, pruebas | ADR firmadas, diagramas, NFR medibles, modelo físico, contratos, checklist seguridad | responsable |
| G3 | plan de ejecución Codex | backlog por dependencias, prompts por tareas, roles skills, DoD, estrategia Git/CI, plan de demo | responsable explícitamente autoriza CODIFICAR |
| G4 | incrementos ejecutados | commits/diffs, test rojo-verde, revisión independiente, defectos resueltos | responsable por incremento |
| G5 | demo académica | E2E B01–B06, 2+ tenants, datos sintéticos, docs de ejecución, evidencias reales | responsable |

## P1A — Documentación y baseline segura (sin instalaciones)
- Congelar la especificación funcional, comparar estructura, inspeccionar hooks y `.gitignore` antes de tocar.
- Preparar en rama actual con archivos explícitos: `AGENTS.md`, `PRODUCT.md`, docs obsoletos actualizados, política de `.gitignore` para secretos y evidencias; hooks no confiables aislados SOLO con respaldo y consentimiento.
- Comprobar secretos y diff, identificar cambios fuera de alcance, reportar **antes de hacer commit**.
- Commit inicial de línea base en acción autorizada posterior; no forzar rama main ni reescribir master solo por estilo.

## P1B — Diagnóstico y reparación de Impeccable
- No instalar ECC duplicado.
- Verificar motor de Impeccable 0.1.16 de fuente original y método compatible Windows; descargar/instalar únicamente tras revisión de procedencia y autorización.
- Verificar el motor mediante versión/help y una invocación de prueba no destructiva. Habilitar hooks solo tras confianza explícita; comprobar eventos Stop y PostToolUse sin ediciones riesgosas.
- Registrar si timeout 5 s es suficiente; si no, proponer y ensayar cambio, no forzarlo.
- Si red falla: conservar hooks deshabilitados e informar; no desactivar TLS ni usar fuentes aleatorias.
- Skills Taste/Emil pueden instalarse después para diseño; img2threejs no bloquea V1.

## P2 — Arquitectura técnica (sin código de aplicación)
### Stack: candidato, no aprobado
- Next.js + TypeScript para web cliente/personal; NestJS + TypeScript para API; PostgreSQL; Docker Compose para entorno demo; Playwright para E2E.
- **Comparar en ADR** con alternativa más simple Next.js full-stack modular + PostgreSQL; evaluar hosting, esfuerzo individual, observabilidad, testing, contratos y aislamiento.
- No seleccionar ORM, librería auth o proveedor cloud solo por preferencia.

### DDD estratégico y límites de contexto
`SaaSRegistration`, `IdentityAccess`, `OwnerPet`, `Clinical`, `Scheduling`, `Grooming`, `CatalogStock`, `POS`, `OrdersDelivery`, `BillingInternal`, `NotificationsAudit`.
Definir ownership de datos/entidades, contratos de módulos, invariantes, eventos de negocio, dependencias y límites de transacciones. Monolito modular primero; arquitectura hexagonal selectiva para integraciones (correo, almacenamiento, pagos futuros) que lo requieran.

### Riesgos técnicos a resolver con pruebas de diseño
- **Multi-tenant:** evaluar tenancy por `tenant_id` + restricciones/queries y políticas, vs aislamiento físico. Auditoría de IDOR/tenant escape y pruebas negativas para cada recurso.
- **Inventario:** transacciones con control concurrente, movimientos auditables, idempotencia de confirmación/restauración, sin negativos.
- **Agenda:** control concurrente de profesional único entre clínica y grooming, intervalos y huso horario Lima.
- **Clínica:** integridad de historias, adendas trazables, visibilidad de datos protegidos y propiedad transferida.
- **Identidad:** sesiones revocables, email/cuentas compartidas, inicio seguro, recuperación excepcional y RUC disputado.
- **Notificaciones:** fallos/reintentos sin deshacer operación confirmada; evaluar outbox local si resulta necesario.

### Entregables mínimos G2
`NFR-001`, `THREAT-001`, `DDD-001`, `C4-L1..L3`, `ARC42-001`, `DATA-001` modelo relacional físico (PK/FK/índices/únicos/concurrencia/tenancy), `ADR-001..` selección de stack/tenancy/auth/ORM/eventos, `API-001`, `UX-001` mapas de pantalla y `DESIGN.md`, `OPS-001` local/demo, `TEST-001` plan de calidad, matriz RF↔test actualizada.
Medición de rendimiento solo con hardware/dataset/hosting definidos; WCAG 2.2 AA objetivo recomendado a validar; RPO/RTO reales de entorno académico, no SLA ficticio.

## P3 — Preparación del backlog listo para Codex (sin implementar)
1. **I00** repositorio, CI mínima, formateadores, pruebas base y Docker, según stack aprobado.
2. **I01** tenancy, SaaS onboarding, login, permisos, seed de demo sintético.
3. **I02** propietarios/mascotas + vínculo y transferencias.
4. **I03** consulta clínica, vacunas, recetas, auditoría.
5. **I04** agenda conjunta y grooming con flujo completo.
6. **I05** catálogo, inventario y POS, pruebas de concurrencia.
7. **I06** catálogo público, carrito/pedidos, delivery, devoluciones parciales.
8. **I07** notificaciones, auditorías y reportes, pruebas E2E, hardening, demo, entrega académica.
Las dependencias I00→I01 son estrictas; particionar cada incremento en contratos de agente y gates menores. No paralelizar escrituras sobre migraciones/esquemas compartidos. Revisar riesgos críticos antes de abrir flujo vulnerable.

## Definición de terminado de cada tarea
- Alcance/EXCLUSIONES/RF/CA referenciados.
- Inspección inicial y diff final.
- Pruebas que reproduzcan criterio, test automático pertinente y resultado genuino.
- Revisión de seguridad/tenancy/concurrencia cuando aplique; defectos críticos corregidos y retest.
- Migraciones versionadas y ensayadas en base aislada cuando aplique.
- Evidencia, logs sin secretos, documentación de instalación y decisión ADR actualizada.
- Estado Git limpio del commit previsto o cambios explícitos acordados, no afirmaciones sin evidencia.

## Políticas irrevocables sin nuevo control de cambios
Una sola sede; cuentas separadas por vet; 5 roles laborales; portal cliente sin historia clínica íntegra; sin SUNAT/pasarela; stock único con descuento POS y confirmación online; devolución parcial solo inspeccionada, no despacho parcial; grooming con empleado y sin solape; datos de demostración sintéticos. Cambios requieren aprobación.
