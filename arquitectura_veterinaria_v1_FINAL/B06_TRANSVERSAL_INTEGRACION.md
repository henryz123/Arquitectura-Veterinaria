# B06 — Integración funcional, notificaciones, seguridad, consistencia y excepciones | Especificación funcional V1.0 FINAL

**Estado:** CERRADO FUNCIONALMENTE para V1 académica. **Objetivo:** conectar los Bloques 1 a 5 sin duplicar autoridad sobre datos, permisos ni transacciones. Este bloque es transversal, no un módulo inventado de funcionalidades ajenas al alcance.

## 1. Procesos transversales y casos de uso

| ID | Caso transversal |
|---|---|
| CU-B06-01 | Consultar panel operativo básico por veterinaria |
| CU-B06-02 | Emitir y consultar notificaciones de eventos aprobados |
| CU-B06-03 | Consultar auditoría autorizada de acciones sensibles |
| CU-B06-04 | Registrar y resolver fallos de notificación sin deshacer transacción |
| CU-B06-05 | Verificar consistencia de agenda, identidades y stock |
| CU-B06-06 | Suspender tenant y bloquear operaciones transversalmente |
| CU-B06-07 | Reactivar tenant sin reejecutar operaciones históricas |
| CU-B06-08 | Generar datos sintéticos de demostración aislados |
| CU-B06-09 | Gestionar errores, concurrencia e idempotencia en flujos críticos |

**Panel básico propuesto:** admin vet observa citas próximas, solicitudes pendientes, pedidos en revisión, productos con stock bajo y servicios del día dentro de su tenant. No es un dashboard de business intelligence complejo. Veterinario ve su agenda y pacientes por atender; recepción agenda; cajero pedidos/ventas; grooming tareas; cliente sus solicitudes. Administrador SaaS ve **solo los siete campos administrativos previamente aprobados**, no indicadores privados internos.

## 2. Matriz de propiedad de datos — una fuente autoritativa

| Dato/regla | Contexto dueño | Contextos consumidores | Restricción |
|---|---|---|---|
| Tenant y estado activo/suspendido | Bloque 1 | Todos | Suspendido bloquea operaciones protegidas |
| Cuentas, roles, sesiones y cliente/empleado | Bloque 1 | Todos | No duplicar identidad; referenciar por ID y tenant |
| Propietario, mascota, vinculación y responsable | B02 | B03/B05 | B03 usa mascota autorizada; B05 cliente comprador, no historia clínica |
| Historia clínica, vacunas y recetas | B02 | B03 (solo referencia de consulta) | Solo rol veterinario edita |
| Citas y disponibilidad del personal | B03 | B02 (atención), B04 (servicio cobrable) | Cita no equivale a consulta ni pago |
| Producto, precio actual y stock vendible | B04 | B05 | B04 es autoridad de stock, no mantener saldo alternativo editable en web |
| Venta POS y movimientos | B04 | B06 (reportes) | Movimiento POS único |
| Pedido online, snapshot, entrega, devolución | B05 | B04 (movimientos autorizados) | Pedido confirmado descuenta una vez, no en entrega |
| Historial de auditoría | Infraestructura transversal | Actores autorizados | No registrar secretos ni contenido clínico indiscriminadamente |
| Notificaciones | Servicio transversal | Usuarios destinatarios | No es fuente de verdad para estados de negocio |

**Regla de integración:** consumir un identificador ajeno no significa duplicar escrituras sobre el dato dueño. Las acciones cruzadas pasan por la regla de negocio y la autorización del contexto responsable.

## 3. Contratos de negocio entre módulos (sin elegir API todavía)

