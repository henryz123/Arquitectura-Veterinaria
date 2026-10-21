# Lista de aceptación G1 — Entorno verificablemente listo

Marcar solamente al tener evidencia local de Codex y revisión del responsable.

- [ ] Revisión de `git status --porcelain=v1 -uall` previa a cambios; inventario de archivos y exclusiones.
- [ ] Directorio `arquitectura_veterinaria_v1_FINAL/` preservado y validado: 18 SHA sobre 18 + manifiesto.
- [ ] `.gitignore` revisado y actualizado únicamente tras aprobación; no contiene negación accidental de assets de evidencia; `.env` y caches futuros protegidos.
- [ ] Inspección de secretos y binarios en rutas previstas para commit, sin imprimir secretos.
- [ ] `AGENTS.md` y `PRODUCT.md` validados y concordantes con V1; `MASTER.md` marcado provisional; docs obsoletos reconciliados.
- [ ] ECC nativo verificado sin instalación duplicada; lista de skills efectivamente detectadas.
- [ ] Agentes ECC identificados como plantillas/no registrados o registrados y probados de verdad; sin promesas falsas de multiagente.
- [ ] Impeccable motor instalable y smoke verificado **O** hooks explícitamente inactivos por incompatibilidad documentada; no mantener hooks rotos confiados.
- [ ] Agent Browser y UIUX accesibles; opcionales marcados como tales sin gates falsos.
- [ ] Cambios P1A/P1B inspeccionados mediante `git diff` y `git diff --cached` antes del primer commit.
- [ ] Primer commit aprobado, sin secretos/artefactos efímeros; SHA commit guardado, `git status` informado.
- [ ] Git remoto: no necesario; si se configurará, decidir después sin publicar repositorio automáticamente.
- [ ] Procedimiento reproducible para reiniciar Codex y cargar AGENTS/skills/hook correcto.
- [ ] Autorización expresa del responsable para cruzar G1→G2.

**Nota:** tener 100 skills instaladas no es condición de G1; tener disponibles las capacidades necesarias y seguros los hooks, sí.
