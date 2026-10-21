# B04 — Productos, inventario y ventas presenciales | Especificación funcional V1.0 FINAL

**Estado:** CERRADO FUNCIONALMENTE para V1 académica. **Procesos:** PB-07, PB-08 y parte comercial de PB-12. **Dependencias:** Bloque 1 (admin/cajero), B03 solo para cobro de servicios. **Confirmado:** inventario compartido físico/online por veterinaria; admin crea productos y ajusta stock, cajero vende y gestiona pedidos, pendientes web sin descuento; restituir una sola vez cuando procede.

## 1. Alcance y modelo operativo

V1 contiene un catálogo por veterinaria, existencias de un único almacén lógico (una sede), ventas de caja y registro de movimientos de stock. No contiene compras a proveedores, contabilidad general, múltiples almacenes, lotes/series estrictos, POS hardware, emisión tributaria SUNAT automática ni devoluciones de dinero por pasarela.

**Propuesta:** vender artículos y, cuando corresponda, **servicios clínicos o grooming como líneas sin stock**. No se genera un cargo automáticamente por crear una cita o completar una consulta; el cajero realiza el registro comercial por separado, con referencia opcional a la atención para evitar cobro repetido.

## 2. Casos de uso

| ID | Operación |
|---|---|
| CU-B04-01 | Crear/editar/desactivar producto |
| CU-B04-02 | Registrar stock inicial y ajustes manuales |
| CU-B04-03 | Consultar inventario y kardex lógico |
| CU-B04-04 | Registrar venta presencial |
| CU-B04-05 | Registrar pago declarado/validado por empleado |
| CU-B04-06 | Anular o rectificar una venta presencial autorizada |
| CU-B04-07 | Registrar devolución física de venta presencial |
| CU-B04-08 | Consultar operaciones y reportes comerciales básicos |

## 3. Entidades y campos básicos

- **Producto**: tenant, SKU único local (opcional código interno autogenerado), título, descripción, categoría simple, precio de venta PEN con dos decimales, visibilidad web, activo, stock inventariable sí/no, imagen opcional segura. SKU puede repetirse en distintos tenants.
- **StockActual**: tenant+producto, unidades vendibles enteras >=0, versión para control de concurrencia. Para producto sin stock (servicio), esta entidad no existe.
- **MovimientoStock**: tenant, producto, delta (+/- cantidad), tipo (`ALTA_INICIAL`, `AJUSTE_POSITIVO`, `AJUSTE_NEGATIVO`, `VENTA_POS`, `CONFIRMACION_ONLINE`, `CANCELACION_ONLINE`, `DEVOLUCION_APTA`, `RECONCILIACION`), referencia de operación, actor y fecha. Movimientos **inmutables** y con clave de idempotencia; corregir con movimiento compensador, no editando el pasado.
- **VentaPOS**: tenant, cajero, líneas con snapshots de precio/cantidad, descuentos (fuera de V1 salvo decisión), total, instante de venta, estado `COMPLETADA`, `ANULADA` o `DEVOLUCION_PARCIAL`/`DEVUELTA`; referencia opcional a cita/servicio atendido.
- **LineaPOS**: producto inventariable o servicio no inventariable, cantidad entera >0, precio congelado, subtotal calculado. Si el tipo es servicio, no descontar stock.
- **PagoManual**: referencia a venta/pedido, forma informada (`EFECTIVO`, `TRANSFERENCIA`, `OTRO`), importe declarado, estado (`PENDIENTE_VALIDACION`, `VALIDADO`, `RECHAZADO`), usuario que valida y fecha. No consultar automáticamente bancos.
- **DevolucionPOS**: origen venta y líneas/cantidades, cantidad física recibida, estado de aptitud para reventa, ajuste económico registrado de manera manual, responsable y fecha.
- **AjusteInventario**: motivo, cantidad +/-, responsable con rol administrador, referencia a stock antes/después, fecha.

