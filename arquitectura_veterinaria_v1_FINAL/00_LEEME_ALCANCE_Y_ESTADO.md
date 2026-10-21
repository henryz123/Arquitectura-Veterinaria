# Arquitectura - Veterinaria — Especificación funcional integral V1.0 FINAL

**Fecha de cierre funcional:** 21 de octubre de 2026.  
**Estado:** CERRADO funcionalmente para **prototipo académico V1 con datos sintéticos**; fase de arquitectura, plan de Codex, implementación, pruebas reales y cumplimiento para uso comercial **NO cerrados**.  
**Aprobación del responsable:** cuatro decisiones V1 expresamente ratificadas en el chat y solicitud explícita de entregar los seis bloques finalizados.  
**Base aprobada anterior:** VIS-001 V0.3, ACT-001 V0.7, REG-DEC-001 V0.1, REG-RN-001 V0.1 y CU-001/RF-001 V0.6 del Bloque 1. Sus originales permanecen en el historial de la conversación; B01 es una **materialización reconstruida** a partir de esas aprobaciones, no una copia verificada de un archivo anterior.

## 1. Los seis bloques

| Bloque | Documento fuente funcional definitivo | Resultado |
|---|---|---|
| B01 | `B01_ADMINISTRACION_SAAS_Y_AUTENTICACION.md` | Alta/verificación, roles, cuentas independientes, administración y recuperación excepcional |
| B02 | `B02_PROPIETARIOS_MASCOTAS_CLINICA.md` | Propietarios, mascotas, vínculo verificado, historia clínica y privacidad |
| B03 | `B03_CITAS_Y_GROOMING.md` | Solicitudes/contrapropuestas, asignación, agenda común sin doble reserva, grooming |
| B04 | `B04_INVENTARIO_Y_POS.md` | Productos, stock compartido, ventas presenciales y pagos manuales |
| B05 | `B05_ECOMMERCE_PEDIDOS.md` | Catálogo, carrito, pedidos, entrega única, cancelaciones y devoluciones parciales |
| B06 | `B06_TRANSVERSAL_INTEGRACION.md` | Permisos, eventos de negocio, notificaciones, auditoría, reportes y consistencia |

**Anexos operativos:** `07_DICCIONARIO_DATOS_LOGICO.md`, `08_TRAZABILIDAD_Y_PRUEBAS.csv`, `09_DECISIONES_Y_RIESGOS_PARA_APROBACION.md`, `10_CHECKLIST_PRECODEX.md`, `12_NAVEGACION_Y_ESCENARIO_DEMO.md`, `13_RESUMEN_EJECUTIVO_FUNCIONAL_V1.md`, `14_AUDITORIA_ESTATICA_DEL_PAQUETE.md`, `16_REGISTRO_DE_CIERRE_Y_DECISIONES.md`, `17_ACTA_CIERRE_FUNCIONAL_V1.md`, `18_MATRIZ_DE_PERMISOS_Y_TRANSICIONES.md`, `11_VALIDACION_AUTOMATICA.json` y `15_MANIFIESTO_SHA256.json`.

## 2. Clases de decisión y fuente de verdad

1. **Confirmada históricamente (C):** DEC-001..DEC-063, RN-001..RN-072, líneas base anteriores. No se renumeran.
2. **Confirmada explícitamente en este cierre (C-V1):** cuatro decisiones nuevas DEC-064..067, recogidas en `16_REGISTRO_DE_CIERRE_Y_DECISIONES.md`.
3. **Regla operativa de especificación (RO):** criterios conservadores necesarios para dar coherencia a V1 sin ampliar módulos (precios congelados, eventos inmutables, datos sintéticos, PEN, idempotencia, notificaciones mínimas, etc.). Son criterios **incluidos** en esta especificación final a petición del responsable, pero no se presentan falsamente como decisiones numeradas y votadas individualmente.
4. **Puerta posterior (G):** cualquier requisito legal, NFR, selección tecnológica, operacional o procedimental que deba cerrarse antes de desarrollar flujo de alto riesgo o usar datos/ventas reales. No se presenta como implementado ni probado.