1. `PetOwnershipValidated(tenant, account, pet)` -> B03 permite solicitar servicio; si cambia dueño durante proceso, revalidar antes de nuevas acciones sensibles.
2. `AppointmentConfirmed(tenant, pet, professional, start, end, id)` -> B02 puede asociar episodio, B06 notifica; el evento de agenda no crea nota clínica automáticamente.
3. `AppointmentCompleted` / `GroomingCompleted` -> B04 puede ofrecer referencia para una línea de servicio, no registrar venta automática sin cajero.
4. `InventoryDecrementRequested(tenant, product lines, source, operationId)` -> solo B04 valida y persiste movimientos; B05 confirma **únicamente** si recibe resultado consistente de stock.
5. `OnlineOrderConfirmed` -> B06 notifica y B05 inicia preparación; **no** genera un nuevo movimiento de stock en POS.
6. `OrderCancelledBeforeDispatch` -> B04 valida restock idempotente por origen; B05 guarda resolución.
7. `ReturnInspected(tenant, order, line, qty, resellable)` -> solo el stock vendible del artículo apto se incrementa, una sola vez.
8. `TenantSuspended` -> todos los contextos bloquean escrituras protegidas y lecturas privadas de ese tenant salvo recuperación administrativa autorizada. **No** borrar entidades.
9. `TenantReactivated` -> restablece acceso autorizado, pero no reenvía pedidos, pagos, citas ni movimientos en masa.
10. `WorkerDeactivated` / `RolesChanged` -> permisos y sesiones actualizados; B03 señala citas asignadas para revisión de agenda.

Estos nombres son **eventos de negocio conceptuales**, no una obligación de usar broker de mensajes ni microservicios. En un monolito modular pueden implementarse mediante casos de uso y notificaciones transaccionales.

## 4. Idempotencia, concurrencia y consistencia

| Escenario | Unidad que debe permanecer consistente | Resultado que debe impedirse |
|---|---|---|
| Dos personas confirman misma franja | Reserva + profesional + período | Dos reservas incompatibles |
| POS compite con confirmación online | Stock + operación confirmada + movimiento | Stock negativo, descuento parcial |
| Cliente reintenta crear pedido | Identidad cliente + petición lógica | Pedidos duplicados |
| Cajero reintenta confirmar pedido | Estado confirmado + movimiento inventario | Segundo descuento |
| Usuario cancela pedido dos veces | Resolución cancelación + movimiento compensador | Segundo restock |
| Devolución revisada dos veces | Cantidades devueltas por línea + movimiento | Reposición mayor a vendido |
| Dos transferencias de mascota | Responsable único + historial de periodos | Doble propietario vigente |
| Revocación de trabajador | Cuenta/roles/sesiones | Operaciones posteriores a revocación |
| Activar veterinaria con RUC disputado | Reserva RUC + tenant + cuenta admin | Dos asociaciones válidas de RUC |

**Estrategia recomendada de diseño posterior:** invariantes en backend y BD, transacciones ACID en el mismo dominio, restricciones únicas compuestas con tenant, bloqueos/versión optimista en carreras, IDs de operación/idempotency-key, y mecanismo confiable de entrega de notificaciones (patrón outbox o alternativa equivalente). No imponer estas técnicas sin evaluar stack y ADR.

## 5. Autorización, datos personales y confidencialidad

- Resolver `tenant_id` a partir del contexto autenticado y de la ruta de veterinaria validada, **nunca** aceptar arbitrariamente tenant enviado en la carga del cliente.
- Validar en servidor rol efectivo, estado de organización, tipo de cuenta, propiedad del recurso y estado de operación. Principio denegar por defecto.
- Cliente no puede consultar mascota de un tercero por cambiar un ID; cajero no puede editar clínica; groomer no puede consultar historia clínica completa; administrador SaaS solo campos administrativos generales.
- Notificaciones no exponen diagnósticos completos, recetas, documentos personales ni tokens. Recordatorio puede decir «Tiene una cita» y fecha, sin detalles médicos innecesarios.
- Registro de auditoría de acciones críticas: actor, rol, tenant, acción, objeto, fecha, resultado, referencia de operación, motivo cuando corresponda; no registrar contraseñas, tokens, número completo de credenciales, ni copiar narrativa clínica sin necesidad.
- No usar documentos de identidad escaneados para verificar vinculaciones presenciales; se registra el verificador y resultado, de acuerdo a DEC-030.
- No se proporciona mecanismo de exportación clínica completo a clientes hasta que se aprueben reglas de acceso y privacidad (PROP-V1-01).
- Considerar cifrado en transporte, protección de datos en reposo, protección de copias, expiración de sesiones y validación de datos como NFR a concretar antes de infraestructura.
- **Puerta legal:** cumplir las obligaciones aplicables de protección de datos en Perú, con políticas de información/consentimiento y gestión de derechos que se definan con el análisis legal correspondiente. Referencia normativa a revisar: Ley 29733 y reglamento DS 016-2024-JUS. Este documento no certifica cumplimiento.

## 6. Notificaciones mínimas [RO-08]

