# B05 — Tienda online, pedidos, entregas, cancelaciones y devoluciones | Especificación funcional V1.0 FINAL

**Estado:** CERRADO FUNCIONALMENTE para V1 académica. **Procesos:** PB-09, PB-12, PB-13. **Dependencias:** Bloque 1 (clientes), B04 (productos/stock/ventas), B06 (notificaciones). **Confirmado:** catálogo público por veterinaria activa; cliente autenticado para pedir; carro; recojo y delivery gestionado por veterinaria con tarifa fija; no pago online; no descuento de inventario hasta confirmación por cajero/administrador; si falta stock pasa a revisión; cliente cancela directamente pedido pendiente y solicita cancelar confirmado; productos despachados vuelven a stock solo tras recibir/inspeccionar.

## 1. Límites del comercio V1

- Cada veterinaria publica su propio catálogo. El carrito está acotado a **una sola veterinaria**.
- **No hay pasarela ni automatización de cobros**, tampoco integración de courier o rastreo GPS. La veterinaria coordina los pagos por sus medios y las entregas.
- Pedido online es una solicitud de compra; **confirmación de personal** es la operación que compromete físicamente el stock mediante su descuento.
- No se permiten envíos parciales de un pedido en V1 (DEC-065), para reducir complejidad. Se admiten devoluciones parciales por cantidades de línea (DEC-065).
- La gestión de comprobantes tributarios sigue pendiente: cualquier resumen/PDF interno se etiqueta **«constancia de pedido, no comprobante tributario»** [DEC-066].

## 2. Casos de uso

| ID | Caso de uso |
|---|---|
| CU-B05-01 | Consultar tienda/catálogo público de veterinaria |
| CU-B05-02 | Crear/modificar carrito local de una veterinaria |
| CU-B05-03 | Registrar pedido autenticado con modalidad de entrega |
| CU-B05-04 | Revisar, confirmar o dejar pedido en revisión |
| CU-B05-05 | Proponer cambios de pedido e incorporar aceptación del cliente |
| CU-B05-06 | Registrar validación manual de pago |
| CU-B05-07 | Preparar pedido y registrar retiro/entrega |
| CU-B05-08 | Cancelar pedido pendiente o solicitar cancelación confirmada |
| CU-B05-09 | Resolver cancelación confirmada y restituir si corresponde |
| CU-B05-10 | Registrar devolución total o parcial y aptitud física |
| CU-B05-11 | Consultar estado e historial de pedidos propios |
| CU-B05-12 | Configurar delivery de tarifa fija y ámbito de cobertura |

## 3. Datos funcionales

**Carrito:** contenido temporal por tenant y cuenta/navegador; no representa pedido, no modifica inventario y no garantiza precio ni existencia.

**Pedido:** tenant, número tenant-local, comprador autenticado, fecha, dirección de entrega para delivery o recojo, teléfono de coordinación, estado operativo, estado de pago separado, totales congelados, tarifa delivery congelada, moneda PEN, notas operativas no sensibles y versión para concurrencia.

**LineaPedido:** producto del tenant, cantidad entera >0, snapshot nombre/SKU/precio unitario, subtotal calculado. No permitir productos inactivos al registrar la solicitud, salvo que solo hayan sido despublicados después de una compra anterior.

**ModoEntrega:** `RECOJO` o `DELIVERY`. `DELIVERY`: dirección dentro de ámbito de atención **según la regla simple definida por la veterinaria** (área descriptiva/zonas preconfiguradas, sin geolocalización obligatoria ni mapas). `RECOJO`: dirección fija de la única sede.

**PropuestaModificacionPedido:** pedido, cambios de cantidad/precio/entrega, motivo, autor staff, estado propuesta/aceptada/rechazada/vencida; historial. Cambios materiales exigen aceptación previa del cliente. No se obliga aceptar precio nuevo por cambio de catálogo.

**Entrega:** modalidad, receptor, fecha/hora de recogida o entrega, trabajador registrante, constancia operativa; no GPS/firma avanzada.

**PagoManual:** medio declarado por cliente o personal, referencia opcional no sensible, personal validador, estado registrado/validado; no implica consulta bancaria automática.

