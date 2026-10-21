# AGENTS.md — Arquitectura - Veterinaria (borrador para raíz)

> Copiar en raíz solo mediante P1A y revisión de diff. Aplicación V1 NO autorizada para codificar hasta G3.

## Fuente de verdad y restricciones
1. Instrucciones actuales expresas del responsable.
2. `arquitectura_veterinaria_v1_FINAL/` especificación funcional V1, cerrada para prototipo académico; no modificar sin CCR.
3. `instrucciones_maestras_chatgpt_v1_1.md` y `manual_protocolo_integral_chatgpt_v1_1.md` como protocolos de ingeniería.
4. ADR arquitectónicas explícitamente aprobadas (cuando existan) y plan de tareas autorizado.
En conflictos, registrar discrepancia y pedir decisión; no sobrescribir documentos con supuestos.

## Estado del proyecto
Multi-tenant, una sede por veterinaria, cinco roles internos acumulables, cuentas independientes de clientes, inventario compartido POS/web, clínica solo veterinario, agenda sin doble reserva, pago manual/constancia interna, sin SUNAT/pasarela, datos exclusivamente sintéticos.

## Restricciones de ejecución
- Fase actual: preparación de entorno y arquitectura, NO scaffolding ni implementación.
- Antes de cada cambio: inspeccionar repo, estado Git, archivos y contexto; declarar plan, ruta, riesgos y pruebas.
- No hacer `git add .`, `git reset --hard`, `git clean`, `git push`, instalaciones globales, actualización de hooks o deploy sin autorización específica.
- Proteger secretos y datos de clientes; no usar datos clínicos reales, no registrar tokens/contraseñas en logs.
- No prometer resultados no probados. Cada avance requiere diff, prueba verificable y revisión.

## Skills y agentes
ECC de Affaan `ecc@ecc` es ruta primaria. Considerar y activar skills según tarea y disponibilidad real: Impeccable/UIUX para diseño, Agent Browser para UI/E2E, Motion/Emil/Taste para mejoras visuales justificadas. No asumir que `AGENTS.md` crea agentes, no superponer ECC universal ni herramientas sin revisar procedencia.

## Orquestación
Coordinador único, exploración/revisión paralela solo lectura cuando disponible, escritura con ownership de archivos, sin ediciones paralelas del esquema/migraciones. Prompt de subagente: objetivo, entradas, exclusiones, RF/CA, archivos autorizados, pruebas y reporte de evidencias.

## Puertas y definición de terminado
- G1: baseline Git, entorno reparado, hooks verificados o inactivos, PRODUCT/AGENTS revisados.
- G2: diseño arquitectónico, NFR, modelo de datos, ADR, seguridad, UX/infra, plan de pruebas aprobado.
- G3: autorización expresa para programar.
- Por tarea: diff auditable, pruebas reproduciendo criterios, seguridad de tenant/permisos, fallos y rollback, docs actualizadas.
