# PM-CODEX-001 — Plan maestro refinado V0.2 | Arquitectura - Veterinaria

**Fecha:** 2026-10-21. **Estado:** documento de planificación listo para ejecutar por puertas, NO aplicación lista, NO stack aprobado.

## Qué se verificó realmente
- El responsable mostró la raíz de su repositorio de Windows y aportó informe P0 de Codex de **solo lectura**.
- El informe P0 declara: Git 2.51.0 sin commits; Node 22.20.0; Python 3.12.10 y 3.14.2; ECC nativo `ecc@ecc` activo; Agent Browser y UI UX Pro Max activos; Impeccable con motor ausente y hooks que lo llaman; Taste, Emil e img2threejs incompletos/ausentes.
- Codex verificó el manifiesto del paquete funcional: **18/18 archivos cubiertos por SHA-256**, más el propio manifiesto (19 archivos), en `arquitectura_veterinaria_v1_FINAL/`.
- Esta respuesta no accedió al PC Windows; los estados locales provienen de la auditoría reportada por Codex. Se comprobó de manera independiente aquí el contenido de los artefactos compartidos con ChatGPT.

## Carpetas de destino: no reorganizar sin motivo
```text
Arquitectura-Veterinaria/
  .agents/                                    # skills de proyecto, preservar
  .codex/                                     # configuración/hook; preservar y revisar
  .gitignore                                 # revisar antes de cambiar
  arquitectura_veterinaria_v1_FINAL/          # BASE FUNCIONAL: INMUTABLE
  design-system/                              # conservar MASTER.md; estilo provisional
  docs/
    blueprints/                               # documentos antiguos: conciliar con V1
    plan-maestro-codex/                       # AQUI colocar este plan V0.2
    architecture/                             # futuro: ADR/C4/arc42, aún no crear implementación
  instrucciones_maestras_chatgpt_v1_1.md
  manual_protocolo_integral_chatgpt_v1_1.md
  AGENTS.md                                   # crear solo tras revisión; borrador adjunto
  PRODUCT.md                                  # crear solo tras revisión; borrador adjunto
  DESIGN.md                                   # crear en fase UX, no ahora
```

## Qué copiar a Windows
**Copiar únicamente esta carpeta V0.2 bajo `docs/plan-maestro-codex/`, SIN sobrescribir documentos actuales.** No copiar V0.1 como autoridad paralela. No duplicar B01–B06. No dejar archivos `.zip` temporalmente fuera de control ni dar por hecho que un `AGENTS.md` borrador ya está activo.

## Orden operativo
1. Leer `01_ESTADO_Y_RIESGOS_P0.md`.
2. Revisar `02_PLAN_MAESTRO_Y_GATES.md` y `03_ORQUESTACION_Y_SKILLS.md`.
3. **Obtener aprobación del responsable para P1A** y enviar a Codex `04_PROMPT_CODEX_P1A.md`. P1A prepara cambios conservadores, verifica diff y **NO realiza commits ni instalaciones externas**.
4. Revisar informe de P1A. Autorizar por separado `05_PROMPT_CODEX_P1B.md` para Impeccable/hook y smoke tests. P1B requiere inspeccionar procedencia y advertir si hay descarga de ejecutables.
5. Preparar y aprobar línea base Git con nombres de archivos exactos, revisión de secretos y commit inicial. No usar `git add .` a ciegas.
6. Aprobar G1 antes de abrir P2: seguridad/NFR, ADR stack, diseño C4/arc42, modelo físico BD, UX/infra, plan agente por incremento.
7. Solo tras G2+G3 y autorización explícita: implementación incremental.

## Jerarquía de autoridad
Instrucción expresa actual del responsable > línea base funcional V1 + decisiones aprobadas > normas de ingeniería V1.1 > ADR posteriormente aprobadas > propuestas técnicas. Documentar contradicciones, nunca editar líneas base funcionales por implicación.

## Prohibiciones
No iniciar producto; no desplegar; no cargar datos reales; no instalar packs de skills redundantes; no asumir equivalencia de hooks Claude/Codex; no desactivar salvaguardas sin revisión; no `git reset --hard`, `git clean -fd`, `git push`, `--force`, ni modificaciones masivas no inspeccionadas.
