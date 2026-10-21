# Orquestación y skills — V0.3

## Inventario operativo relevante

| Capacidad | Estado comprobado | Uso autorizado ahora |
|---|---|---|
| ECC de Affaan Mustafa (`ecc@ecc`) | Plugin nativo presente; skills disponibles en la sesión | planificación, revisión documental y Git por habilidades aplicables |
| Agentes ECC | Se hallaron plantillas, no registros activos demostrados | no simular ni delegar hasta comprobar un agente invocable |
| Agent Browser | Instalado, Chromium disponible | solo cuando exista aplicación autorizada para E2E y accesibilidad |
| UI UX Pro Max | Disponible; `MASTER.md` existente | referencia para UX posterior, no genera diseño aprobado ahora |
| Design Motion Principles | Disponible | solo para motion y accesibilidad cuando exista UI |
| Impeccable | skill/launcher presentes; motor no validado | prohibido ejecutar, activar hook o descargar motor en G1-A |
| Taste, Emil adicional, img2threejs | ausentes, parciales u opcionales según auditoría | no bloquean V1 ni G1 |

## Regla de asignación

Se usa una skill solo si está detectada, es pertinente y su resultado puede validarse. Todo agente, si llega a estar realmente disponible, recibe objetivo, entradas, exclusiones, archivos de lectura/escritura, evidencia esperada y un único propietario de escritura. Las plantillas de caché no cuentan como agentes registrados.

No hay agentes ECC registrados demostrables para invocación en esta tarea; por tanto, G1-A se realiza con revisión directa y el skill de flujo Git de ECC. No se crea ni instala un agente para satisfacer formalmente la orquestación.

## Router para fases posteriores

| Trabajo | Capacidades que deben verificarse antes de uso | Evidencia de salida |
|---|---|---|
| G2 arquitectura/ADR | ECC de arquitectura y revisión de seguridad/base de datos, solo si son invocables | ADR, amenazas, decisiones y crítica independiente |
| Diseño UX | UI UX Pro Max, Design Motion Principles, Impeccable solo tras P1B | alternativas, accesibilidad y `DESIGN.md` aprobado |
| Implementación | TDD, revisión Java/TypeScript, seguridad y base de datos | pruebas reproducibles y diff revisado |
| E2E | Agent Browser y navegador real | escenarios ejecutados, a11y y evidencia visual legítima |

Los hooks son código ejecutable. El hook de Impeccable permanece fuera de la ruta activa hasta que un P1B autorizado valide motor, procedencia, permisos, timeout, comportamiento de fallo y rollback.
