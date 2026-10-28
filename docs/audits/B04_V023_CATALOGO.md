# B04 — Auditoría y verificación de V023

**Alcance:** categorías y productos tenant-locales.
**Base autorizada:** `arquitectura_veterinaria` local.
**Archivo aplicado:** `database/migration/V023__b04_catalog_categories_and_products.sql`.
**SHA-256:** `113D19A63F1675DF25255392EE0397F96A41B8A449AF87A35B768D863262DBB1`.

## Revisión previa y aplicación

- La migración es aditiva: crea el esquema `inventory_sales`, las tablas `product_category` y `product`, índices, RLS y ACL mínimas. No modifica V001–V022.
- No contiene funciones, `SECURITY DEFINER`, SQL dinámico, extensiones, `GRANT` a `PUBLIC`, `DELETE` para `av_app`, cambio de propietarios ni DDL destructivo.
- El preflight ejecutó el SQL completo dentro de `BEGIN` y terminó en `ROLLBACK`; antes y después del rollback no existía el esquema `inventory_sales`.
- El aplicador Flyway se ejecutó con Java 21 y rol de migración autorizado, dirigido a la base local y target `023`. V023 quedó registrada una sola vez, con checksum Flyway `-921549720` y `success=true`.
- Los arranques y pruebas ordinarias del backend mantuvieron `AV_FLYWAY_ENABLED=false`.

## Controles efectivos posteriores

| Control | Resultado verificado |
|---|---|
| Propietario | `av_schema_owner` para ambas tablas; no es superusuario ni tiene `BYPASSRLS`. |
| Rol de aplicación | `av_app` no es superusuario, no tiene `BYPASSRLS`, tiene `USAGE` del esquema y no puede crear objetos en él. |
| RLS | Habilitado en `product_category` y `product`, con políticas `USING` y `WITH CHECK` basadas en `current_setting('app.tenant_id', true)`. |
| ACL de tablas | `PUBLIC` no tiene `SELECT` ni `EXECUTE` aplicable; `av_app` conserva solo `SELECT`, `INSERT`, `UPDATE` sobre las dos tablas. |
| Identidades | `av_app` continúa sin acceso directo autorizado a identidades protegidas. |
| Sin contexto tenant | Una consulta de `av_app` sin `app.tenant_id` devolvió cero filas en ambas tablas. |
| Integridad local | FKs compuestas fuerzan categoría y autor del mismo tenant; los índices únicos normalizados impiden categoría y SKU repetidos dentro del tenant. |

El propietario puede omitir RLS por la semántica normal de PostgreSQL, pero el runtime `av_app` no es propietario y opera bajo RLS. No se añadió `FORCE ROW LEVEL SECURITY`, porque no forma parte del patrón de las migraciones anteriores ni es necesario para la ruta de aplicación de mínimo privilegio ya verificada.

## Regresiones de producto

- La primera regresión de catálogo detectó una consulta JDBC que no tipaba un filtro `NULL`; PostgreSQL devolvía que no podía inferir el tipo. La corrección mínima tipa los parámetros opcionales como `varchar`; no cambia esquema, permisos ni migraciones.
- `CatalogDatabaseIntegrationTest` confirma que un conflicto de SKU en alta o actualización devuelve `409` controlado y que la operación posterior conserva una transacción utilizable.
- El recorrido Playwright real pasó dos veces y la consulta posterior de solo lectura encontró exactamente una categoría y un producto para el fixture sintético idempotente.

## Riesgo residual y siguiente puerta

V023 no implementa stock, movimientos, POS, pagos, devoluciones ni catálogo público; por tanto no satisface todavía las invariantes de saldo, concurrencia o no duplicidad de B04. La siguiente migración deberá ser aditiva, preservar la autoridad única de stock y ser auditada antes de aplicarse.