## 4. Invariantes contables y de stock

1. Cada producto es propio de una veterinaria. Un `tenant_id` ajeno jamás puede obtener ni modificar stock, precios o ventas.
2. Las cantidades de inventario se gestionan en **unidades enteras no negativas**, sin fracciones en V1. Los servicios son no inventariables.
3. El **precio** se almacena en PEN de forma exacta (entero de céntimos o decimal fijo). No usar aritmética monetaria binaria de punto flotante como fuente de verdad.
4. El stock disponible no puede volverse negativo por una venta, confirmación de pedido o ajuste negativo. Toda operación valida y descuenta en una única transacción consistente.
5. El saldo de stock tiene explicación en un **registro histórico de movimientos**: saldo inicial + movimientos posteriores; alertar cuando no cuadre con saldo calculado.
6. **Una operación comercial produce una sola salida de stock**. La venta POS y la confirmación online tienen distintos identificadores y causas; marcar venta online como `ENTREGADA` no descuenta otra vez.
7. Un **reintento** de la misma venta, cancelación o ajuste no puede repetir el asiento de stock; respuesta idempotente o error sin duplicación.
8. Solo administrador hace ajustes manuales de stock. Cajero puede descontar/restituir **exclusivamente** mediante la operación comercial autorizada. `DEVOLUCION_APTA` procede cuando se comprobó devolución física y estado vendible.
9. Baja/despublicación de producto no elimina sus líneas históricas ni precios congelados. Si stock es 0, impedir venta y confirmación de pedido.
10. La venta presencial queda finalizada con sus líneas; para corregirla, ejecutar anulación o devolución auditada, no editar retrospectivamente el total histórico.
11. Los ingresos comerciales reportados provienen de ventas registradas y pedidos online finalizados/pagados según fuente separada, **sin sumar el mismo evento dos veces**. No hay estados bancarios verificados automáticamente.
12. El inventario online no se reserva por un carrito o pedido pendiente, de acuerdo a decisión confirmada.

## 5. Flujos

### F-B04-01 — Alta y ajuste de producto

Admin introduce nombre, SKU, categoría y precio → establece visibilidad, inventariable y existencia inicial → valida precios/cantidades → registra producto y movimiento inicial. Cambio de precio afecta futuro catálogo, nunca snapshots ya creados. Ajuste manual requiere motivo y autorización, comprueba que resultado no sea negativo, crea movimiento inmutable.

### F-B04-02 — Venta presencial

Cajero abre venta → busca producto dentro de su veterinaria → añade productos/servicios (sin stock) → revisa cantidades y precios → confirma → sistema valida stock y descuenta de forma transaccional por cada producto → crea venta y movimientos inmutables → registra tipo de pago y estado manual → entrega constancia interna **no fiscal** si se implementa impresión. Si pago falla o venta no se confirma, no debe quedar un descuento huérfano.

### F-B04-03 — Dos cajas / tienda online compitiendo

Stock producto P=1. Cajero A intenta vender 1; cajero B intenta vender 1; pedido web C intenta confirmar 1. Solo una operación puede confirmar el descuento. Las demás reciben `STOCK_INSUFICIENTE` y permanecen sin confirmar/compensar. Repeticiones de petición A no generan ventas duplicadas.

### F-B04-04 — Anulación y devolución POS

Cajero/admin identifica venta propia del tenant y condición → registra anulación o devolución; antes de restituir debe conocerse estado físico del artículo. Si artículo no salió físicamente del establecimiento, se puede reponer según anulación válida; si salió, se registra recepción y se inspecciona aptitud antes de mover a stock vendible. Restituir cantidad no superior a la previamente descontada menos restituciones previas. Si hay desacuerdo o ajuste excepcional, aprobación de administrador. Un cambio económico no equivale automáticamente a un movimiento físico.

### F-B04-05 — Cobro de servicios

