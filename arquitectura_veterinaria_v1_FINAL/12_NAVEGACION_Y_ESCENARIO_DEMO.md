# V1 — Arquitectura de información UX y escenario de demostración académica

**Estado:** propuesta de estructura de pantallas, no wireframes ni interfaz implementada. Solo refleja funcionalidades previamente identificadas.

## 1. Vistas públicas

- **Landing del SaaS**: explicación general y acceso a registro de veterinaria.
- **Registro de veterinaria**: formulario + espera de verificación + establecimiento de contraseña; vista de error de RUC/conflicto y solicitud de revisión.
- **Portada pública de veterinaria**: identificación comercial, productos publicados, servicios y acceso de clientes.
- **Tienda pública del tenant**: filtros mínimos de categoría, listado/detalle de producto, carrito por veterinaria; sin filtraciones de otros tenants.
- **Accesos separados**: administración global, personal de veterinaria y cliente (según DEC-048), con recuperación en contexto.

## 2. Panel de administración de veterinaria

Navegación mínima: `Inicio operativo | Trabajadores y roles | Propietarios | Mascotas | Agenda | Productos | Inventario | Ventas | Pedidos online | Servicios | Mi cuenta`.

- **Inicio operativo**: solicitudes pendientes, citas del día, pedidos en revisión, bajo stock (solo tenant).
- **Trabajadores**: invitaciones, rol(es), estado y desactivación con protección de último administrador.
- **Propietarios y mascotas**: búsqueda limitada al tenant, ficha básica, historial de responsabilidad y solicitudes de vinculación.
- **Agenda**: configuración de turnos/bloqueos, confirmación/contrapropuestas/reasignaciones.
- **Inventario**: productos, precio, stock, ajustes y movimientos.
- **Pedidos**: revisión y confirmación, preparación, entrega, cancelaciones y devoluciones.

## 3. Panel del veterinario

`Mi agenda | Pacientes | Consultas | Vacunaciones | Recetas`. Registro clínico limitado a tenant y rol. Formularios de borrador/finalización/adenda. Si también posee rol administrativo, aparecen permisos adicionales explícitos, no los obtiene por ser veterinario.

## 4. Panel de recepción

`Agenda | Solicitudes | Propietarios | Mascotas | Vinculaciones y transferencias | Reservas grooming`.

La recepcionista puede gestionar confirmaciones/reasignaciones, pero no editar historia clínica. Muestra únicamente datos básicos para coordinar la atención.

## 5. Panel de caja

`Venta presencial | Pedidos web | Cobros manuales | Cancelaciones/Devoluciones`.

No existe pantalla para ajuste manual de inventario si posee solo rol cajero.

## 6. Panel de grooming

`Mis servicios de hoy | Detalle operativo | Iniciar/Completar`.

No muestra historia clínica íntegra ni permite confirmar reservas sin rol adicional.

## 7. Portal privado de cliente

`Mis mascotas | Mis citas | Reservas de baños/peluquería | Pedidos | Perfil | Solicitar vinculación`.

Muestra progreso de solicitudes y pedidos. No gestiona historias clínicas íntegras hasta que exista autorización funcional/legal explícita.

## 8. Administrador SaaS

`Organizaciones | Detalle administrativo | Suspender/Reactivar | Reclamaciones RUC | Recuperaciones excepcionales`.

Solo los siete campos administrativos del tenant están visibles en consulta normal. Las vistas de reclamación y recuperación contienen solo los datos necesarios del expediente autorizado y no conceden una vista sobre los datos clínicos.

## 9. Flujo de sustentación integrado (todo sintético)

1. Mostrar dos o tres veterinarias ficticias con catálogos, trabajadores, clientes, mascotas y existencias diferentes.
2. Acceder como cliente de Veterinaria A; visualizar sus mascotas básicas y solicitar cita clínica en horario propuesto.
3. Entrar con recepción de A y contraofertar otra hora; aceptar como cliente; confirmar asignación de veterinario disponible.
4. Acceder como veterinario de A; abrir cita y registrar consulta/vacuna/receta; demostrar bloqueo de edición clínica para recepción.
5. Solicitar grooming, confirmar con recepción y completar con encargado de servicio.
6. Registrar venta POS de producto X en A, comprobar saldo del mismo inventario usado online.
7. Crear pedido online de X con cliente; comprobar que pendiente no resta stock; confirmar como cajero y observar movimiento único.
8. Forzar stock insuficiente con una segunda operación; mostrar estado revisión sin saldo negativo.
9. Cancelar pedido confirmado antes de despacho, mostrar restitución una sola vez; después simular un pedido entregado y devolución física apta/no apta.
10. Entrar como trabajador de Veterinaria B con mismo correo que A pero contraseña/tenant propio; demostrar que no ve pacientes, ventas, pedidos ni permisos de A.
11. Mostrar que un empleado desactivado pierde sesión, pero su historial clínico/comercial permanece.
12. Mostrar un administrador SaaS consultando y suspendiendo A; no ve historias ni registros de B; B continúa funcionando.
13. Mostrar logs/auditoría y pruebas automatizadas reales cuando Codex haya implementado, nunca inventar capturas o resultados.

## 10. Criterios de diseño UX posteriores

- Formularios claros con mensajes de error sin filtración de datos privados.
- Navegación responsive en escritorio y móvil web (no app nativa).
- Etiquetas de estados comprensibles al cliente, no solo códigos técnicos.
- Confirmaciones explícitas para pérdidas de acceso, cancelaciones y liberación de RUC.
- Aceptación/rechazo de contrapropuesta evidente y accesible.
- Accesibilidad verificable mediante criterios a definir con NFR y stack.

**Siguiente etapa:** wireframes, mapa de rutas y estados por pantalla, **no autorizado como implementación en este momento**.

## Estado de cierre

**Navegación objetivo V1 documentada.** Diagramas de interfaz/wireframes y pruebas UX se realizarán en etapa arquitectónica/de diseño; demo siempre con datos sintéticos.
