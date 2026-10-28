# Trazabilidad B04 — catálogo, inventario y punto de venta

**Fecha de actualización:** 2026-10-28
**Fuente de verdad:** `arquitectura_veterinaria_v1_FINAL/B04_INVENTARIO_Y_POS.md` (inmutable).
**Checkpoint cubierto:** catálogo tenant-local de categorías y productos. B04 no está cerrado.

| Requisito | Estado | Evidencia verificable | Pendiente para cierre V1 |
|---|---|---|---|
| RF-B04-001 | Verificado | ADMIN_VETERINARIA crea, lista, busca y actualiza categorías y productos reales mediante `/inventory` y `/api/v1/catalog/*`. El backend valida SKU local, estado, precio PEN, categoría tenant-local y autorización. Playwright comprobó alta, edición y recarga. | Incluir la operación de desactivación en una regresión visible específica. |
| RF-B04-002 | Pendiente | V023 solo define el atributo `inventory_managed`; todavía no existe saldo. | Una fuente de stock por tenant/producto compartida con B05. |
| RF-B04-003 | Pendiente | No se registra saldo inicial ni ajuste. | Motivo, auditoría y movimiento inmutable. |
| RF-B04-004 | Pendiente | No existe venta POS ni descuento. | Transacción atómica de venta y salida. |
| RF-B04-005 | Pendiente | No existe saldo concurrente que proteger. | Prueba de dos descuentos y no negatividad. |
| RF-B04-006 | Pendiente | No existe kardex. | Movimientos inmutables y conciliación de saldo. |
| RF-B04-007 | Pendiente | No existe venta ni líneas con precio congelado. | Registro comercial e historial tenant-local. |
| RF-B04-008 | Pendiente | Se puede marcar un producto como no inventariable, pero aún no hay venta de servicios. | Línea comercial de servicio sin movimiento de stock. |
| RF-B04-009 | Pendiente | No existe pago manual. | Registro y validación manual sin integración bancaria. |
| RF-B04-010 | Pendiente | No existe venta, anulación ni devolución. | Flujos auditados e idempotentes de restitución física apta. |
| RF-B04-011 | Pendiente | La política actual niega la gestión de catálogo a CAJERO, pero todavía no hay ajustes manuales que proteger. | Autorización específica de ajuste ADMIN frente a CAJERO. |
| RF-B04-012 | Parcial verificado | El estado `INACTIVO` se persiste y la UI permite cambiarlo; no existe aún catálogo público B05 ni ventas históricas. | Ocultamiento web y conservación de líneas históricas. |
| RF-B04-013 | Pendiente | No existen ventas ni saldos. | Informe simple sin duplicar POS y pedidos. |
| RF-B04-014 | Verificado para el catálogo | V023 aplica RLS por tenant y ACL mínima; la integración rechaza actualización Bosque de un producto Aurora y Playwright confirma que Bosque no recibe el SKU Aurora. | Repetir la misma garantía para stock, ventas y reportes al implementarlos. |
| RF-B04-015 | Pendiente | No existe venta de servicios ni referencia a atención. | Vinculación opcional y bloqueo de cobro duplicado. |

## Evidencia del checkpoint de catálogo

- V023 se aplicó una vez sobre `arquitectura_veterinaria`; SHA-256 fuente `113D19A63F1675DF25255392EE0397F96A41B8A449AF87A35B768D863262DBB1`, checksum Flyway `-921549720`.
- Backend: `mvnw.cmd '-Dtest=InventoryAccessPolicyTest,CatalogDatabaseIntegrationTest' test` con Java 21, PostgreSQL local y Flyway desactivado durante la prueba: **3/3 correctas**. Incluye autorización, SKU repetido al crear y actualizar, continuidad de la transacción después del conflicto, categoría ajena y actualización cross-tenant.
- Angular: `npm.cmd test -- --watch=false`: **7 archivos / 21 pruebas correctas**. `npm.cmd run build`: correcto; bundle inicial 286.53 kB y ruta `/inventory` lazy de 22.00 kB.
- OpenAPI: `npm.cmd run validate:contract`: Redocly correcto para las cinco operaciones de catálogo.
- Playwright/Chrome: `B04_CATALOG_BROWSER_E2E=PASS` dos veces. Crea o reutiliza la categoría y el producto sintéticos, edita el producto, recarga Angular y comprueba aislamiento Aurora/Bosque.
- PostgreSQL de solo lectura posterior: `CATEGORY_ROWS=1`, `PRODUCT_ROWS=1`, `V023_ROWS=1` para el fixture sintético de E2E.
- Agent Browser: una visita anónima a `/inventory` redirige a `/login/veterinaria-aurora` y expone `main`, título, etiquetas nativas y navegación de contexto. La versión instalada no ofrece el subcomando `a11y`; no se declara auditoría automática de accesibilidad.

## Próxima operación concreta

Implementar el vertical de stock compartido: saldo único por tenant/producto, entradas y ajustes autorizados, movimientos inmutables, historial y pruebas de rollback, reintento, concurrencia y aislamiento Aurora/Bosque. Después continuará el POS sobre esa única fuente de saldo.
