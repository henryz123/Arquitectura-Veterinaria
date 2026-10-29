# Trazabilidad B04 — catálogo, inventario y punto de venta

**Fecha de actualización:** 2026-10-28
**Fuente de verdad:** `arquitectura_veterinaria_v1_FINAL/B04_INVENTARIO_Y_POS.md` (inmutable).
**Checkpoint cubierto:** catálogo, stock compartido y POS presencial con comprobante interno. B04 sigue parcial.

| Requisito | Estado | Evidencia verificable | Pendiente para cierre V1 |
|---|---|---|---|
| RF-B04-001 | Verificado | ADMIN crea, busca y edita categorías/productos reales en `/inventory`; SKU, precio PEN, estado, categoría y tenant se validan en servidor. | Añadir regresión visible específica de desactivación. |
| RF-B04-002 | Verificado para POS | V024 mantiene un saldo único por tenant/producto; V025 lo reutiliza al confirmar POS, sin una segunda fuente de existencias. | Repetir la invariante al implementar B05. |
| RF-B04-003 | Parcial verificado | Altas, ajustes y `VENTA_POS` se escriben en `stock_movement` inmutable con operación, antes/después y actor. | Conciliación y movimientos de retorno autorizados. |
| RF-B04-004 | Verificado para venta presencial | CAJERO prepara líneas, confirma en una transacción y obtiene detalle/comprobante e historial por tenant. Las pruebas cubren rollback por stock insuficiente. | Anulación/devolución y sus flujos visuales. |
| RF-B04-005 | Verificado para POS | Dos confirmaciones reales que compiten por una unidad dejan un único `COMPLETADA`, un rechazo `B04_STOCK_INSUFFICIENT`, saldo cero y un único descuento POS. | Aplicar la misma regresión a pedidos web. |
| RF-B04-006 | Parcial verificado | El ledger no concede `UPDATE`/`DELETE` a `av_app`; la venta se enlaza con sus movimientos y el historial POS es consultable. | Kardex e informe comercial simplificado. |
| RF-B04-007 | Verificado para POS | V025 conserva SKU, título, cantidad, precio unitario, subtotal y total en líneas/comprobante tenant-local. | Historial visual E2E con una cuenta CAJERO legítima. |
| RF-B04-008 | Verificado para POS | Una línea no inventariable se confirma sin saldo ni movimiento, mientras las líneas físicas descuentan el saldo único. | Integración opcional de servicio con atención, si V1 la exige. |
| RF-B04-009 | Verificado para POS | La confirmación persiste un único pago manual `VALIDADO` con método e importe exacto; no hay integración bancaria ni SUNAT. | Revisar variantes operativas aprobadas de pago manual. |
| RF-B04-010 | Pendiente | La estructura admite estados de anulación/devolución, pero todavía no existe flujo ni restitución física implementados. | Anulación, devolución e inspección apta con movimientos correctos. |
| RF-B04-011 | Verificado | Solo ADMIN registra ajustes; solo CAJERO puede usar POS. ADMIN sin CAJERO recibe `B04_CASHIER_REQUIRED`. | Incluir acciones de retorno cuando existan. |
| RF-B04-012 | Parcial verificado | Productos `INACTIVO` se rechazan al crear/confirmar venta y las líneas históricas preservan su instantánea. | Catálogo web B05 y regresión visible de ocultamiento. |
| RF-B04-013 | Parcial | El historial tenant-local de ventas y comprobantes está disponible. | Reporte comercial simple sin duplicar fuentes. |
| RF-B04-014 | Verificado para POS | RLS, ACL y contexto de sesión aíslan catálogo, saldo, venta, líneas y pago. La integración rechaza la venta Bosque de un producto Aurora. | Repetir la evidencia para B05 y reportes. |
| RF-B04-015 | Parcial | El POS cobra productos y servicios reales, sin referencia clínica opcional implementada. | Definir e implementar la vinculación solo conforme a V1. |

## Evidencia consolidada

- V023: SHA-256 `113D19A63F1675DF25255392EE0397F96A41B8A449AF87A35B768D863262DBB1`; Flyway `-921549720`, una aplicación.
- V024: SHA-256 `E10E8A5DC46F890E21CCD348CF8B200AD4F92176F823C5CC4ABEAB3F8E86F785`; Flyway `-396847516`, una aplicación.
- V025: SHA-256 `2DBE63322EFBAB51D4FFED52EF6A22FC6C0783B7069D9EE6C27B8A0E3CEEEFF4`; Flyway `-964356549`, una aplicación.
- Backend B04 actual: `InventoryAccessPolicyTest`, `StockDatabaseIntegrationTest`, `StockConcurrencyCommitIntegrationTest`, `PointOfSaleDatabaseIntegrationTest` y `PointOfSaleConcurrencyCommitIntegrationTest`: **8/8** con Java 21, PostgreSQL local y Flyway desactivado en pruebas ordinarias.
- Angular: `npm.cmd test -- --watch=false`: **7 archivos / 22 pruebas correctas**. Build correcto: **286.69 kB** inicial y POS lazy **17.73 kB**.
- Contrato: `npm.cmd run validate:contract` validó OpenAPI con Redocly.
- E2E: catálogo y stock tienen recorridos Playwright previos correctos. El E2E POS autenticado queda bloqueado honestamente: no hay variables locales `AV_DEMO_CASHIER_*` ni cuenta CAJERO sintética aprobada; no se concedió el rol a ADMIN como atajo.
- Limpieza: la comprobación de solo lectura posterior a la carrera POS encontró **0** fixtures `Producto POS carrera` o `Producto limitado` residuales.

## Próxima operación concreta

Completar los flujos B04 pendientes de anulación, devolución e informe simple; en paralelo preparar una cuenta CAJERO sintética mediante el procedimiento B01 aprobado para ejecutar el E2E POS sin debilitar roles.
