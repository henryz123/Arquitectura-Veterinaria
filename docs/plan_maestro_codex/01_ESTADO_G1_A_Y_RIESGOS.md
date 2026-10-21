# Estado consolidado y riesgos — V0.4

**Fecha de consolidación:** 21 de octubre de 2026.

## Hechos verificados

| Superficie | Estado |
|---|---|
| Repositorio | Rama `master`, baseline documental local `e2a8ab9943da38244468ecfb167d9ad5b64774e9`, sin remoto; `.agents/` local queda ignorado. |
| Paquete funcional V1 | 19 archivos presentes; el manifiesto cubre 18 artefactos y se vuelve a verificar antes de cerrar G1. No se modifica. |
| Documentación P1A/P1B/G2/G3 | `AGENTS.md`, `PRODUCT.md`, auditoría de ecosistema, arquitectura, ADR y backlog existen; el software no existe. |
| Impeccable | El launcher y la skill existen, pero el motor no fue validado. `.codex/hooks.json` permanece ausente del punto activo; el respaldo P1A se conserva bajo `.codex/backup/`, ignorado por Git. |
| ECC | `ecc@ecc` nativo es la ruta primaria. Las plantillas detectadas no prueban que haya agentes registrados invocables. |
| Diseño | `MASTER.md` existe y sigue siendo provisional. |
| Aplicación | No hay scaffold ni código de aplicación; G4 sigue pendiente de autorización. |

## Riesgos activos

1. **Calendario crítico:** quedan cinco días calendario para la presentación del 26/10/2026. La arquitectura no habilita G4 ni justifica omitir pruebas.
2. **Hook incompleto:** ejecutar el launcher de Impeccable podría descargar un motor no validado. Se mantiene aislado hasta aprobación específica.
3. **Entorno incompleto:** JDK 21, Maven, Angular CLI y Docker no están disponibles; instalar/configurar requiere tarea autorizada.
4. **Publicación:** una URL y un repositorio públicos requieren revisión de secretos, licencia/procedencia de terceros, datos sintéticos y evidencia de pruebas; no se conecta GitHub en G1-A.
5. **Alcance:** V1 contiene 118 requisitos; la demo no puede representarse como cobertura total si solo prueba flujos verticales priorizados.

## Condición para entrar a G4

Antes de iniciar el primer incremento: autorización G4-I01-T01, JDK 21/Maven/Angular CLI disponibles y versionados por evidencia, contrato/migración bajo tarea controlada y entorno de pruebas sintético. La publicación continúa bloqueada.