| Evento | Destinatario | Contenido permitido |
|---|---|---|
| Veterinaria activada | Administrador inicial | Confirmación y acceso |
| Cuenta/invitación/verificación | Titular correspondiente | Enlace temporal de un solo uso |
| Cita solicitada | Recepción/admin dentro del tenant | Solicitud y horario propuesto |
| Contrapropuesta enviada | Cliente | Hora propuesta y respuesta requerida |
| Cita confirmada/reasignada/cancelada | Cliente y profesional pertinente | Fecha, estado y asignación, sin datos clínicos completos |
| Grooming confirmado/completado | Cliente/encargado autorizado | Estado y hora |
| Pedido creado/confirmado/revisión | Cliente y personal comercial pertinente | Número y estado de pedido |
| Pedido listo/despachado/entregado | Cliente | Estado logístico |
| Cancelación/devolución resuelta | Cliente | Resolución comercial |
| Suspensión/controversia de RUC | Representante autorizado | Estado administrativo mínimo, sin datos reclamante |

**Canal V1 propuesto:** notificación dentro de cuenta + correo transaccional donde haya correo verificado. Fallo de correo queda reintentable/registrado sin revertir confirmación de cita o pedido. No WhatsApp/SMS como requerimiento obligatorio; proveedor se elegirá más tarde. Si un correo contiene link sensible, almacenar solo token seguro y nunca incluirlo en logs.

## 7. Reportes mínimos (académicos, sin BI avanzada)

- Admin veterinaria: citas por estado/día, atenciones clínicas contabilizadas como episodios (no contenido diagnóstico), pedidos en revisión, ventas registradas por período, existencias bajas, grooming completados.
- Veterinario: citas propias pendientes/confirmadas y resumen de consultas realizadas, sin exportación de datos ajenos.
- Recepción: solicitudes, contrapropuestas, citas y grooming por día, propietarios sin vínculo digital.
- Cajero: pedidos a confirmar, caja/ventas del día, devoluciones pendientes.
- Groomer: reservas asignadas y pendientes de ejecutar.
- Cliente: mascotas básicas, citas, reservas y pedidos propios.
- Admin SaaS: **solo** información administrativa aprobada; no métricas de ventas, pacientes ni empleados internos.
- Los totales comerciales deben diferenciar `ordenes_recibidas`, `pedidos_confirmados`, `entregas`, `ventas_POS`, `pagos_validados`, `devoluciones` y no sumarse como si fueran la misma métrica.

## 8. Errores y respuestas funcionales

| Código conceptual | Origen | Respuesta esperada |
|---|---|---|
| `TENANT_SUSPENDED` | Tenant suspendido | Denegar operación; conservar estado previo |
| `FORBIDDEN_RESOURCE` | Falta de rol/relación | No revelar recurso privado |
| `PET_OWNER_CHANGED` | Cliente ya no responsable | Detener operación; requerir revisión |
| `SLOT_UNAVAILABLE` | Agenda ocupada | No confirmar; sugerir alternativa |
| `INVENTORY_INSUFFICIENT` | Stock no suficiente | No descontar; pedido a revisión |
| `STALE_PROPOSAL` | Contraoferta antigua | No confirmar; solicitar revisión |
| `ORDER_STATE_CONFLICT` | Transición no válida | Rechazar sin nuevos movimientos |
| `ALREADY_PROCESSED` | Reintento idempotente | Responder resultado anterior sin duplicar |
| `RETURN_NOT_INSPECTED` | Restock indebido | No restituir hasta validar retorno físico |
| `INVALID_LINK` | Invitación/recuperación vencida | No habilitar acceso |

No exponer trazas de stack, correos de terceros ni detalles internos en mensajes al público.

## 9. Cumplimiento fiscal: DEC-066 aprobada y puerta legal externa

La ley peruana y SUNAT pueden obligar a emitir comprobantes electrónicos de las ventas reales. **El registro interno de un pedido o venta en este SaaS no sustituye un comprobante tributario**. Para demostración académica con datos sintéticos, el sistema puede operar con constancias internas. Para usarlo en un negocio real: (A) integrar SUNAT/operador autorizado, o (B) documentar y validar un flujo externo de facturación al margen de este SaaS; nunca afirmar que ya emite boletas/facturas oficiales.

**Fuentes de consulta:**
- https://cpe.sunat.gob.pe/informacion_general/obligados_cpe
- https://www.gob.pe/institucion/anpd/normas-legales/6554453-n-016-2024-jus
- https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html

