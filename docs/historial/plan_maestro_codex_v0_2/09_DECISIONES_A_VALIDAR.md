# Decisiones del responsable requeridas para P1

**No plantear como nuevo problema lo que P0 ya resolvió.** Basta ratificar los permisos siguientes; Codex deberá informar antes de cambios irreversibles.

1. **P1A:** autorizar crear/revisar `AGENTS.md`, `PRODUCT.md`, conciliar documentación antigua y proponer cambios de `.gitignore`, **sin installs, sin commit**. ¿Sí?
2. **Hooks Impeccable:** autorizar solo diagnóstico y un plan de aislamiento reversible de hooks rotos antes de escribir; si requiere cambiarlos, presentar diff y pedir aprobación específica. ¿Sí?
3. **Git:** tras revisar secretos y diff, ¿autoriza que Codex prepare staging explícito y solicite una segunda confirmación para ejecutar el **primer commit local**? Sin push. ¿Sí?
4. **Plan maestro V0.2:** autorizar guardarlo dentro de `docs/plan-maestro-codex/`, sin desplazar la carpeta funcional ni sobrescribir `MASTER.md`. ¿Sí?

No hace falta decidir stack, frontend, auth ni cloud en P1; pertenecen a G2. Tampoco hace falta instalar Taste/img2threejs ni crear agentes que la versión de Codex no pueda invocar.