**Cancelación:** petición, origen cliente/personal, motivo, estado, resolutor, consecuencia stock, consecuencia de pago manual, fecha y audit trail.

**Devolucion/LineaDevuelta:** orden + artículo + cantidad devuelta, físicamente recibido sí/no, apto/no apto, observación, responsable; restitución de unidades aptas solo una vez.

## 4. Estados separados obligatoriamente

### 4.1 Estado de pedido (operación/fulfillment)

| Estado | Quién / evento | Consecuencia |
|---|---|---|
| `PENDIENTE` | Cliente crea pedido | Sin descuento/reserva de stock |
| `EN_REVISION` | Personal detecta insuficiencia o inconsistencia | Sin descuento; se coordina solución |
| `CAMBIO_PROPUESTO` | Staff ofrece ajustar contenido/importe/entrega | Sin descuento; cliente decide |
| `CONFIRMADO` | Cajero/admin verifica stock, condiciones y confirma | **Descuento atómico de stock una vez** |
| `EN_PREPARACION` | Personal inicia preparación | Sin nuevo descuento |
| `LISTO_PARA_RECOJO` | Personal informa disponibilidad para retiro | Sin nuevo descuento |
| `EN_REPARTO` | Personal despacha pedido delivery | Sin nuevo descuento |
| `ENTREGADO` | Cliente recoge o repartidor entrega, registro manual | Cierre logístico; sin nuevo descuento |
| `CANCELACION_SOLICITADA` | Cliente pide cancelar confirmado | No se restituye stock automáticamente |
| `CANCELADO` | Cancelación autorizada | Si no despachado y recuperable, compensar descuento una vez; si despachado, esperar devolución física apta |
| `DEVOLUCION_EN_PROCESO` | Se gestiona devolución tras entrega/despacho | Sin reposición automática |
| `CERRADO_CON_DEVOLUCION` | Devolución validada y consecuencias registradas | Ajustes de unidades según inspección |

**Restricción de transiciones:** no marcar `ENTREGADO` una solicitud `PENDIENTE`; no marcar `CONFIRMADO` sin stock; no devolver al estado `PENDIENTE` después de descontar sin compensación. Para pedidos con devoluciones parciales, conservar `ENTREGADO` como estado principal y representar la devolución en un subproceso específico; la nomenclatura exacta será resuelta en diseño de transiciones antes de codificar.

### 4.2 Estado de pago separado

`NO_DECLARADO` → `DECLARADO` → `VALIDADO` / `RECHAZADO`. Según política de la veterinaria puede registrarse cobro al recojo o delivery. **El sistema no afirma pago recibido porque un cliente marque «ya pagué»**.

### 4.3 Estado de devolución

`SOLICITADA` → `AUTORIZADA`/`RECHAZADA` → `RECIBIDA` → `INSPECCIONADA_APTA` o `INSPECCIONADA_NO_APTA` → `CERRADA`.

## 5. Reglas críticas

1. Carrito/pedido pendiente nunca baja ni bloquea stock.
2. Cajero o administrador de veterinaria confirma el pedido. Stock suficiente se valida **para todas las líneas** y se descuenta en una operación indivisible; si una falta, no confirmar parcialmente.
3. El stock disponible se comparte con B04/POS y no puede quedar negativo.
4. Un pedido solo descuenta **una vez** por su primera confirmación válida. Ni pago, preparación ni entrega hacen descuentos adicionales.
5. Al crear pedido se congela el precio/cantidad y la tarifa de delivery conocida por el cliente [RO-06]; revisiones materiales antes de confirmar requieren aceptación expresa. Si se cambia el producto o precio, no mutar silenciosamente la solicitud original.
6. En `EN_REVISION` se puede proponer reducir cantidades/cambiar línea, pero debe aceptarlo el cliente; luego se verifica nuevamente stock para confirmar.
7. Pedido pendiente cancelado directamente por el cliente **no tiene movimientos de stock**. Cliente con confirmado solicita cancelación, la veterinaria la resuelve.
8. Cancelar pedido confirmado **antes de despacho**: restituir una sola vez los artículos que efectivamente fueron descontados y no han salido físicamente, en la misma operación consistente que cancela. Si el pedido ya salió a entrega, no reponer hasta recibir y comprobar aptitud, aunque el estado lógico sea cancelado.
9. En devolución parcial [DEC-065], cantidad que vuelve a stock por línea <= cantidad descontada - restituciones previas. No son necesarias entregas parciales para soportar devoluciones parciales.
10. Pago y stock son dimensiones separadas: validar pago no modifica stock. Las devoluciones económicas se gestionan **manualmente** y se registran; no suponer reintegro bancario por un estado interno.
11. Configuración de tarifa fija **por veterinaria**, con cobertura decidida por el establecimiento; `RECOJO` sin costo de delivery. Tarifa congelada al registrar pedido.
12. Visibilidad: cliente solo pedidos propios; cajero/admin solo pedidos de su tenant; SaaS admin no ve detalles comerciales internos por rol global aislado.
13. Cancelaciones, reintentos y devoluciones generan auditoría. Nunca eliminar pedidos ni movimientos anteriores para «corregir» cifras.
14. Cambios de precio posteriores en catálogo no alteran pedidos anteriores.
15. La suspensión del tenant bloquea creación/confirmación/entrega online protegida, conservando su información y compromisos previos. La resolución de compromisos al reactivar debe gestionarse, no ejecutarse automáticamente.

