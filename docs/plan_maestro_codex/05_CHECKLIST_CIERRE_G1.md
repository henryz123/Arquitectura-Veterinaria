# Checklist de cierre G1 — V0.3

Marcar solamente con evidencia local y aprobación que corresponda.

- [x] La especificación funcional conserva sus 19 archivos y su manifiesto verificable.
- [x] `AGENTS.md`, `PRODUCT.md`, `.gitignore` y la documentación P1A declaran límites coherentes con V1.
- [x] El backup de `.codex/hooks.json` conserva el SHA-256 registrado y el archivo activo no está presente.
- [x] No se ejecutó el launcher de Impeccable ni se descargó su motor durante P1A/G1-A.
- [x] V0.2 queda como historial y V0.3 concentra las decisiones operativas vigentes.
- [x] Se registró que `MASTER.md` es provisional.
- [x] Las 48 rutas exactas del primer staging fueron revisadas por contenido, procedencia y secretos. `.gitattributes` preserva sin normalización el paquete V1 cubierto por SHA-256.
- [x] `git diff --cached --name-only`, `--check`, `--stat` y `git status --short` fueron ejecutados y revisados. `--check` informa cinco saltos Markdown con dos espacios finales, ya presentes en dos archivos V1 inmutables; las rutas documentales editadas están limpias.
- [ ] El responsable aprobó el primer commit local por separado.
- [ ] Se autorizó la salida G1 → G2.

No se requiere ejecutar P1B para hacer el primer commit documental mientras el aislamiento del hook sea verificable. P1B sigue siendo requisito antes de habilitar o usar Impeccable.