**Precedencia:** aprobación explícita del usuario > documento conceptual aprobado > política explícita de este cierre > detalle propuesto/supuesto técnico. Toda modificación de línea base se materializará en una nueva versión con CCR, sin sobrescribir silenciosamente originales.

## 3. Reglas esenciales y fronteras del alcance V1

- SaaS B2B para varias veterinarias independientes de Perú, **una sola sede por tenant**; cinco roles internos acumulables, sin privilegios cross-tenant.
- Portal cliente autenticado por veterinaria, independiente de cuentas laborales; sin lectura del expediente clínico completo. Derechos legales de acceso a datos personales requerirán procedimiento separado.
- Clínica la edita exclusivamente rol veterinario; transferencias de propiedad y vínculos digitales se verifican manualmente y conservan historia.
- Citas clínicas y grooming confirmados solo por recepción/admin, con profesional/encargado disponible asignado y **sin solapamientos**. Contraoferta no bloquea cupo; cambio de hora requiere aceptación y nueva validación.
- Cajero confirma venta presencial; administrador puede operar POS solo si también tiene rol cajero. Administración de catálogo/ajustes manuales es exclusiva del rol admin vet.
- Un único inventario vendible por tenant/producto; POS descuenta al completar venta, online descuenta **solo al confirmar**; pendientes y carrito no reservan; sin saldos negativos, movimientos duplicados ni confirmaciones parciales.
- Pedidos con recojo o delivery de tarifa fija y zona textual; **sin despachos parciales**; devoluciones por línea/unidad con inspección y restitución solo para productos físicamente aptos.
- Pagos externos **registrados manualmente** y constancias identificadas como internas/no tributarias. Sin pasarela, sin integración SUNAT de V1 académica; **prohibida presentación como sistema de comprobantes tributarios**.
- Notificaciones y estados separados de negocio; fallos de correo se gestionan con retry y registro, sin reversión de transacción válida.
- UTC para instantes, `America/Lima` para agenda, PEN con precisión decimal fija; datos sintéticos para demo.

## 4. Exclusiones controladas

Sin multisucursal, app móvil nativa, acceso clínico completo vía portal cliente, pasarela online, emisión electrónica SUNAT integrada, hospitalización avanzada, laboratorio, logística GPS, envíos parciales, fusión destructiva de historias, contabilidad general y reportes BI complejos.

## 5. Alcance del cierre y condición previa a Codex

**Terminado:** lógica de negocio y especificación funcional de B01 a B06, casos de uso, requisitos, criterios de aceptación diseñados, reglas, ámbitos y estados. **No significa:** arquitectura física lista, código implementado, cobertura probada ni conformidad legal.

**Después del cierre:** NFR medibles, modelo de datos físico, arquitectura arc42/C4/ADR, elección de stack, diseño UX y contratos, privacidad y retención, estrategia de despliegue/backup, inspección del repositorio real y plan maestro de Codex con agentes, dependencias y gates de pruebas. Ningún agente recibe tareas de implementación hasta autorización explícita adicional.

## 6. Referencias de control de riesgos externos (no sustituyen evaluación legal)

- Autoridad Nacional de Protección de Datos Personales, DS 016-2024-JUS: https://www.gob.pe/institucion/anpd/normas-legales/6554453-n-016-2024-jus
- SUNAT, contribuyentes obligados a comprobantes de pago electrónicos (página consultada en octubre de 2026): https://cpe.sunat.gob.pe/informacion_general/obligados_cpe
- OWASP Authorization Cheat Sheet, denegar por defecto y autorizar por recurso: https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html

Estas referencias justifican controles y puertas futuras. El prototipo académico **no** queda certificado ni fiscalmente habilitado por su sola existencia.