Cajero selecciona servicio no inventariable prestado → enlaza opcionalmente referencia de consulta/grooming → registra venta manual de servicio → cero movimientos de stock. Si el servicio ya consta cobrado por una operación asociada, advertir y bloquear duplicado por referencia salvo anulación/corrección documentada.

## 6. Estados

- Producto: `BORRADOR` (propuesta opcional), `ACTIVO_PUBLICADO`, `ACTIVO_NO_PUBLICADO`, `INACTIVO`. Solo activos publicados aparecen online, pero activos no publicados pueden venderse en mostrador.
- Venta presencial: `PREPARACION` → `COMPLETADA` → `ANULADA` o `DEVOLUCION_PARCIAL` / `DEVUELTA`. `PREPARACION` no modifica stock; confirmar sí. No se borra el historial.
- Pago manual: `NO_DECLARADO` → `PENDIENTE_VALIDACION` → `VALIDADO` / `RECHAZADO`; corregir mediante nuevo registro/decisión y auditoría.
- Devolución: `SOLICITADA` → `RECIBIDA` → `INSPECCIONADA_APTA` / `INSPECCIONADA_NO_APTA` → `CERRADA`. Reposición solo para unidades aptas físicamente recibidas.

## 7. Matriz de permisos

| Acción | Admin | Cajero | Cliente | Otros roles aislados |
|---|---|---|---|---|
| Crear/editar producto/precio | Sí | No | No | No |
| Ver stock y movimientos generales | Sí | Consulta operativa limitada (propuesta) | Solo disponibilidad pública | No |
| Ajustar stock manual | Sí | No | No | No |
| Registrar venta presencial | Solo con rol cajero adicional (política funcional V1) | Sí | No | No |
| Anular venta/comercial autorizada | Sí | Sí | Solicitar según su operación | No |
| Inspeccionar retorno físico | Admin/cajero autorizado (propuesta) | Sí dentro de flujo | No | No |
| Aprobar restitución excepcional | Sí | No | No | No |
| Registrar pago manual | Sí | Sí | No | No |

**Permiso V1 fijado:** el administrador de veterinaria requiere acumular expresamente rol cajero para registrar ventas presenciales; gestionar pedidos online no concede permiso de venta POS.

## 8. Requisitos funcionales candidatos

| ID | Exigencia |
|---|---|
| RF-B04-001 | CRUD controlado de productos por administrador |
| RF-B04-002 | Stock único por producto y veterinaria para ambos canales |
| RF-B04-003 | Registro de stock inicial y ajuste con motivo y auditoría |
| RF-B04-004 | Descuento atómico de stock en venta presencial |
| RF-B04-005 | Concurrencia y prevención de inventario negativo |
| RF-B04-006 | Kardex inmutable y conciliable |
| RF-B04-007 | Registro comercial de ventas y precios congelados |
| RF-B04-008 | Venta de servicios no inventariables sin movimiento de stock |
| RF-B04-009 | Registro manual de medios y validación de pago |
| RF-B04-010 | Anulación/devolución auditada y restitución apta, idempotente |
| RF-B04-011 | Bloquear ajustes manuales por cajero sin rol administrador |
| RF-B04-012 | Ocultar productos inactivos del catálogo sin perder ventas históricas |
| RF-B04-013 | Informe simple de ventas y stock con no duplicidad entre canales |
| RF-B04-014 | No exponer precios/stock/ventas internos de otro tenant |
| RF-B04-015 | Vincular opcionalmente servicios prestados a venta sin cobro duplicado |

## 9. Pruebas de aceptación