## 6. Flujos principales y alternativos

### F-B05-01 — Pedido nuevo

Visitante navega catálogo por veterinaria → añade artículos a carrito sin reserva → al finalizar inicia sesión/verifica cuenta del tenant → confirma cantidades, dirección/recojo, tarifa fija y precio mostrado → crea pedido `PENDIENTE` con snapshot monetario y clave de idempotencia → recibe constancia interna/estado. **Error:** tenant suspendido, carrito con productos retirados o precios desactualizados; antes de enviar se exige actualizar y aceptar los cambios.

### F-B05-02 — Confirmación normal

Cajero/admin abre `PENDIENTE` → valida datos, dirección si delivery, precios congelados y stock de todas las líneas → confirma atómicamente el pedido y las salidas de stock → estado `CONFIRMADO` → notifica. Si falla una línea o hay competencia con venta POS, pasa a revisión sin descontar ninguna.

### F-B05-03 — Revisión por stock

Personal detecta insuficiencia → estado `EN_REVISION` → propone cantidades, cambio de productos o cancelación → cliente acepta/rechaza → tras aceptar se verifica stock y total nuevamente → solo si hay suficiente, confirmación. El sistema **no reserva** artículos mientras se negocia.

### F-B05-04 — Preparación, recojo y entrega

Pedido confirmado → personal marca preparación → si recojo, `LISTO_PARA_RECOJO` y posteriormente `ENTREGADO` tras registrar recepción; si delivery, `EN_REPARTO`, después `ENTREGADO` con receptor/fecha. El personal solo declara cumplimiento físico; no se confirma pago automáticamente. Pedido entregado no se puede eliminar.

### F-B05-05 — Cancelación predespacho

Cliente solicita cancelar pedido confirmado → cajero/admin revisa estado y condición física → autoriza cancelación → sistema evita nuevas acciones de despacho y registra restock una sola vez de cantidades que realmente permanecen en sede → comunica decisión → pago manual se ajusta por procedimiento comercial independiente.

### F-B05-06 — Cancelación tras despacho/devolución

Cliente solicita cancelar/devolver → personal registra trámite → si artículos ya salieron, stock **permanece descontado** → se registra recepción física por líneas y aptitud → solo unidades aptas son repuestas → unidades no aptas quedan fuera del stock vendible y bajo trazabilidad → se registra resultado económico manualmente. Repetir la confirmación de devolución no repone una segunda vez.

### F-B05-07 — Devolución parcial propuesta

Pedido entregado incluye 2 unidades A y 3 B. Cliente devuelve 1 unidad A. El personal registra 1 recibida y apta; stock A aumenta solo 1 y no se altera stock B. Si intenta devolver 2 unidades A adicionales, se rechaza porque excede lo vendido menos lo devuelto previamente.

## 7. Matriz de autorización

