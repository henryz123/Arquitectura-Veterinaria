# G3 — Incrementos y ruta crítica

## I00 — Fundaciones verificables y vertical técnico limitado

**Objetivo:** crear el entorno del único producto solo después de autorización: JDK 21 activo, Maven Wrapper, Angular 21, PostgreSQL de prueba, contrato OpenAPI mínimo, Flyway y configuración segura.

La instrucción maestra G4-I00 vigente autoriza, como excepción explícita y limitada, un primer vertical de identidad y aislamiento que valida las fundaciones con integración real. Sigue siendo **I00**, no inicia I01 ni declara B01 completo. Solo puede reportar cobertura cuando existan pruebas ejecutadas de `RF-AUT-01`, `RF-AUT-04`, `RF-AUT-05`, `RF-AUT-10`, `RF-AUT-13` y la porción aplicable de `RF-B06-001/002`; el resto de B01 y B06 permanece pendiente.

**Alcance estricto del vertical I00:** dos tenants sintéticos, dos cuentas laborales independientes, sesión opaca revocable, contexto confiable, perfil mínimo del tenant propio, Angular conectado a la API, PostgreSQL con RLS y pruebas negativas cross-tenant. No habilita registro SaaS/RUC, correo real, invitaciones, recuperación, cuentas cliente, B02–B05 ni endpoints administrativos globales.

**Bloqueadores de entrada verificados:** JRE 8 activa sin `javac`, Maven/Angular CLI/Docker ausentes y validador OpenAPI no instalado. Docker no bloquea I00 si la ruta local Java/Angular/PostgreSQL resulta reproducible.

## I01 — Resto de identidad, tenant y acceso SaaS

**Cobertura prioritaria:** B01, B06-001/002.
**Vertical real:** cuenta laboral contextual → sesión revocable → rol/tenant efectivo → recurso protegido → UI Angular que informa estado sin exponer datos ajenos.

### Tarea originalmente propuesta, absorbida de forma limitada por G4-I00

| Campo | Definición |
|---|---|
| Título | Contexto de identidad, rol y aislamiento de tenant |
| RF | RF-AUT-01, RF-AUT-04, RF-AUT-05, RF-AUT-10, RF-AUT-13, RF-B06-001, RF-B06-002 |
| Objetivo | Dos tenants sintéticos, cuentas laborales independientes y un recurso mínimo protegido con negaciones cross-tenant/rol/suspensión. |
| Archivos autorizados | `backend/`, `frontend/`, `database/`, `docs/contracts/openapi-v1.yaml`, pruebas y evidencia de G4-I00; nada más. |
| Exclusiones | registro SaaS/RUC, correo real, invitaciones, clínica, agenda, inventario, pedidos, pago, despliegue y URL pública. |
| Pruebas | TDD de política de acceso, integración PostgreSQL/RLS y casos negativos; contrato y UI E2E solo si el preflight completa I00. |
| Cierre | resultados reales, revisión independiente de seguridad y trazabilidad actualizada; sin secretos. La cobertura se registra como G4-I00 y no habilita el resto de I01. |

**Comando inicial previsto:** `mvnw.cmd test` desde `backend/` tras crear el wrapper. La instrucción G4-I00 vigente autoriza su ejecución únicamente tras completar el preflight y revisar el contrato.

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

1. G4-I00 con su vertical técnico limitado, sin saltar pruebas ni ocultar bloqueadores.
2. Vertical de propietario/mascota + una atención o cita si I02/I03 pasa sus integraciones.
3. Vertical de catálogo/inventario/POS con stock consistente si I04 pasa concurrencia.
4. Añadir pedido web solo si no degrada los verticales anteriores.
5. Solo tras revisión de secretos/licencias/datos y autorización, desplegar el mismo producto definitivo.

La presentación es avance funcional. Cada RF que no tenga evidencia se muestra como pendiente, no como completo.
