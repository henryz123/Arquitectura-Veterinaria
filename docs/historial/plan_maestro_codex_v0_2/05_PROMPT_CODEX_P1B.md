# MENSAJE PARA CODEX — P1B / Remediación selectiva del toolkit

**USAR SOLO tras revisión y aprobación independiente del resultado de P1A. NO PEGAR AHORA.**

Proyecto Arquitectura - Veterinaria. P1A fue auditado y aprobado. Ahora analiza y remedia exclusivamente problemas verificables del toolkit. Para cada componente: estado previo, fuente oficial/versiones, cambios previstos, dependencia, método de instalación y rollback, evidencias postinstalación.

1. ECC Affaan: `ecc@ecc` nativo es fuente primaria. Solo verificar funcionamiento/detección y si agentes locales pueden registrarse sin duplicar. **Prohibido instalar ECC universal/sync encima**.
2. Impeccable: motor falta pero hook existe. Revisar documentación upstream autenticada, release exacta fijada y ejecutable Windows; diagnosticar permisos/antivirus/red y riesgos de binario auto-descargable. Mostrar plan de reparación; instalar únicamente después de autorización específica para descarga/ejecución de binarios. Probar versión y smoke no destructivo ANTES de habilitar hooks. Si no es posible, mantener hooks sin habilitar, documentar bloqueo y alternativa manual para Product Brief.
3. Evaluar `commandWindows`, runtime, `PostToolUse` de 5 s, `Stop`, trust prompts. No alterar hooks globales sin permiso; no fingir prueba exitosa si no se ejecuta.
4. Node/Python: conservar 22.20.0 y 3.12.10; usar `.cmd` para npm/npx en PowerShell Restricted. No cambiar políticas de ejecución globales.
5. UI UX Pro Max, Agent Browser y Motion: conservar; smoke mínimo permitido según contexto. Taste, Emil adicionales y img2threejs NO son bloqueantes; proponer solo si una tarea concreta los requiere.
6. Crear inventario final instalado/operativo/opcional y cómo Codex descubrirá cada skill al iniciar nueva sesión.
7. Entregar diff, logs limpios, pruebas, riesgos y rollback. No iniciar scaffold/app, no seleccionar framework/BD ni comenzar codificación.

**Detente antes de G2 y presenta informe.**