| Operación | Visitante | Cliente | Cajero | Admin vet | SaaS admin |
|---|---|---|---|---|---|
| Navegar catálogo activo | Sí | Sí | Sí | Sí | Solo catálogo público |
| Crear carrito sin stock | Sí | Sí | No necesita rol | No necesita rol | No necesita rol |
| Registrar pedido | No | Sí, cuenta verificada | Solo mediante cuenta cliente separada | Solo mediante cuenta cliente separada | No |
| Ver pedidos propios | No | Sí | Solo sus pedidos de cliente propios | Solo sus pedidos de cliente propios | No |
| Ver/confirmar pedidos de tenant | No | No | Sí | Sí | No |
| Cambiar precios/productos publicados | No | No | No | Sí | No |
| Registrar entrega/recogida | No | No | Sí | Sí | No |
| Cancelar pedido pendiente propio | No | Sí | Gestión autorizada | Gestión autorizada | No |
| Resolver cancelación confirmada | No | Solicita | Sí | Sí | No |
| Aprobar ajuste excepcional de stock | No | No | No | Sí | No |

## 8. Requisitos funcionales candidatos

| ID | Requisito |
|---|---|
| RF-B05-001 | Catálogo web público aislado por veterinaria activa |
| RF-B05-002 | Carrito sin reserva ni decremento de stock |
| RF-B05-003 | Registro de pedido con cuenta cliente verificada y snapshot monetario |
| RF-B05-004 | Métodos de entrega RECOJO y DELIVERY con tarifa fija local |
| RF-B05-005 | Validar disponibilidad global al confirmar todas las líneas |
| RF-B05-006 | Confirmación autorizada y decremento atómico/idempotente |
| RF-B05-007 | Enviar sin stock a revisión sin descuentos parciales |
| RF-B05-008 | Registrar propuestas de cambio y exigir aceptación del cliente |
| RF-B05-009 | Mantener estados de preparación, recojo, reparto y entrega |
| RF-B05-010 | Registrar pago manual independientemente de entrega/stock |
| RF-B05-011 | Cancelación directa de pedidos pendientes por cliente |
| RF-B05-012 | Petición de cancelación de confirmado resuelta por staff |
| RF-B05-013 | Restituir una vez stock predespacho bajo condiciones válidas |
| RF-B05-014 | Posponer restitución post-despacho a devolución física apta |
| RF-B05-015 | Registrar devoluciones parciales por línea sin exceder cantidades |
| RF-B05-016 | Mantener auditoría y snapshot de precios y tarifas |
| RF-B05-017 | Impedir exposición de pedidos de otros clientes/tenants |
| RF-B05-018 | Notificaciones de estados importantes sin confiar en entrega del email para persistir la transacción |
| RF-B05-019 | Bloquear operaciones protegidas de veterinarias suspendidas |
| RF-B05-020 | Evitar cobro/stock doble por reintentos o integración con POS |

## 9. Pruebas de aceptación

| ID | Escenario | Resultado |
|---|---|---|
| AT-B05-01 | Carrito con 5 unidades, stock 3 | No reserva ni descuenta; checkout deberá informar disponibilidad al confirmar |
| AT-B05-02 | Pedido recién creado, stock 5 | Stock sigue 5 |
| AT-B05-03 | Cajero confirma pedido de 2, stock 5 | Stock 3, un movimiento por pedido |
| AT-B05-04 | Repetir confirmación por timeout | Stock permanece 3 |
| AT-B05-05 | Compra POS ocupa última unidad simultáneamente | Una operación confirma, otra a revisión |
| AT-B05-06 | Un ítem insuficiente en pedido de 3 líneas | Ninguna línea se descuenta; pedido en revisión |
| AT-B05-07 | Cliente acepta contrapropuesta pero stock volvió a cambiar | No confirmar sin revalidar |
| AT-B05-08 | Precio cambia después de registro | Pedido preserva precio aceptado por cliente |
| AT-B05-09 | Cliente cancela pendiente | Cancelado, stock sin cambio |
| AT-B05-10 | Cancelación confirmada predespacho | Reposición una vez |
| AT-B05-11 | Reintento de cancelar confirmada | Sin reposición doble |
| AT-B05-12 | Pedido en reparto cancelado | Stock no vuelve sin devolución física apta |
| AT-B05-13 | Devolución de unidad dañada | Cero retorno a vendible |
| AT-B05-14 | Devolución parcial de 1 de 2 unidades | Solo +1 stock; otra unidad sigue vendida |
| AT-B05-15 | Intentar devolver más que vendido | Rechazado |
| AT-B05-16 | Delivery costo fijo cambia luego de solicitud | Pedido conserva tarifa snapshot |
| AT-B05-17 | Cuenta de cliente del tenant B consulta pedido A por ID | Denegado |
| AT-B05-18 | Cliente declara «pagado» sin revisión staff | Estado no puede ser VALIDADO |
| AT-B05-19 | Marcar entregado un pedido pendiente | Denegado |
| AT-B05-20 | Tenant suspendido, usuario intenta pagar/confirmar pedido | Bloqueado conforme a política, pedido histórico conservado |
| AT-B05-21 | Reporte suma POS y pedidos | No contabiliza el mismo evento dos veces |

