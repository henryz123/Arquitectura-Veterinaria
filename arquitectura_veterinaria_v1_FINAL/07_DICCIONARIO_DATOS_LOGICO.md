# Anexo — Diccionario lógico y contratos entre dominios V1.0

**Estado:** propuesta lógica; **no** es un diseño físico SQL, ERD definitivo ni elección de tecnología.

## 1. Agregados, propietarios y cardinalidades

| Agregado lógico | Entidades | Relación e invariantes principales |
|---|---|---|
| Organización SaaS (Bloque 1) | Veterinaria, Registro, PerfilComercial, Suspensión, ReservaRUC, Reclamación | Una veterinaria = una sede; RUC opcional, único entre asociaciones vigentes; reservas 24 h |
| Identidad (Bloque 1) | CuentaSaaS, CuentaTrabajador, CuentaCliente, Rol, Invitación, Sesión, TokenTemporal | Cada cuenta está tipada y, salvo global SaaS, circunscrita a un tenant; mismo correo entre cuentas permitido según contexto |
| Propietarios (B02) | Propietario, Vinculación, Transferencia, HistorialResponsable | 1 propietario tiene N mascotas; mascota tiene exactamente un principal vigente; vinculación verificada |
| Mascota/Historial (B02) | Mascota, Episodio, Vacunación, Receta, Adenda | Mascota tiene N episodios; solo veterinario edita datos clínicos; adendas no borran episodios |
| Agenda (B03) | Servicio, Horario, Bloqueo, Solicitud, Propuesta, Reserva, AtenciónGrooming | Solicitud N propuestas, máximo una reserva confirmada vigente; profesional no solapado |
| Catálogo y stock (B04) | Producto, StockActual, MovimientoStock, Ajuste | Producto 1 stock vendible por tenant; saldo y movimientos consistentes, no negativos |
| Caja (B04) | Venta, LíneaVenta, PagoManual, DevoluciónPOS | Una venta N líneas; snapshot monetario, descuento una vez y devoluciones compensatorias |
| Web (B05) | Carrito, Pedido, LíneaPedido, PropuestaCambio, Entrega, Cancelación, Devolución | Pedido 1 tenant y 1 comprador; N líneas, decremento al confirmar, entrega sin nuevo decremento |
| Transversal (B06) | RegistroAuditoría, Notificación, EventoOperación | Auditoría con tenant/actor/objeto; notificación no fuente de verdad |

## 2. Restricciones lógicas esenciales

1. Cada recurso identificable de una veterinaria porta su `tenant_id`; asociaciones fuertes requieren mismo tenant. Referenciar solo ID sin validar tenant es insuficiente.
2. Las cuentas de cliente son independientes por tenant, con correo normalizado único **en su contexto**. Una cuenta de trabajador pertenece a un único tenant, pero un correo puede aparecer en cuentas laborales de varios tenants.
3. El correo administrativo **registrante inicial** no puede registrar dos veterinarias en V1; esto no bloquea que sea invitado posteriormente como trabajador en otra.
4. RUC: 11 dígitos si se proporciona, validación adecuada definida más tarde; control de unicidad para asociaciones vigentes y reservas temporales no vencidas, con excepciones de disputas autorizadas. No interpretar RUC como identificador primario irreversible de tenant.
5. HistorialResponsable: índices temporales no superpuestos, exactamente un principal actual. Transferencia atómica.
6. CuentaCliente↔Propietario: no vincular cuentas ajenas al tenant ni dos personas a un registro privado por coincidencia de correo/nombre.
7. Episodio: autor veterinario, mascota del tenant, finalización con fecha y contenido; rectificación vinculada al episodio original. Referencia de cita opcional.
8. Agenda: evitar traslapes `[inicio,fin)` por empleado en todas las categorías de servicio. Concurrencia verificada al confirmar; alternativas no reservan cupo.
9. Stock: `cantidad_vendible >= 0`, unicidad por (tenant, producto); solo movimientos explícitos alteran saldo, con referencia única por evento idempotente.
10. Pedido: idempotency-key/ticket de operación único por cuenta y petición; líneas y tarifas snapshoteadas; total = suma líneas + tarifa envío; monedas no mezcladas.
11. Cantidad devuelta por ítem <= unidades realmente descontadas − unidades previamente restituidas; artículos dañados no incrementan stock vendible.
12. Las transiciones de estado inválidas deben rechazarse. Auditoría no se borra como efecto de cancelación o suspensión.
13. UTC para instantes de eventos; representación de agenda en hora local `America/Lima`. La hora propuesta se almacena con referencia a zona/hora local para reconstrucción exacta.
14. Importes PEN mediante decimal fijo o unidades menores enteras; redondeo documentado a dos decimales por línea/importe total antes de implementación.
15. Eventuales archivos/imágenes de productos tienen validación de tipo, tamaño y acceso; documentos de identidad no se almacenan como evidencia de vinculación.

