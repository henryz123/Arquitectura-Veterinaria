# G3 — Incrementos y ruta crítica

## I00 — Fundaciones verificables

**Objetivo:** crear el entorno del único producto solo después de autorización: JDK 21 activo, Maven Wrapper, Angular 21, PostgreSQL de prueba, contrato OpenAPI mínimo, Flyway y configuración segura.
**No cubre RF por sí mismo:** habilita pruebas y no debe declararse funcionalidad de V1.
**Bloqueadores actuales:** JRE 8 activa, Maven/Angular CLI/Docker ausentes.

## I01 — Identidad, tenant y acceso SaaS

**Cobertura prioritaria:** B01, B06-001/002.
**Vertical real:** cuenta laboral contextual → sesión revocable → rol/tenant efectivo → recurso protegido → UI Angular que informa estado sin exponer datos ajenos.

### Primera tarea exacta propuesta: G4-I01-T01

| Campo | Definición |
|---|---|
| Título | Contexto de identidad, rol y aislamiento de tenant |
| RF | RF-AUT-01, RF-AUT-04, RF-AUT-05, RF-AUT-10, RF-AUT-13, RF-B06-001, RF-B06-002 |
| Objetivo | Dos tenants sintéticos, cuentas laborales independientes y un recurso mínimo protegido con negaciones cross-tenant/rol/suspensión. |
| Archivos autorizados futuros | `backend/`, `frontend/`, `database/`, `docs/contracts/openapi-v1.yaml`, pruebas y evidencia de la tarea; nada más. |
| Exclusiones | registro SaaS/RUC, correo real, invitaciones, clínica, agenda, inventario, pedidos, pago, despliegue y URL pública. |
| Pruebas | TDD de política de acceso, integración PostgreSQL/RLS y casos negativos; contrato y UI E2E solo si el preflight completa I00. |
| Cierre | resultados reales, revisión independiente de seguridad y trazabilidad actualizada; sin secretos. |

**Comando inicial previsto, no autorizado ni ejecutable todavía:** `mvnw.cmd test` desde `backend/` tras crear el wrapper autorizado. El comando no se ejecuta hasta que I00 exista y la tarea reciba luz verde.

## I02 — Propietarios, mascotas y clínica

Implementa B02 sobre I01: propietario/mascota, vínculo, transferencia y privacidad clínica. La demostración valida que recepción y cliente no equivalen a veterinario y que una adenda no destruye episodio finalizado.

## I03 — Agenda clínica y grooming

Implementa B03: disponibilidad, solicitud, propuesta, aceptación, asignación y no traslape real de profesional. Esta ruta solo se presenta si la prueba de concurrencia pasa contra PostgreSQL.

## I04 — Inventario y POS

Implementa B04: catálogo, saldo único, kardex, venta/pago manual y devolución. La ruta de demo exige competir POS/web o dos operaciones concurrentes por última unidad y evidenciar una sola confirmación.

## I05 — Comercio web

Implementa B05 sobre el inventario real: catálogo por tenant activo, carrito sin reserva, pedido, confirmación, estados, cancelación y devolución parcial. No se simulan pagos online ni SUNAT.

## I06 — Transversal y estabilización

Completa B06: auditoría/outbox, notificaciones desacopladas, dashboards/reportes, tiempo/moneda, datos sintéticos, pruebas de seguridad, a11y y recuperación.

## Ruta crítica hacia el 26/10/2026

1. I00 y G4-I01-T01 sin saltar pruebas ni ocultar bloqueadores.
2. Vertical de propietario/mascota + una atención o cita si I02/I03 pasa sus integraciones.
3. Vertical de catálogo/inventario/POS con stock consistente si I04 pasa concurrencia.
4. Añadir pedido web solo si no degrada los verticales anteriores.
5. Solo tras revisión de secretos/licencias/datos y autorización, desplegar el mismo producto definitivo.

La presentación es avance funcional. Cada RF que no tenga evidencia se muestra como pendiente, no como completo.
