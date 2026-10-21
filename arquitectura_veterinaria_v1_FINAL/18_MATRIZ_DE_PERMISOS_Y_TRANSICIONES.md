# 18 — Matriz transversal de permisos y transiciones V1

**Estado:** línea base funcional. **Principio:** prohibición por defecto, roles acumulables **dentro de tenant**, administración SaaS aislada.

## Permisos por operación

| Operación | SaaS admin | Admin vet | Recepción | Veterinario | Cajero | Grooming | Cliente |
|---|---|---|---|---|---|---|---|
| Listar/suspender/reactivar organizaciones | Sí | No | No | No | No | No | No |
| Expediente excepcional RUC / admin vet | Sí, expediente acotado | Solo solicita según autorización | No | No | No | No | No |
| Invitaciones, roles, desactivación | No ordinariamente | Sí | No | No | No | No | No |
| Vincular registro presencial / transferir titular | No | Sí | Sí | Solo rol adicional | No | No | Solicita |
| Crear/modificar episodio clínico | No | Solo si posee rol vet | No | Sí | No | No | No |
| Leer historia clínica completa | No | Solo si posee rol vet | No | Sí | No | No | No |
| Confirmar/reprogramar cita clínica | No | Sí | Sí | Solo rol adicional | No | No | Responde propuesta |
| Confirmar grooming, asignar encargado | No | Sí | Sí | Solo rol adicional | No | No | Responde propuesta |
| Completar grooming | No | Solo rol grooming adicional | No | Solo rol grooming adicional | No | Sí | No |
| Editar catálogo, precio, ajustar inventario | No | Sí | No | No | No | No | No |
| Registrar venta presencial POS | No | Solo rol cajero adicional | No | No | Sí | No | No |
| Confirmar/gestionar pedido web | No | Sí | No | No | Sí | No | Solicita compra |
| Gestionar cancelación comercial autorizada | No | Sí | No | No | Sí | No | Solicita si confirmado |
| Aprobar restitución excepcional de stock | No | Sí | No | No | No | No | No |
| Consultar operaciones propias | No | Según roles y tenant | Agenda/propietarios básicos | Su agenda/episodios tenant | Caja/pedidos tenant | Tareas propias | Solo sus solicitudes/datos básicos |

## Invariantes de estados y transiciones

- **Tenant:** PENDIENTE_VERIFICACION → PENDIENTE_CONTRASEÑA → ACTIVO ↔ SUSPENDIDO solo por administrador SaaS autorizado; disputa RUC impide reactivación sin resolución.
- **Mascota:** responsable principal único, historial de transferencias; cliente anterior pierde permisos vigentes de mascota; no sobrescribe historia clínica.
- **Cita/grooming:** PENDIENTE → CONTRAPROPUESTA → CONFIRMADA solo con aceptación y disponibilidad (o PENDIENTE → CONFIRMADA si se acepta horario original); CANCELADA/INASISTENCIA/COMPLETADA como terminales operativas auditables; contrapropuesta no ocupa intervalo. En confirmado, modificación de horario exige aceptación.
- **Producto:** disponible >=0; solo movimientos únicos identificados alteran saldo.
- **POS:** BORRADOR → COMPLETADA (movimiento de stock único) → ANULADA o DEVOLUCION_PARCIAL/DEVUELTA mediante eventos compensatorios, nunca borrado.
- **Pedido:** PENDIENTE/EN_REVISION/CAMBIO_PROPUESTO sin stock; CONFIRMADO descuenta stock exactamente una vez; preparación/recojo/reparto/entrega no lo modifica. Sin despachos parciales.
- **Pago:** independiente de estado del pedido; declaración cliente no es validación por cajero/admin.
- **Devolución:** cantidad por producto <= descontada - previamente devuelta; solo unidad físicamente recibida y apta vuelve a vendible; reintentos no duplican stock.

## Casos negativos obligatorios para pruebas futuras

1. Cliente A cambia ID en petición para leer paciente B: denegar.
2. Admin SaaS intenta consultar notas clínicas: denegar.
3. Recepción/grooming cambia diagnóstico: denegar.
4. Cajero intenta ajuste manual sin rol admin: denegar.
5. Admin vet sin rol cajero intenta confirmar venta presencial: denegar.
6. Empleado con dos roles intenta confirmar dos intervalos que se solapan: segunda denegada.
7. Ventas POS y web compiten por última unidad: exactamente una confirma; saldo no negativo.
8. Devolución parcial duplicada: movimiento de entrada ocurre una sola vez.
9. Desactivar último administrador o quitarle rol admin: denegar operación ordinaria.
10. Veterinaria suspendida: acceso bloqueado solo a tenant afectado; otras veterinarias continúan operativas.
11. Carrito y pedido pendiente: no descuentan ni reservan stock.
12. Pago marcado DECLARADO por cliente: no pasa a VALIDADO sin funcionario autorizado.
