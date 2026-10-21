# 17 — Acta de cierre de la especificación funcional V1

**Proyecto:** Arquitectura - Veterinaria  
**Fecha:** 21 de octubre de 2026  
**Objeto:** cerrar funcionalmente los seis bloques de V1 para demostración académica con datos sintéticos.

## 1. Aprobaciones y procedencia

- **Bloque 1:** CU-001 V0.6 y RF-001 V0.6 aprobados previamente por el responsable, con riesgos anotados; B01 en este ZIP es su materialización documental a partir de la conversación.
- **Bloques 2 a 6:** el responsable aprobó expresamente cuatro decisiones críticas y solicitó recibir los seis bloques finalizados; esta entrega consolida reglas aprobadas y detalles conservadores en versión funcional final V1.0.
- **No aprobado ni ejecutado:** diseño técnico completo, despliegue, programación, pruebas reales, emisión SUNAT, funcionamiento con datos personales reales.

## 2. Criterios cumplidos de cierre FUNCIONAL (documental)

1. Registro público SaaS, cuenta inicial, empleados/clientes, roles y suspensión definidos en B01.
2. Propietarios, pacientes y clínica con privacidad de portal y vinculación verificada definidos en B02.
3. Agendas y grooming con profesional asignado, rechazo de solapamientos y aceptación de contrapropuestas definidos en B03.
4. Stock único, venta presencial, ajustes, pagos manuales y devoluciones definidos en B04.
5. Pedido online, inventario al confirmar, estados de entrega/pago y devoluciones parciales sin despacho parcial definidos en B05.
6. Integración, aislamiento de tenants, auditoría, notificaciones, concurrencia y demostración definidos en B06.
7. Identificadores de casos/requisitos/criterios trazables de los seis bloques, sujetos a validación estática del ZIP.
8. Separación explícita entre aprobado/derivado, diseñado/no ejecutado y puertas de producción.

## 3. Aspectos que permanecen pendientes, fuera del cierre funcional

**Antes de implementación:** cerrar evidencia mínima y tratamiento de credenciales antiguas para recuperación excepcional y conflictos RUC; modelar NFR de seguridad, retención, auditoría y sesiones; aprobar stack, persistencia, contratos, diagramas C4/arc42/ADR, UX y pruebas de carreras concurrentes; plan maestro Codex con auditoría de repositorio real.

**Antes de operación real:** evaluar obligaciones tributarias SUNAT y circuito de comprobantes válido; implementar salvaguardas de Ley 29733 y reglamento aplicables, información a titulares/gestión de derechos; validar privacidad y seguridad, backups, recuperación y cumplimiento. No utilizar pacientes reales en demo.

**Tras implementación:** ejecutar criterios de aceptación, no presentar pruebas diseñadas como ejecutadas. Una aceptación documental no equivale a certificación de seguridad/cumplimiento.

## 4. Decisión de cierre

**ESTADO: V1 FUNCIONAL COMPLETA PARA PLANIFICACIÓN ARQUITECTÓNICA.** El catálogo funcional y sus controles quedan congelados para preparación de arquitectura y del plan maestro Codex. Cambios nuevos exigirán solicitud, evaluación de impacto y nueva versión. **No iniciar Codex para programar sin autorización explícita futura.**