## 10. Requisitos transversales candidatos

| ID | Requisito |
|---|---|
| RF-B06-001 | Bloquear operaciones protegidas en veterinaria suspendida sin borrar datos |
| RF-B06-002 | Enforzar tenant/rol/propiedad de recurso en cada operación |
| RF-B06-003 | Unificar contrato de stock entre POS y ecommerce, sin movimientos duplicados |
| RF-B06-004 | Aplicar idempotencia a operaciones críticas |
| RF-B06-005 | Revalidar disponibilidad bajo concurrencia en agenda y stock |
| RF-B06-006 | Registrar auditoría de operaciones sensibles sin secretos |
| RF-B06-007 | Generar notificaciones mínimas y conservar estado de entrega |
| RF-B06-008 | No revertir transacciones de negocio por fallo de email posterior |
| RF-B06-009 | Proporcionar paneles mínimos por rol/tenant |
| RF-B06-010 | Producir reportes básicos sin doble conteo comercial |
| RF-B06-011 | Operar montos monetarios de precisión exacta en PEN |
| RF-B06-012 | Presentar fechas en zona America/Lima y almacenar instantes normalizados |
| RF-B06-013 | Separar claramente datos sintéticos de la información real |
| RF-B06-014 | No llamar comprobante fiscal a constancias internas |
| RF-B06-015 | Conservar historial y autoría en operaciones clínicas y comerciales |
| RF-B06-016 | No ejecutar operaciones irreversibles sin autorización y trazabilidad |

## 11. Pruebas transversales de aceptación

| ID | Escenario | Resultado |
|---|---|---|
| AT-B06-01 | Tenant A intenta leer mascota de B usando ID conocido | Denegado |
| AT-B06-02 | Tenant B intenta confirmar pedido de A | Denegado; A no cambia |
| AT-B06-03 | Admin SaaS consulta historia clínica con rol global | Denegado |
| AT-B06-04 | Cambio de precio tras pedido existente | Snapshot no cambia |
| AT-B06-05 | Web y POS compiten por unidad final | Una operación, stock >=0 |
| AT-B06-06 | Correo de confirmación falla después de reservar cita | Cita se mantiene; notificación en error/reintento |
| AT-B06-07 | Reintento del mismo comando por caída de red | Resultado único sin duplicación |
| AT-B06-08 | Cliente transferido intenta ver mascota previa | Denegado control actual; historial propio conservado |
| AT-B06-09 | Groomer consulta narrativa clínica ajena | Denegado |
| AT-B06-10 | Veterinaria suspendida conserva datos y otras veterinarias funcionan | Sí |
| AT-B06-11 | Fecha de visita mostrada en hora peruana | Se representa correctamente en America/Lima |
| AT-B06-12 | Reporte cuenta pedido online + entrega del mismo pedido | No duplica ingresos |
| AT-B06-13 | Auditoría de cambio de propietario | Guarda actor/instante/resultado sin adjuntar copia de DNI |
| AT-B06-14 | Demo usa solo pacientes sintéticos | Ningún paciente real incluido en semillas de demostración |
| AT-B06-15 | Constancia interna de venta | No afirma emisión oficial SUNAT |
| AT-B06-16 | Desactivar trabajador de tenant A que también existe en B | B permanece operativo |
| AT-B06-17 | Fallo de proceso entre movimiento y cambio de estado de pedido | No deja descuentos huérfanos |
| AT-B06-18 | Recuperar backup de entorno de prueba | Integridad de tenant e historial comprobable; prueba futura, no ejecutada |

## 12. Matriz de cobertura de procesos aprobados

| PB ACT-001 | Bloque / dueño del diseño | Cobertura |
|---|---|---|
| PB-01 Registro SaaS | Bloque 1 | Ya aprobado funcionalmente con pendientes |
| PB-02 Trabajadores/roles | Bloque 1 | Ya aprobado funcionalmente con pendientes |
| PB-03 Propietarios/mascotas | B02 | Cubierto |
| PB-04 Citas clínicas | B03 | Cubierto |
| PB-05 Historia clínica | B02 | Cubierto |
| PB-06 Baños/peluquería | B03 | Cubierto |
| PB-07 Productos/inventario | B04 | Cubierto |
| PB-08 Ventas presenciales | B04 | Cubierto |
| PB-09 Pedidos online | B05 | Cubierto |
| PB-10 Administración SaaS | Bloque 1 | Ya aprobado funcionalmente con pendientes |
| PB-11 Clientes/autenticación | Bloque 1 + B02 | Cubierto |
| PB-12 Cancelaciones/devoluciones | B03 + B04 + B05 | Cubierto, cada subproceso con política propia |
| PB-13 Consulta pública | B05 | Cubierto |

