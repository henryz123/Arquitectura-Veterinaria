# 16 — Registro de cierre: decisiones V1 y políticas operativas

**Estado:** decisiones expresamente confirmadas por el usuario y especificación funcional final para proyecto académico. **Fecha:** 21-10-2026. **No modifica numeraciones antiguas.**

## A. Decisiones finales expresamente aprobadas

| ID | Área | Texto aprobado |
|---|---|---|
| DEC-064 | Privacidad clínica | Cliente ve ficha básica, citas, reservas y pedidos, pero **no** historia clínica completa en portal V1. |
| DEC-065 | Devoluciones | Permitir devoluciones parciales por producto y cantidad tras inspección física; **no** despachos parciales V1. |
| DEC-066 | Pago y comprobantes | Registrar pagos manualmente y generar solo constancias internas. El cumplimiento tributario se resolverá antes de operaciones reales. |
| DEC-067 | Grooming y agenda | Asignar encargado disponible al confirmar grooming, impedir solapamientos entre roles y requerir aceptación ante cambios de horario confirmado. |

**Nuevas reglas derivadas:**

- **RN-073:** la cuenta cliente no habilita lectura de historia clínica íntegra mediante portal, sin limitar los derechos legales aplicables tramitados por canales autorizados.
- **RN-074:** se permiten devoluciones parciales por líneas/cantidades con inspección y límite respecto de lo realmente descontado; no existen despachos parciales V1.
- **RN-075:** los cobros son manuales y las constancias son expresamente internas/no tributarias; el uso fiscal real necesita procedimiento legal validado.
- **RN-076:** grooming confirmado exige encargado activo y disponible; conflictos de agenda se verifican por trabajador entre todos sus roles; modificar horarios confirmados requiere aceptación.

## B. Reglas operativas de especificación (sin falsificar aprobación individual)

El responsable solicitó cerrar los seis bloques como una propuesta final coherente. Los siguientes comportamientos se incorporan como **reglas operativas V1** derivadas de los procesos y decisiones ya aceptados; se diferencian de nuevas decisiones DEC explícitas:

| Política | Regla de V1 | Motivación |
|---|---|---|
| RO-01 | Transferencia de mascota conserva historia y revoca control operativo anterior, sin acceso retroactivo irrestricto al historial clínico | Privacidad |
| RO-02 | No fusionar automáticamente fichas ni historias clínicas duplicadas | Seguridad de datos |
| RO-03 | Consultas sin cita previa, por veterinario autorizado | Operación presencial |
| RO-04 | Calendario de disponibilidad por empleado, configuración simple de horarios, servicios y ausencias | Simplicidad V1 |
| RO-05 | Registrar movimientos de inventario inmutables, atómicos e idempotentes | Consistencia de stock |
| RO-06 | Conservar precios/tarifas aceptados y exigir aceptación de cambios materiales de pedido | Consistencia comercial |
| RO-07 | Confirmación logística, validación de pago y movimiento de stock tienen estados independientes | Evita dobles efectos |
| RO-08 | Notificaciones mínimas por bandeja interna/correo, fallo de email no revierte transacciones | Fiabilidad |
| RO-09 | Moneda PEN exacta, instantes UTC, agenda representada en America/Lima | Precisión |
| RO-10 | Historia clínica finalizada y ventas tienen rectificación/anulación con rastro, sin borrado silencioso | Auditoría |
| RO-11 | Catálogo público solo de tenant activo; no mostrar datos privados | Aislamiento |
| RO-12 | Servicios prestados se cobran como líneas no inventariables, sin crear venta automática | Separación de procesos |
| RO-13 | Delivery por zonas textuales y una tarifa fija de veterinaria, sin GPS | Alcance acotado |
| RO-14 | Venta POS exige rol cajero; admin solo si también tiene ese rol | Mínimo privilegio |
| RO-15 | Recepción/admin resuelve cancelaciones de citas y grooming confirmados; cliente cancela las pendientes | Coherencia de responsabilidad |
| RO-16 | Uso demo exclusivamente con datos sintéticos y constancias no fiscales | Seguridad académica |

## C. Controles de cambios de líneas base

**CCR-001:** activar veterinaria después de verificar correo y establecer contraseña (aprobado previamente). ACT-001/VIS-001 mantienen su historial; actualización consolidada posterior no se finge ejecutada.

**CCR-002:** recuperación excepcional del administrador de veterinaria por expediente comprobado, sin acceso clínico SaaS admin (aprobada previamente).

**CCR-003:** integración de las cuatro decisiones DEC-064..067 en B02..B06; no se modifican los 10 actores/13 procesos de ACT-001.

## D. Riesgos residuales que NO bloquean cierre funcional académico

- La autenticación real y privacidad exigen NFR, amenazas, seguridad de tokens/roles, respaldos y pruebas con datos sintéticos.
- La recuperación excepcional del admin y las disputas de RUC requieren evidencia mínima y revisión antes de implementar flujos sensibles.
- La contabilidad tributaria y el tratamiento de datos personales reales se deben validar antes de operar comercialmente.
- Los detalles de rutas, diseño gráfico, proveedor de correo, motor de BD, compensaciones técnicas y despliegue pertenecen a la etapa de arquitectura/plan de implementación.

**Cierre:** DEC-001..067 y RN-001..076 identificadas (los históricos permanecen en fuente conversacional; 064..067 y 073..076 son nuevos aquí); RO-01..16 son políticas funcionales incluidas en el paquete V1, no aprobación individual atribuida retroactivamente.
