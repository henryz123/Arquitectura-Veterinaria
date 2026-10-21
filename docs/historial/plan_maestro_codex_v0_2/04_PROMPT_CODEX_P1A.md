# MENSAJE PARA CODEX — P1A / Preparación de línea base controlada

**Usar solo cuando el responsable apruebe P1A. Pegar íntegro en el chat actual de Codex.**

---

Estamos en el proyecto `C:\Users\HNR05\Documents\Arquitectura-Veterinaria`. Tu auditoría P0 de solo lectura ya confirmó: Git sin commits, ECC nativo activo, paquete funcional completo (19 archivos; 18 cubiertos por SHA), motor Impeccable ausente y hook que lo invoca, AGENTS.md/PRODUCT.md inexistentes.

**AUTORIZACIÓN DE ESTA TAREA P1A:** preparación documental y de Git **conservadora**, limitada y reversible. No hay permiso para implementar la aplicación, instalar software, activar hooks, generar scaffold, mover los seis bloques, desplegar, subir a GitHub ni hacer commit hasta que apruebe el diff/staging.

### Instrucciones obligatorias
1. Antes de escribir, inspecciona de nuevo `git status --porcelain=v1 -uall`, `.gitignore`, `.codex/hooks.json`, `.agents`, `docs`, `design-system/arquitectura-veterinaria/MASTER.md`, archivos existentes y `arquitectura_veterinaria_v1_FINAL/`. Enumera archivos y sus roles. **Nunca sobrescribas por convención.**
2. Usa como autoridad `arquitectura_veterinaria_v1_FINAL/` y los dos documentos `*_v1_1.md`; no alteres los 19 archivos funcionales. No recrees el ZIP ni cambies nombres/IDs.
3. Compara los documentos de `docs/blueprints` y `docs/product-brief-questionnaire.md` con V1; prepara ajustes mínimos que marquen lo ya definido y lo todavía pendiente (arquitectura física, stack, NFR, etc.), conservando el historial. No afirmes que el código existe.
4. Preparar `AGENTS.md` en raíz con precedencia de documentos, protocolos, límites de V1, política Git, agentes y skills, pruebas y prohibición de programar hasta G3. Preparar `PRODUCT.md` con información aprobada y sin estilo visual inventado. Revisar propuestas que se adjunten, adaptar a lo realmente existente y enseñar diff. `DESIGN.md` sigue pendiente de UX; no lo generes a partir de los diales provisionales como si estuviera aprobado.
5. Revisar `.gitignore` para excluir secretos (`.env`, `.env.*` excepto plantillas explícitas), dependencias y cachés del stack **sin ignorar a ciegas** `.agents/`, `.codex/`, specs ni documentación. La regla anterior de `*.png/*.webm/*.mp4` puede ocultar capturas oficiales: **proponer** una corrección estrecha, dejando constancia de la preferencia antigua. Solicita aprobación antes de alterarla.
6. Seguridad de hooks: Impeccable apunta a un motor inexistente. **No ejecutes ni habilites esos hooks.** Inspecciona y presenta una propuesta reversible para mantenerlos no confiados/deshabilitados antes de cualquier edición que pueda dispararlos. Si el mecanismo de desactivación es ambiguo, DETENTE antes de tocar archivos. Nunca borres hooks ni bajes ejecutables por tu cuenta.
7. Inspecciona nombres y tamaños de potenciales secretos, cachés, binarios y archivos temporales ANTES de preparar staging. No muestres el valor de secretos en logs. Explica qué carpetas versionar y cuáles no.
8. Verifica el material funcional y su manifiesto sin modificarlo. Si viene un plan maestro en `docs/plan-maestro-codex/`, identifícalo como PLANIFICACIÓN y no como una especificación funcional sustitutiva.
9. Prepara un **paquete de diff y staging propuesto**, con rutas explícitas que versionarías; no ejecutes `git add .`, `git commit`, `git push`, `git reset`, `git clean`, inicializadores, migraciones ni instalaciones.
10. Si alguna skill ECC aporta revisión de solo lectura, úsala únicamente si la sesión realmente puede invocarla. Reporta cuáles empleaste. No simules subagentes.

### Entrega obligatoria
A) tabla de archivos: existente/creado/modificado/no tocado; B) cambios propuestos con diff; C) informe `.gitignore`+secretos y riesgos; D) estado seguro de hooks; E) manifest verificable de V1; F) plan exacto de staging y primer commit sin ejecutarlo; G) comprobaciones efectivas, fallos y evidencias; H) cuestiones que requieren mi autorización explícita. **DETENTE al finalizar P1A.**

### Gate
P1A se aceptará solamente si el paquete funcional está intacto, el diff es pequeño, no hay secretos y nada instaló ni modificó involuntariamente herramientas. No avances a P1B o P2 sin mi permiso.
