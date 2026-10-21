# SKILL-REG-002 — Política de skills, agentes y hooks

## Regla de activación
**Evaluar todas las skills pertinentes; usar únicamente las que aportan valor a la tarea y están realmente detectadas.** El requisito del responsable no significa invocar todas simultáneamente. Registrar skill, tarea, estado instalado, modo de uso y evidencia; si falta, proponer instalación verificada o alternativa aprobada.

## Estado actual (según auditoría P0 de Codex)
| Componente | Estado | Uso previsto | Bloquea |
|---|---|---|---|
| ECC Affaan plugin Codex (`ecc@ecc`) | activo caché 2.2.3 | plan, TDD, review, contexto, investigación, seguridad | Sí, debe conservarse |
| Plantillas ECC explorer/reviewer/docs-researcher | presentes, no registradas | lectura y revisión paralela si Codex las soporta | No, verificar antes de prometer |
| Agent Browser 0.27.0 | instalado | browser smoke y E2E UI, evidencia | En etapa E2E |
| UI UX Pro Max 2.15.0 | instalado | UX, componentes, revisión diseño | En fase UX |
| Impeccable | skill/hook sí, motor no | Product Brief, crítica, anti-slop | Resolver O desactivar hooks antes de escribir |
| Design Motion Principles | instalado | accesibilidad, transiciones útiles | No |
| Emil animation-vocabulary | utilizable | diseño de microinteracciones | No |
| Emil restantes | no verificados | uso solo cuando una necesidad lo justifique | No |
| Taste | ausente | revisión estética optativa | No |
| img2threejs | incompleto | 3D NO requerido V1 | No |
| awesome-design-md | referencias | inspiración para DESIGN.md sin copiar activos | No |

## Reglas de orquestación
1. **Un solo coordinador** decide el plan y consolida resultados; agente explorador investiga solo lectura; arquitecto analiza decisiones; reviewer independiente valida después.
2. Definir alcance de archivo exclusivo por agente; no escrituras paralelas en las mismas rutas ni migraciones compartidas.
3. Nunca inventar herramientas: enumerar herramientas que la sesión expone, roles y `SKILL.md` reales; si hay agente templado pero no habilitado, decirlo.
4. Antes de cada tarea: escoger skill pertinente por evidencia, explicar si está disponible, indicar invocación y prueba de resultado.
5. Registrar diferencias entre `plugin nativo`, `skill proyecto`, `skill global`, `CLI global`, `hook`, `plantilla de agente`: una cosa no prueba las demás.
6. Hooks ejecutan código: revisar origen, confianza, permisos, efecto, salida, timeout, y comportamiento fallido. **No habilitar Impeccable roto** ni permitir descargas automáticas sorpresa vía hook.
7. No añadir ECC Universal sobre plugin nativo; la documentación oficial advierte duplicación de capas para un mismo harness.

## Router sugerido
| Actividad | Principal | Opcional | Evidencia |
|---|---|---|---|
| P1A repo y Git | Codex inspección ECC | explorer si existe | status/diff, secret scan, lista staging |
| P1B instalación motor | inspección de origen/verificación | ECC reviewer | versión ejecutada, smoke hook |
| P2 ADR/arquitectura | ECC planning/architecture detectados | revisor seguridad y DB | ADR, diagramas, crítica |
| P2 UX y diseño | UI UX Pro Max + Impeccable reparado | Taste/Emil/Motion | PRODUCT/DESIGN, a11y, alternativas |
| I01 auth/tenancy | ECC TDD + security | reviewer independiente | unit/integración cross-tenant |
| I05 stock/ventas | ECC TDD + DB review | revisor concurrencia | pruebas idempotencia y paralelismo |
| I04 agenda | ECC TDD + QA | reviewer | doble reserva y huso horario |
| E2E integración | Playwright si se aprueba + agent-browser | a11y audit | escenarios reales, capturas auténticas |

## Fuentes upstream relevantes
- https://github.com/affaan-m/ECC (plugin nativo Codex, no apilar sync heredado).
- https://github.com/pbakaus/impeccable
- https://github.com/vercel-labs/agent-browser
- https://github.com/nextlevelbuilder/ui-ux-pro-max-skill
- https://github.com/kylezantos/design-motion-principles
- https://github.com/Leonxlnx/taste-skill
- https://github.com/emilkowalski/skills
- https://github.com/img2threejs/img2threejs
- https://github.com/VoltAgent/awesome-design-md

Todas las descargas requieren verificación de origen y aprobación de alcance; no ejecutar comandos de README de repositorios no inspeccionados a ciegas.
