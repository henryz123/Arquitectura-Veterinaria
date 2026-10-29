# Trazabilidad B04 — catálogo, inventario y punto de venta

**Fecha de actualización:** 2026-10-28
**Fuente de verdad:** `arquitectura_veterinaria_v1_FINAL/B04_INVENTARIO_Y_POS.md` (inmutable).
**Checkpoint cubierto:** catálogo tenant-local y stock compartido con altas/ajustes y ledger inmutable. B04 no está cerrado.

| Requisito | Estado | Evidencia verificable | Pendiente para cierre V1 |
|---|---|---|---|
| RF-B04-001 | Verificado | ADMIN_VETERINARIA crea, lista, busca y actualiza categorías y productos reales mediante `/inventory` y `/api/v1/catalog/*`. El backend valida SKU local, estado, precio PEN, categoría tenant-local y autorización. Playwright comprobó alta, edición y recarga. | Incluir la operación de desactivación en una regresión visible específica. |
| RF-B04-002 | Parcial verificado | V024 conserva un saldo único por tenant/producto inventariable. Angular permite alta inicial y ajustes justificados; el backend deriva tenant de sesión y rechaza saldo negativo. | Reutilizar esta autoridad en POS y B05, sin una fuente paralela. |
| RF-B04-003 | Parcial verificado | Cada alta o ajuste persiste `operationId`, motivo, antes/después y tipo en `stock_movement`. El ledger es append-only para `av_app`. | Completar movimientos comerciales, conciliación y operaciones de retorno aprobadas. |
| RF-B04-004 | Pendiente | No existe venta POS ni descuento. | Transacción atómica de venta y salida. |
| RF-B04-005 | Parcial verificado | La regresión con dos transacciones coordinadas sobre una unidad permite un único descuento y conserva saldo cero; los bloqueos de producto y operación protegen el ajuste. | Repetir la garantía en confirmación POS y pedido web. |
| RF-B04-006 | Parcial verificado | `/api/v1/inventory/stock/movements` muestra movimientos reales filtrables por producto propio; RLS y ACL impiden edición/borrado directo del ledger. | Kardex/conciliación comercial y todos los tipos de movimiento. |
| RF-B04-007 | Pendiente | No existe venta ni líneas con precio congelado. | Registro comercial e historial tenant-local. |
| RF-B04-008 | Pendiente | Se puede marcar un producto como no inventariable, pero aún no hay venta de servicios. | Línea comercial de servicio sin movimiento de stock. |
| RF-B04-009 | Pendiente | No existe pago manual. | Registro y validación manual sin integración bancaria. |
| RF-B04-010 | Pendiente | No existe venta, anulación ni devolución. | Flujos auditados e idempotentes de restitución física apta. |
| RF-B04-011 | Parcial verificado | Solo `ADMIN_VETERINARIA` puede consultar o registrar ajustes; `InventoryAccessPolicyTest` conserva el rechazo para roles sin autoridad. | Política final de venta/caja y auditoría de acciones sensibles. |
| RF-B04-012 | Parcial verificado | El estado `INACTIVO` se persiste y la UI permite cambiarlo; no existe aún catálogo público B05 ni ventas históricas. | Ocultamiento web y conservación de líneas históricas. |
| RF-B04-013 | Pendiente | Existen saldos y movimientos de ajuste, pero no ventas ni informe comercial. | Informe simple sin duplicar POS y pedidos. |
| RF-B04-014 | Verificado para catálogo y stock | V023/V024 aplican RLS por tenant y ACL mínima. Backend y Playwright confirman que Bosque no recibe producto, saldo ni movimiento Aurora. | Repetir la misma garantía para ventas, pedidos y reportes al implementarlos. |
| RF-B04-015 | Pendiente | No existe venta de servicios ni referencia a atención. | Vinculación opcional y bloqueo de cobro duplicado. |

## Evidencia del checkpoint de catálogo

- V023 se aplicó una vez sobre `arquitectura_veterinaria`; SHA-256 fuente `113D19A63F1675DF25255392EE0397F96A41B8A449AF87A35B768D863262DBB1`, checksum Flyway `-921549720`.
- Backend: `mvnw.cmd '-Dtest=InventoryAccessPolicyTest,CatalogDatabaseIntegrationTest' test` con Java 21, PostgreSQL local y Flyway desactivado durante la prueba: **3/3 correctas**. Incluye autorización, SKU repetido al crear y actualizar, continuidad de la transacción después del conflicto, categoría ajena y actualización cross-tenant.
- Angular: `npm.cmd test -- --watch=false`: **7 archivos / 21 pruebas correctas**. `npm.cmd run build`: correcto; bundle inicial 286.53 kB y ruta `/inventory` lazy de 22.00 kB.
- OpenAPI: `npm.cmd run validate:contract`: Redocly correcto para las cinco operaciones de catálogo.
- Playwright/Chrome: `B04_CATALOG_BROWSER_E2E=PASS` dos veces. Crea o reutiliza la categoría y el producto sintéticos, edita el producto, recarga Angular y comprueba aislamiento Aurora/Bosque.
- PostgreSQL de solo lectura posterior: `CATEGORY_ROWS=1`, `PRODUCT_ROWS=1`, `V023_ROWS=1` para el fixture sintético de E2E.
- Agent Browser: una visita anónima a `/inventory` redirige a `/login/veterinaria-aurora` y expone `main`, título, etiquetas nativas y navegación de contexto. La versión instalada no ofrece el subcomando `a11y`; no se declara auditoría automática de accesibilidad.

## Evidencia del checkpoint de stock

- V024 se aplicó una vez: SHA-256 `E10E8A5DC46F890E21CCD348CF8B200AD4F92176F823C5CC4ABEAB3F8E86F785`; checksum Flyway `-396847516`.
- La revisión posterior confirma RLS en `stock_balance`/`stock_movement`, `PUBLIC` sin acceso al ledger y `av_app` sin `UPDATE` ni `DELETE` sobre movimientos.
- Backend focalizado: **5/5**; cubre alta, reintento idempotente, ajuste negativo, rechazo de saldo insuficiente, producto no inventariable, aislamiento y dos transacciones concurrentes.
- Angular: **7 archivos / 22 pruebas** y build correcto. Redocly validó las tres rutas `/api/v1/inventory/stock*`.
- Playwright: `B04_STOCK_BROWSER_E2E=PASS`; el saldo sintetico persiste tras recarga y Bosque no recibe datos Aurora. Agent Browser confirmó que la ruta protegida redirige a login anónimo.
- Auditoría detallada: `docs/audits/B04_V024_STOCK.md`.

## Próxima operación concreta

Implementar el POS presencial sobre el saldo único V024: borrador de venta, confirmación atómica, comprobante interno, historial y regresiones de descuento único, rollback, concurrencia y aislamiento Aurora/Bosque.
