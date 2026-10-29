# Auditoria B04 V024 - stock compartido y movimientos

**Fecha:** 2026-10-28
**Alcance:** vertical B04 de saldo por producto, altas y ajustes manuales; no incluye aun POS, pedidos B05, pagos, anulaciones ni devoluciones.

## Migracion aplicada

- Archivo: `database/migration/V024__b04_shared_stock_and_immutable_movements.sql`.
- SHA-256 fuente: `E10E8A5DC46F890E21CCD348CF8B200AD4F92176F823C5CC4ABEAB3F8E86F785`.
- Flyway local: version `024`, checksum `-396847516`, `success = true`, una sola fila.
- Preflight previo: ejecucion de la fuente dentro de `BEGIN` y `ROLLBACK` sobre la base local autorizada; no dejo objetos persistentes. La aplicacion posterior fue una sola vez mediante el launcher restringido V024.

La migracion es aditiva: crea `inventory_sales.stock_balance` y `inventory_sales.stock_movement`, sus indices, FKs tenant-locales, checks de no negatividad/no cero y RLS. No contiene SQL dinamico, `SECURITY DEFINER`, extensiones, DDL destructivo ni grants a `PUBLIC`.

## Catalogo y privilegios efectivos

Consulta posterior en modo `default_transaction_read_only=on`:

| Objeto | Propietario | RLS |
|---|---|---|
| `inventory_sales.stock_balance` | `av_schema_owner` | activa |
| `inventory_sales.stock_movement` | `av_schema_owner` | activa |

| Rol/tabla | SELECT | INSERT | UPDATE | DELETE |
|---|---:|---:|---:|---:|
| `PUBLIC` / `stock_movement` | no | no | no | no |
| `av_app` / `stock_movement` | si | si | no | no |
| `av_app` / `stock_balance` | si | si | si | no |

La aplicacion no recibe privilegio para alterar ni borrar el ledger. El saldo se modifica solo desde el servicio transaccional B04, que deriva el tenant de la sesion, exige `ADMIN_VETERINARIA`, toma el bloqueo de producto y registra el movimiento en la misma transaccion. `operationId` se serializa mediante bloqueo asesor y la restriccion unica tenant-operacion evita duplicar reintentos.

## Evidencia ejecutada

- Backend Java 21, Flyway desactivado: `InventoryAccessPolicyTest`, `CatalogDatabaseIntegrationTest`, `StockDatabaseIntegrationTest` y `StockConcurrencyCommitIntegrationTest`: **5/5** correctas.
- La regresion concurrente crea saldo uno, libera dos transacciones coordinadas que descuentan una unidad y verifica un unico exito, un conflicto `B04_STOCK_INSUFFICIENT`, saldo final cero, dos movimientos y limpieza transaccional del fixture.
- Angular: `npm.cmd test -- --watch=false`: **7 archivos / 22 pruebas** correctas.
- Angular production build: correcto; bundle inicial **286.53 kB** y ruta lazy de inventario **27.66 kB**.
- Contrato: `npm.cmd run validate:contract`: Redocly correcto para las rutas de stock.
- Playwright/Chrome: `B04_STOCK_BROWSER_E2E=PASS`. STAFF crea o reutiliza producto sintetico desde Angular, aplica el ajuste visible necesario para saldo cinco, recarga, consulta movimientos y confirma que Bosque no recibe saldo ni ledger Aurora.
- Agent Browser: una visita anonima a `/inventory` fue redirigida a la pantalla de login, sin exponer saldo ni movimientos. La herramienta instalada no ofrece subcomando `a11y`; no se afirma una auditoria automatizada adicional.

## Limites pendientes

V024 no constituye el cierre B04. Faltan la venta presencial atomica, comprobante interno, pagos manuales, anulacion, devolucion, servicios cobrables, reportes y sus regresiones. El mismo saldo sera la unica autoridad que reutilizaran POS y B05.
