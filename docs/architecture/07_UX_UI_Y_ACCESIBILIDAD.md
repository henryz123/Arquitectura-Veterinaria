# G2 — Estrategia UX/UI y accesibilidad

## Estado de diseño

`design-system/arquitectura-veterinaria/MASTER.md` permanece una dirección visual provisional; no es una marca ni un `DESIGN.md` aprobado. Esta estrategia evita fijar colores, activos o tipografía como hecho de producto antes de una decisión específica de UX.

Las superficies se clasifican como **Operate**: los usuarios completan tareas administrativas, clínicas o comerciales. La claridad de estado, jerarquía, prevención de error y recuperación priman sobre ornamentación.

## Principios implementables

- Diseño responsive mobile-first, con formulario y tabla adaptables; no depender de hover ni ocultar acciones esenciales en iconos sin nombre accesible.
- Un sistema de tokens en tres capas (primitivo → semántico → componente), sin hexadecimales dispersos en componentes.
- Estados explícitos para carga, vacío, éxito, error, permiso denegado, suspensión de tenant, conflicto de stock y propuesta de horario.
- Etiquetas visibles, errores junto al campo, resumen enfocable en formularios extensos, foco persistente y navegación completa por teclado.
- Contraste mínimo 4.5:1 para texto normal, objetivos táctiles suficientes, información no transmitida solo por color y soporte de escala de texto.
- Toda animación respeta `prefers-reduced-motion`; las acciones frecuentes usan respuesta inmediata o movimiento menor de 300 ms, sin animar geometría de layout.
- Iconografía vectorial coherente, nunca emoji como control estructural.

## Flujos que requieren diseño y prueba antes de demo

1. Inicio de sesión por tipo de cuenta y recuperación sin enumerar identidad.
2. Creación/consulta de propietario y mascota con mensajes de privacidad.
3. Agenda con alternativa de horario, aceptación y conflicto de disponibilidad comprensible.
4. Episodio clínico con controles de rol y diferenciación visual entre borrador, final y adenda.
5. POS/pedido con precio congelado, idempotencia y error de stock no negativo.
6. Catálogo público y pedido privado sin hacer parecer que hay pago/SUNAT integrado.

## Uso contextual de skills

UI UX Pro Max y Design Motion Principles sirven de lista de control cuando se diseñe una pantalla autorizada. La calibración provisional conservada es `DESIGN_VARIANCE=3`, `MOTION_INTENSITY=2`, `VISUAL_DENSITY=6`; no es una orden para generar UI ahora. Impeccable no se ejecuta: su motor no está disponible y el hook continúa aislado. Taste, img2threejs y una copia independiente de Emil Skills no están disponibles; no son bloqueantes para V1.

## Evidencia futura

Cada pantalla autorizada tendrá su brief de superficie, criterios de accesibilidad y captura sintética. Agent Browser realizará E2E, `a11y` y comparación visual únicamente después de que Chromium y la aplicación estén verificadamente disponibles.
