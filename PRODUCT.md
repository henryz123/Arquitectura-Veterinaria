# PRODUCT.md — Arquitectura - Veterinaria

## Estado y autoridad

Este Product Brief deriva exclusivamente de "arquitectura_veterinaria_v1_FINAL/", la línea base funcional V1. Describe producto y límites aprobados; no declara código, arquitectura física, diseño visual definitivo, pruebas ejecutadas ni cumplimiento para operación real.

## Producto

SaaS B2B académico para varias veterinarias independientes del Perú, con una sede por veterinaria. Integra la gestión de propietarios y mascotas, atención clínica, agenda, grooming, catálogo, inventario compartido, POS y pedidos online. La demostración utiliza únicamente datos sintéticos.

## Personas y ámbitos

- Administrador SaaS: administra organizaciones, suspensiones, disputas RUC y recuperaciones excepcionales mediante expedientes acotados; no consulta historias clínicas ni operación interna ordinaria.
- Administrador de veterinaria: gestiona trabajadores, roles, catálogo, ajustes de inventario y pedidos dentro de su tenant. Para operar POS debe acumular rol cajero.
- Veterinario: registra y rectifica información clínica dentro de su tenant.
- Recepción: coordina propietarios, vinculaciones, transferencias, citas y grooming; no edita clínica.
- Cajero: registra ventas POS, valida pagos manuales y gestiona pedidos autorizados.
- Encargado de grooming: ejecuta servicios asignados; no confirma reservas por ese rol aislado ni consulta historia clínica íntegra.
- Cliente y visitante: consultan el catálogo público del tenant; el cliente autenticado opera únicamente sus mascotas, solicitudes y pedidos autorizados.

## Capacidades V1

1. Alta, verificación y activación de veterinarias; cuentas de trabajadores y clientes independientes por contexto.
2. Registro de propietarios y mascotas, vinculación manual, transferencia trazable e historia clínica con adendas.
3. Citas clínicas y grooming con confirmación humana, personal disponible y agenda consolidada sin solapamientos.
4. Productos, inventario único, movimientos auditables, POS y pagos manuales.
5. Catálogo público por tenant, carrito sin reserva, pedidos de recojo o delivery de tarifa fija, cancelaciones y devoluciones parciales inspeccionadas.
6. Aislamiento por tenant, permisos, auditoría, notificaciones separadas de las transacciones y reportes operativos básicos.

## Reglas de producto no negociables

- Una cuenta, un rol o un identificador de otro tenant no concede acceso.
- El portal cliente no muestra historia clínica completa, diagnósticos ni prescripciones.
- Agenda, pago, atención clínica, pedido, entrega, movimiento de stock y devolución son hechos separados y auditables.
- POS descuenta stock al completar la venta; el pedido web lo descuenta solamente al confirmar. Carritos y pedidos pendientes no reservan inventario.
- No existen saldos negativos, descuentos o restituciones duplicados ni despachos parciales.
- Los montos se expresan en PEN con precisión exacta; instantes en UTC y agenda en "America/Lima".
- Las constancias son internas y no tributarias; no son boletas ni facturas SUNAT.

## Exclusiones V1

No incluye multisucursal, aplicación móvil nativa, pasarela de pago, integración SUNAT, acceso cliente a expediente clínico completo, hospitalización avanzada, laboratorio, GPS, compras a proveedores, contabilidad integral, BI complejo ni datos clínicos reales.

## Planificación técnica aprobada

La planificación futura parte de Java 21, Spring Boot 4.1.1, Maven/Maven Wrapper, Angular 21 con TypeScript, PostgreSQL 18, monolito modular, DDD estratégico y arquitectura hexagonal selectiva. Esta decisión no sustituye los ADR, NFR, modelo físico, contratos ni diseño de seguridad pendientes.

La presentación del 26 de octubre de 2026 es un avance funcional con URL pública y repositorio público solo después de las revisiones autorizadas. No equivale a cubrir los 118 requisitos de V1. La entrega académica final tiene como objetivo el 18 de noviembre de 2026; el presupuesto inicial es S/ 0 y se mantiene un único producto definitivo con pruebas reproducibles antes de publicar cambios.

## Pendiente antes de desarrollo

Definir y aprobar NFR medibles, modelo físico de datos, ADR, C4/arc42, límites de módulos, contratos, autenticación y sesiones, privacidad, retención, backup/restauración, despliegue, estrategia de pruebas, UX y "DESIGN.md". La autorización para programar solo ocurre tras G3.

## Fuentes

- Línea base funcional: "arquitectura_veterinaria_v1_FINAL/".
- Planificación por puertas vigente: "docs/plan_maestro_codex/". El historial V0.2 se conserva en "docs/historial/plan_maestro_codex_v0_2/".
- Método de trabajo: "instrucciones_maestras_chatgpt_v1_1.md" y "manual_protocolo_integral_chatgpt_v1_1.md".