## 3. Glosario que no debe mezclarse

| Término | Qué significa | Qué **no** significa |
|---|---|---|
| Veterinaria | Tenant SaaS independiente | Sucursal de tenant mayor |
| Propietario | Persona responsable de mascota dentro del tenant | Cuenta digital obligatoria |
| Cuenta cliente | Identidad digital por veterinaria | Historia clínica editable |
| Mascota | Paciente/animal con responsable principal | Pedido/cliente indistinto |
| Solicitud de cita | Propuesta pendiente de confirmación | Cupo reservado automáticamente |
| Cita confirmada | Intervalo y profesional asignados | Consulta clínica completada |
| Atención clínica | Episodio registrado por veterinario | Factura o venta automática |
| Servicio grooming completado | Prestación registrada por encargado | Historia clínica veterinaria |
| Stock vendible | Unidades disponibles para nuevas ventas | Unidades ya descontadas en pedido confirmado |
| Carrito | Selección temporal | Pedido con garantía de stock |
| Pedido pendiente | Solicitud comercial online | Venta confirmada con stock descontado |
| Pedido confirmado | Operación de personal que descuenta stock | Pago confirmado o entrega física |
| Entregado | Estado logístico | Cobro verificado automáticamente |
| Devuelto apto | Artículo físicamente recibido e inspeccionado | Solicitud de devolución sin mercancía recibida |
| Constancia interna | Resumen de operación de SaaS | Comprobante de pago tributario SUNAT |

## 4. Pares de invariantes que deben probarse juntos

- **Pet owner & appointment:** solo el responsable digital autorizado solicita cita de su mascota; una transferencia antes de confirmar puede invalidar la facultad del solicitante.
- **Appointment & clinical:** completar agenda no finaliza historia; la consulta creada puede referenciar cita, sin duplicar la atención.
- **Grooming & POS:** prestación y cobro se relacionan pero son transacciones distintas.
- **Product & order:** nombre y precio actual vs snapshot histórico.
- **POS & online:** misma autoridad de stock, diferentes causas de movimiento.
- **Cancellation & inventory:** decisión comercial + movimiento compensatorio consistente, siempre a cantidad real.
- **Suspend & authentication:** revocar acceso de tenant sin tocar otros.
- **Admin SaaS & tenant:** puede suspender/reclamar/recuperar en condiciones excepcionales, no explorar pacientes ni ventas.

## 5. Diseño de integridad posterior (no decidido)

Antes de decidir motor de persistencia, arquitectura física y migraciones, comparar mecanismos para: claves compuestas, foreign keys que incluyan tenant, unicidad de movimientos/idempotency keys, restricción de un propietario vigente, exclusión de intervalos de agenda, bloqueo para stock, cifrado/retención de PII, audit log inmutable, políticas de borrado lógico y restauración.

No inventar esquemas finales ni prometer atomicidad sin seleccionar y probar el mecanismo real.

## Estado de cierre

**Diccionario lógico funcional V1 consolidado.** No equivale a esquema SQL ni ADR de persistencia. Se requiere diseñar PK/FK, unicidad de RUC, tenant y exclusiones de agenda en fase arquitectura.
