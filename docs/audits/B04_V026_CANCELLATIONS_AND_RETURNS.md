# Auditoría B04 V026 — anulaciones y devoluciones físicas

**Fecha:** 2026-10-28
**Alcance:** corrección comercial de ventas POS, devolución física e inspección apta. No incorpora reembolsos bancarios, SUNAT, pagos online ni B05.

## Migración aplicada

- Archivo: `database/migration/V026__b04_pos_cancellations_and_inspected_returns.sql`.
- SHA-256 fuente: `F0C4E75AD741B4A64EB189790017782DAA1FFEEBCC29FE871B1A15BDCD5CFA4D`.
- Flyway local: versión `026`, checksum `1874390031`, éxito y una sola fila.
- Preflight: `BEGIN` + V026 + `ROLLBACK` con `av_schema_owner`; creó temporalmente las tablas, índices, RLS y ACL y revirtió todo. La aplicación posterior fue una sola migración validada desde V025.

V026 es aditiva. Crea `pos_sale_cancellation`, `pos_return` y `pos_return_line`, y agrega la referencia nullable `pos_return_id` al ledger existente. No contiene funciones, SQL dinámico, extensiones, `SECURITY DEFINER`, permisos `PUBLIC`, borrados ni alteración de V001–V025.

## Reglas verificadas

- La anulación solo acepta una venta `COMPLETADA`, requiere motivo y actor, no elimina historia y no permite una segunda anulación. Cuando se declara que no hubo salida física, crea movimientos compensatorios `DEVOLUCION_APTA` una única vez; si hubo salida, no recompone saldo.
- Una devolución reserva una cantidad por línea de venta sin superar lo vendido menos lo ya solicitado. Solo acepta líneas inventariables.
- `SOLICITADA` y `RECIBIDA` no cambian inventario. La inspección bloquea devolución, venta y saldos; únicamente `restockedQuantity > 0` crea el movimiento `DEVOLUCION_APTA` y aumenta el saldo. La repetición devuelve el estado persistido sin un segundo movimiento.
- El cierre mantiene `DEVOLUCION_PARCIAL` o `DEVUELTA` según la recepción física total de líneas inventariables, conservando las líneas y sus instantáneas.
- ADMIN_VETERINARIA o CAJERO pueden consultar y corregir comercialmente dentro de su tenant. Solo CAJERO conserva autorización para crear/confirmar una venta ordinaria.

## Controles de base de datos

| Objeto | Propietario | RLS | Permiso de `av_app` |
|---|---|---|---|
| `pos_sale_cancellation` | `av_schema_owner` | activa | `SELECT`, `INSERT` |
| `pos_return` | `av_schema_owner` | activa | `SELECT`, `INSERT`, `UPDATE` |
| `pos_return_line` | `av_schema_owner` | activa | `SELECT`, `INSERT`, `UPDATE` |

`PUBLIC` no puede seleccionar `pos_return`; `av_app` no recibe ownership ni privilegios DDL. El contexto tenant procede de la sesión confiable antes de cada consulta JDBC.

## Evidencia ejecutada

- Red verde después de crear las pruebas antes de aplicar V026: fallaron exclusivamente porque las tablas nuevas aún no existían.
- Tras V026, Java 21 ejecutó la suite B04 focalizada **11/11** correcta, con Flyway desactivado durante la prueba. `PosSaleReturnDatabaseIntegrationTest` prueba anulación idempotente, reposición por venta no despachada, recepción sin cambio de saldo, inspección parcial apta, reintento sin doble movimiento, cierre e intento de retorno excedente.
- Angular: **8 archivos / 25 pruebas** correctas; el nuevo contrato HTTP cubre anulación, solicitud, recepción, inspección y cierre. Build correcto y OpenAPI validado por Redocly.

## Límites pendientes

- El E2E POS autenticado aún no se ejecuta: faltan `AV_DEMO_CASHIER_AURORA_EMAIL` y `AV_DEMO_CASHIER_AURORA_PASSWORD` en `.env.local`. El fixture versionado no contiene ni imprime valores y no concede CAJERO al administrador.
- Falta el informe B04 y una carrera concurrente dedicada a dos operaciones de devolución sobre la misma venta. B05 deberá reutilizar el ledger, no crear una segunda fuente de inventario.