## 13. Exclusiones de plataforma para evitar expansión no autorizada

No multiubicación; no pagos electrónicos ni transferencias validadas automáticamente; no gestión de flotas; no app móvil nativa; no hospitalización avanzada; no contabilidad integral; no notificaciones WhatsApp automáticas obligatorias; no emisión SUNAT afirmada sin integración/flujo legal; no fusión masiva automática de propietarios/historias; no seguimiento de compras a proveedores; no BI complejo; no predicción IA; no transmisión de historia clínica entre diferentes veterinarias.

## 14. Puertas posteriores al cierre funcional V1

- **FUNC-1 (CUMPLIDO):** aprobadas DEC-064..067 y consolidadas las políticas RO-01..16 en el paquete V1 académico.
- **FUNC-2 (DOCUMENTAL V1 CUMPLIDO):** nuevas DEC-064..067/RN-073..076 registradas y matriz completa; sincronización de originales históricos requiere control de versiones en repositorio en etapa siguiente.
- **FUNC-3:** cerrar bloqueo de acceso clínico post-transferencia y SUNAT/situación fiscal de V1 antes de autorizar uso real.
- **FUNC-4:** revisar estados/diagrama de transiciones contra operaciones inválidas, conflictos de concurrencia y auditoría.
- **FUNC-5:** formular los NFR, modelos de dominio, datos, arquitectura, flujos UI y despliegue en etapas posteriores de diseño **antes de orquestar Codex**.

**Dictamen:** cobertura funcional del paquete V1 aceptada. No se han ejecutado pruebas ni iniciado implementación.

## 15. Cierre transversal V1 — cuatro decisiones y límites de producción

**DEC-064** restringe el portal de clientes a datos básicos, agenda, servicios y pedidos. La atención de derechos legales sobre datos personales se define fuera del portal, con proceso legítimo antes de producción. **DEC-065** permite devoluciones parciales inspeccionadas, prohibiendo despachos parciales. **DEC-066** adopta pagos y constancias internos manuales, sin reemplazar comprobantes fiscales obligatorios. **DEC-067** exige encargado disponible para grooming y agenda sin solapamientos entre roles.

**Separación de fuentes de verdad:** B02 es dueño de propiedad e historia clínica; B03 de citas, reservas y profesional asignado; B04 de stock y POS; B05 de pedido, entrega y devolución; B01 de identidad/tenant; B06 coordina sin duplicar saldos, expedientes ni cuentas. Notificaciones son efectos secundarios: su fallo no revierte operación válida, pero queda registrado y reintentable.

**Autorización mínima en backend:** tenant, cuenta concreta, estado de organización, roles acumulados solo dentro de tenant, titularidad de recurso y estado de operación. Denegar por defecto. SaaS admin mantiene únicamente campos administrativos aprobados; en disputas o recuperaciones revisa **expedientes excepcionales** controlados, no historias clínicas ni ventas privadas.

**Control económico y temporal:** PEN de precisión fija, instantáneas de precios/tarifas, UTC para instantes y `America/Lima` para agenda; movimientos auditables e idempotentes. POS y e-commerce comparten una sola autoridad de existencias. Atención, pedido, pago, entrega y devolución son hechos independientes.

**Reglas de demo y cumplimiento:** utilizar únicamente usuarios/mascotas/ventas sintéticos en la demostración; no usar datos clínicos reales ni presentar comprobantes internos como SUNAT. Antes de operar con personas/ventas reales, completar análisis y cumplimiento de protección de datos aplicable en Perú, obligaciones tributarias, información a usuarios, gestión de derechos, seguridad operacional y pruebas. Esta aprobación V1 **no** acredita conformidad regulatoria.

**Condiciones previas a Codex:** diseño arquitectónico aprobado, selección tecnológica y NFR medibles, contratos y modelo de datos, control de migraciones, pruebas de concurrencia, estrategia de backup/restauración y plan de agentes con inspección inicial del repositorio. Se autoriza preparar el plan, **no ejecutar programación**.