| ID | Escenario | Resultado |
|---|---|---|
| AT-B04-01 | Ajuste de stock por cajero sin rol admin | Denegado |
| AT-B04-02 | Stock=1; dos ventas concurrentes de 1 | Solo una venta válida; stock=0 |
| AT-B04-03 | Stock=1; confirmación online y venta POS simultáneas | Solo una descuenta; no negativo |
| AT-B04-04 | Reintentar confirmar misma venta | No se descuenta dos veces |
| AT-B04-05 | Venta incluye servicio clínico | Servicio suma al total; stock sin cambio |
| AT-B04-06 | Producto oculto en web | No aparece público; sigue en ventas históricas |
| AT-B04-07 | Cambio de precio después de venta | Venta guarda precio original |
| AT-B04-08 | Devolución de producto defectuoso | Sin reposición a vendible |
| AT-B04-09 | Devolución física apta | Reposición una vez y dentro de cantidad vendida |
| AT-B04-10 | Dos confirmaciones de misma devolución | Un solo movimiento de retorno |
| AT-B04-11 | Venta de tenant A manipulando ID de producto de B | Denegada |
| AT-B04-12 | Falla transacción antes de completar venta | Sin venta finalizada ni descuento parcial |
| AT-B04-13 | Recálculo del saldo desde movimientos | Coincide con stock disponible |
| AT-B04-14 | Cobrar dos veces la misma consulta referenciada | Bloqueo/advertencia y corrección autorizada |

## 10. Decisiones funcionales resueltas y puertas legales/técnicas

- **D-B04-01 (CERRADO V1):** servicios no inventariables y pago manual aceptados (DEC-066 en materia de pagos).
- **D-B04-02 (CERRADO V1):** registrar ventas presenciales exige rol cajero; el administrador puede acumularlo expresamente, conforme a ACT-001.
- **D-B04-03 (RESUELTO):** DEC-065 admite devoluciones por línea/cantidad, con inspección física.
- **G-FISCAL:** antes de ventas reales y emisión de comprobantes, decidir integración con SUNAT o un proceso externo conforme; **la constancia interna no equivale a comprobante fiscal**.

**Condición de cierre:** una única fuente de movimiento de stock por evento, atomicidad, auditoría, acceso por tenant y resultados reproducibles de concurrencia previstos.

## 11. Cierre funcional V1 — ventas, cobros y devoluciones

**DEC-066 (aprobación explícita):** los pagos se registran manualmente como efectivo, transferencia u otro medio informado; un usuario comercial autorizado valida su recepción, **sin afirmar comprobación bancaria automática**. Las constancias internas deben indicar **«Documento interno de demostración; no es boleta ni factura electrónica SUNAT»**. No emitir numeración ni denominaciones que suplanten comprobantes tributarios.

**Permiso estricto de caja:** para confirmar una venta presencial se exige rol **cajero**; si el administrador desea operar la caja, deberá tener también el rol cajero. Esta regla mantiene mínimo privilegio y la acumulación de roles autorizada en ACT-001. El administrador sin rol cajero puede gestionar inventario, productos, pedidos web y las cancelaciones comerciales que le correspondan, pero no usar la interfaz POS para efectuar una venta ordinaria.

**Inventario y devoluciones:** un ajuste manual requiere rol administrador; una devolución comercial autorizada por cajero/admin genera una reposición ordinaria solo cuando las unidades salieron antes del pedido y volvieron físicamente aptas. En ventas no despachadas/sin salida física, una anulación válida puede reponer el descuento una sola vez. Las devoluciones por producto/cantidad se limitan a lo efectivamente vendido menos lo previamente devuelto; las unidades no aptas nunca incrementan disponible. Las correcciones excepcionales requieren autorización del administrador.

**Servicios sin stock:** venta de consulta o grooming como línea de servicio no inventariable, opcionalmente referenciada a una atención real, sin movimiento de producto y con control de cobro duplicado. El cumplimiento de atención y el pago son eventos distintos.

**Control fiscal:** V1 académica demuestra registro de ventas con datos sintéticos. Operación comercial real requiere definir y comprobar emisión tributaria por mecanismo legal adecuado (externo o integración posterior), junto con tratamiento de devoluciones y notas de crédito cuando apliquen.
