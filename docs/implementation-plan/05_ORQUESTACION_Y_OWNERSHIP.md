# G3 — Orquestación, agentes y ownership

## Responsables por conjunto de archivos

| Área | Responsable de escritura | Revisión requerida | Escritura concurrente prohibida |
|---|---|---|---|
| ADR, módulos y contratos | arquitecto/owner técnico | seguridad + contrato | OpenAPI/ADR compartido |
| Backend de módulo | backend | revisor Java y seguridad cuando aplique | agregados/repositorios de otro módulo |
| Frontend Angular | frontend | a11y/UX y contrato | modelos/contratos sin coordinación |
| Migraciones/índices | DB owner | backend + seguridad | cualquier segunda migración concurrente |
| Infra/CI | DevOps owner | seguridad | secretos, despliegue o workflows compartidos |
| Evidencia/QA | QA owner | responsable de módulo | reescribir resultados de pruebas |

## Uso real de capacidades

ECC nativo se utiliza mediante skills detectadas para arquitectura, ADR, contratos, API, PostgreSQL, Angular, pruebas y accesibilidad. Sus plantillas no se presentan como agentes invocados. Revisores reales pueden operar solo lectura y deben declarar sus comandos/evidencia. El `planner` del runtime puede descomponer una tarea, pero no se atribuye a ECC.

Impeccable queda fuera de automatización hasta completar su procedimiento P1B. UI UX Pro Max y Design Motion Principles son guías para superficies autorizadas; Taste/img2threejs/Emil independiente son opcionales ausentes. Agent Browser se reserva para aplicación y navegador comprobados.

## Orden de una cadena futura

1. Owner de tarea formula contrato y abre prueba roja.
2. Owner implementa el mínimo verde dentro de sus archivos autorizados.
3. Revisor independiente valida diff, contrato, seguridad y pruebas reales.
4. QA ejecuta integración/E2E proporcional y registra evidencia.
5. Un único owner actualiza trazabilidad y propone staging explícito.

No se emplean cadenas automáticas que creen cambios sin revisión humana. El uso de `/ecc:orchestrate` se condiciona a que el runtime confirme los agentes nombres/namespace antes de emitir el comando; hoy no existe esa confirmación.
