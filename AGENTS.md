# AGENTS.md — Arquitectura - Veterinaria

## Estado y límite de autorización

La especificación funcional V1 está cerrada para una demostración académica con datos sintéticos. La fase actual es preparación documental y arquitectónica. No se puede programar la aplicación, crear scaffolding, ejecutar migraciones, desplegar ni publicar cambios hasta que el responsable apruebe expresamente G3 y el incremento concreto.

## Jerarquía documental

Ante una contradicción, aplicar este orden y registrar la discrepancia antes de escribir:

1. Instrucción expresa y vigente del responsable.
2. "arquitectura_veterinaria_v1_FINAL/": línea base funcional inmutable de B01 a B06. Un cambio requiere una nueva versión y control de cambio; nunca se sobrescribe por inferencia técnica.
3. Decisiones técnicas de planificación aprobadas posteriormente por el responsable.
4. ADR y documentos arquitectónicos aprobados en fases posteriores.
5. "docs/plan_maestro_codex/": Plan Maestro Codex V0.3 vigente; no reemplaza la especificación funcional. V0.2 permanece solo como historial en "docs/historial/plan_maestro_codex_v0_2/".
6. "instrucciones_maestras_chatgpt_v1_1.md" y "manual_protocolo_integral_chatgpt_v1_1.md": método de trabajo.
7. Este archivo: instrucciones persistentes de ejecución.
8. "PRODUCT.md": resumen de producto derivado de V1; no añade requisitos.
9. "design-system/arquitectura-veterinaria/MASTER.md": dirección visual provisional, no diseño aprobado. "DESIGN.md" no existe todavía.

## Alcance funcional protegido

- SaaS B2B académico para veterinarias independientes en Perú, una sede por tenant y datos exclusivamente sintéticos.
- Cuentas y roles separados por contexto y tenant; denegar por defecto; sin acceso cross-tenant.
- Historia clínica editable solo por veterinarios autorizados; el portal cliente no expone historia clínica completa.
- Agenda clínica y grooming sin solapamientos por persona; cambios de horario confirmados requieren aceptación.
- Una autoridad de inventario por tenant/producto para POS y pedidos web; operaciones idempotentes, auditables y sin stock negativo.
- Pagos manuales y constancias internas no tributarias. No se afirma integración SUNAT ni se usa el sistema para operaciones reales.
- No introducir multisucursal, pagos online, apps nativas, despacho parcial, hospitalización avanzada, GPS, BI complejo ni funcionalidades fuera de V1 sin autorización y cambio de línea base.

## Línea técnica aprobada para planificación

Java 21, Spring Boot 4.1.1, Maven y Maven Wrapper, Angular 21 con TypeScript, PostgreSQL 18, monolito modular con DDD estratégico y arquitectura hexagonal selectiva son decisiones de planificación aprobadas. No autorizan todavía archivos de aplicación, selección detallada de librerías, esquema físico, Docker, migraciones ni implementación; esos resultados requieren G2 y ADR revisados.

La primera demostración pública tiene como objetivo el 26 de octubre de 2026 y la entrega académica final el 18 de noviembre de 2026. La demostración es un avance funcional y no presume completar los 118 requisitos de V1: debe priorizar flujos verticales reales y estables. La URL pública y el repositorio GitHub público requieren autorización posterior, revisión de secretos, licencias, datos sintéticos y evidencia de pruebas. Estas fechas no reducen las puertas de seguridad, pruebas ni revisión de secretos. El presupuesto inicial es S/ 0; cualquier servicio o gasto requiere autorización explícita.

## Método de ingeniería

1. Inspeccionar instrucciones, estado de Git, archivos afectados, dependencias y riesgos antes de cada cambio.
2. Declarar alcance, exclusiones, fuente de verdad, archivos autorizados y validación prevista.
3. Separar hechos verificados, propuestas, supuestos y pendientes. No inventar resultados, agentes, pruebas ni configuraciones.
4. Hacer cambios pequeños, reversibles y con ownership explícito. No escribir simultáneamente sobre una misma interfaz, esquema o documento de autoridad.
5. Revisar el diff y ejecutar únicamente verificaciones proporcionales y autorizadas. Registrar limitaciones reales.
6. Detenerse ante una contradicción funcional, operación irreversible, secreto, permiso insuficiente o requisito legal no resuelto.

## Seguridad, datos y calidad

- No introducir, imprimir, registrar ni versionar contraseñas, tokens, claves, datos personales o datos clínicos reales.
- Usar solo datos sintéticos para desarrollo, pruebas y demostración.
- Validar en servidor tenant, tipo de cuenta, estado de organización, rol efectivo, propiedad del recurso y transición de estado.
- Antes de implementar, definir NFR, amenazas, privacidad, retención, backup/restauración, sesiones, auditoría y controles de concurrencia.
- Tras G3, cada incremento seguirá TDD (RED → GREEN → REFACTOR) y pruebas unitarias, integración, contrato, seguridad, concurrencia y E2E según riesgo. No declarar una prueba ejecutada si no hay comando y evidencia real.
- Agent Browser se empleará únicamente cuando exista una aplicación ejecutable y flujos autorizados; incluirá accesibilidad y regresión visual cuando corresponda.

## ECC, skills y agentes

- ECC de Affaan Mustafa ("ecc@ecc") es la ruta primaria. No instalar "ecc-universal" ni duplicar su configuración.
- Usar skills solo cuando sean pertinentes y estén realmente detectadas en la sesión. Las plantillas de agente en la caché de ECC no son agentes registrados ni autorizan a simular subagentes.
- Asignar agentes solo con objetivo, entradas, exclusiones, archivos, evidencia y propietario de escritura definidos. Las revisiones independientes pueden ser de solo lectura; las escrituras deben tener un único responsable por conjunto de archivos.
- UI UX Pro Max y Design Motion Principles se aplicarán al diseñar superficies autorizadas. Taste, img2threejs y skills de movimiento adicionales son opcionales hasta que una necesidad concreta los justifique.
- Impeccable no se ejecuta ni se habilita mientras su motor no esté instalado, validado y aprobado en P1B. Los hooks permanecen aislados durante G1.

## Git y documentación

- Antes de modificar: revisar "git status", archivos existentes y, cuando aplique, el diff.
- Prohibidos sin autorización específica: "git add .", commits, pushes, cambios de remoto, "git reset --hard", "git clean", rebase de historia compartida y operaciones destructivas.
- El primer commit requiere revisión humana del diff, análisis de secretos y una lista de rutas explícitas. Después de la línea base, usar ramas "codex/<alcance>" salvo instrucción distinta.
- Versionar fuentes funcionales, documentación, configuración revisable y evidencias legítimas. Ignorar solo secretos, dependencias, cachés y resultados reproducibles.
- Mantener "PRODUCT.md", el plan, ADR posteriores y pruebas coherentes con el estado real. No presentar documentos de planificación como software implementado.
