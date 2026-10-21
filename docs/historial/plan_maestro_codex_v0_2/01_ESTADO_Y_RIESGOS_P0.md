# P0 — Resultado consolidado y riesgos

**Fuente:** informe de solo lectura copiado por el usuario de su sesión Codex; no se afirma acceso remoto al PC.

| Superficie | Veredicto P0 | Acción P1 |
|---|---|---|
| Git | `master`, sin commits, todo no rastreado | inspección anti-secretos, línea base reproducible, commit SOLO tras revisión |
| Node/npm/npx | 22.20.0 / 10.9.3; PowerShell Restricted | conservar, usar wrappers `.cmd` si se necesita |
| Python | 3.12.10, 3.14.2 también presente | usar Python 3.12 explícito para scripts si aplica; verificar `py -3.12` |
| Codex | App; CLI bloqueada por sandbox específico | no diagnosticar instalación rota sin nuevo test en contexto confiable |
| ECC Affaan | `ecc@ecc` nativo 2.2.3, skills y 30 herramientas | conservar; no `ecc-universal` duplicado; verificar cómo invocar agentes realmente |
| Agentes ECC | plantillas explorer/reviewer/docs-researcher, no agentes registrados | preparar configuración por proyecto SOLO si soportada por la versión instalada |
| Agent Browser | CLI 0.27.0, Chromium instalado | no bloquear P1 por E2E; smoke en fase pertinente |
| UI UX Pro Max | CLI 2.15.0, MASTER.md preexistente | conservar y etiquetar como dirección provisional |
| Impeccable | launcher/skill/hook, motor ausente | BLOQUEANTE ANTES DE HABILITAR HOOK: revisar hook y resolver motor mediante fuente verificada |
| Motion | disponible | invocar para motion cuando exista UI |
| Emil | animation-vocabulary usable; otras ausentes | no instalar masivamente |
| Taste | no está | opcional; revisión estética solo si aporta valor |
| img2threejs | incompleto | excluido de puerta G1 porque V1 no necesita 3D |
| `docs/blueprints`, cuestionario | desactualizados | corregir tras comparar con V1; dejar rastro |
| `AGENTS.md`, `PRODUCT.md`, `DESIGN.md` | ausentes | preparar AGENTS/PRODUCT; DESIGN post aprobación UX |
| `.gitignore` | ignora visuales globalmente; falta .env/dependencias del stack | revisión limitada y consensuada; no versionar secretos ni ocultar evidencia útil |
| Paquete funcional | 19 archivos; hash cubre 18 | declarar inmutable, conservar intacto |

## Severidad de riesgos
- **R1 crítico operativo**: sin commit inicial, ninguna reversión a una línea base identificable.
- **R2 alto**: hooks Impeccable referencian motor ausente. No activarlos antes de reparar o dejarlos explícitamente inactivos.
- **R3 alto**: `git add .` podría incluir cachés/archivos con secretos o ejecutables; revisar staging por ruta.
- **R4 medio**: referencias antiguas a producto indefinido contradicen V1 actual.
- **R5 medio**: imágenes de evidencia legítima ignoradas por `*.png`, etc.
- **R6 medio**: ECC tiene agentes de plantilla; no prometer paralelismo efectivo hasta confirmar capacidad actual.

## Evidencia para salir de P1
E1 status Git y diff; E2 lista de archivos de baseline y exclusiones; E3 detección/verificación de ECC; E4 Impeccable ejecutable O hooks de proyecto no confiados/deshabilitados de manera explícita; E5 borradores AGENTS/PRODUCT revisados; E6 skills activables por tarea y registro de opcionales; E7 primer commit revisado y hash del commit; E8 instrucciones de reproducción y límites.