## 10. Decisiones funcionales resueltas y puertas legales/técnicas

- **D-B05-01 (CERRADO V1):** conservar precios/tarifa aceptados; modificaciones materiales requieren nueva aceptación y validación de stock.
- **D-B05-02 (RESUELTO):** DEC-065 admite devoluciones parciales inspeccionadas, no envíos parciales.
- **D-B05-03 (RESUELTO):** DEC-066 fija cobros manuales y constancias no fiscales.
- **D-B05-04 (CERRADO V1):** cobertura de delivery mediante áreas textuales configurables y una tarifa fija para el área cubierta; sin mapas obligatorios.
- **D-B05-05 (CERRADO V1):** `ENTREGADO` registra entrega física y no supone pago recibido; mostrar estado de pago por separado.
- **G-FISCAL:** no emitir «boletas» tributarias falsamente; cumplir proceso fiscal externo o integrar legalmente antes de operación real.

**Condición de cierre:** ningún doble descuento ni doble restock, no hay venta cruzada de tenants, los estados logísticos y pagos son independientes y las devoluciones preservan historial.

## 11. Cierre funcional V1 — devoluciones parciales, despacho y pago

**DEC-065 (aprobación explícita):** se admiten devoluciones **parciales por línea y unidades**, tras recepción e inspección física, sin permitir **despachos parciales** de un pedido en V1. Un pedido confirmado se prepara para una única recogida/entrega. En devolución parcial, conservar líneas originales, cantidad comprada, cantidad devuelta, aptitud y restituciones; no superar la cantidad neta descontada. Una devolución de una unidad no modifica unidades de otra línea.

**Despacho:** el estado `EN_REPARTO` o `ENTREGADO` no reitera descuento. Después de despacho, una cancelación aprobada no devuelve stock por sí sola. Solo las unidades efectivamente recibidas y declaradas aptas se reincorporan una vez. Si un producto no es vendible, registrar devolución física y resultado económico sin aumentar el stock vendible.

**Cambio de pedido:** congelar productos, precios y tarifa de delivery al registrar la solicitud aceptada; modificar precio, cantidades o modalidad requiere nueva aceptación explícita del cliente y validación de stock antes de confirmar. La aceptación no reserva. Las confirmaciones son atómicas para todas las líneas.

**Pagos manuales:** `PENDIENTE`/`DECLARADO`/`VALIDADO`/`RECHAZADO` es eje diferente al estado del pedido. `ENTREGADO` significa cumplimiento logístico documentado aunque el estado del pago continúe pendiente; el panel exhibe ambas condiciones. La marca `VALIDADO` solo la establece cajero/admin autorizado. Reembolso económico se tramita fuera de pasarela, registrado, sin afirmar transferencia ejecutada automáticamente.

**Cobertura de delivery:** veterinaria configura zonas o áreas textuales simples y una tarifa fija única en PEN para su área admitida; sin geolocalización obligatoria. El cliente confirma dirección dentro de cobertura; la veterinaria valida manualmente si el caso requiere revisión. Recojo tiene tarifa de envío cero.

**Constancias:** si se genera constancia descargable, debe ser inequívocamente **no tributaria**. Uso con ventas reales está condicionado a proceso fiscal y privacidad verificados.
