# Estado G1-A y riesgos — V0.3

**Fecha de consolidación:** 21 de octubre de 2026.

## Hechos verificados

| Superficie | Estado |
|---|---|
| Repositorio | Git sin commits; el primer índice se prepara mediante rutas explícitas y queda pendiente de aprobación para commit. |
| Paquete funcional V1 | 19 archivos presentes; el manifiesto cubre 18 artefactos y se vuelve a verificar antes de cerrar G1. No se modifica. |
| Documentación P1A | `AGENTS.md`, `PRODUCT.md`, `.gitignore`, el blueprint y el cuestionario existen; se concilian con V0.3 durante G1-A. |
| Impeccable | El launcher y la skill existen, pero el motor no fue validado. `.codex/hooks.json` permanece ausente del punto activo; el respaldo P1A se conserva bajo `.codex/backup/`, ignorado por Git. |
| ECC | `ecc@ecc` nativo es la ruta primaria. Las plantillas detectadas no prueban que haya agentes registrados invocables. |
| Diseño | `MASTER.md` existe y sigue siendo provisional. |
| Aplicación | No hay scaffold ni código de aplicación autorizado. |

## Riesgos activos

1. **Calendario crítico:** quedan cinco días calendario para la presentación del 26/10/2026. Sin autorización G3 no hay base legítima para empezar desarrollo; no se compensará omitiendo pruebas o controles.
2. **Hook incompleto:** ejecutar el launcher de Impeccable podría descargar un motor no validado. Se mantiene aislado hasta P1B y autorización específica.
3. **Línea base nueva:** un staging amplio podría exponer dependencias, ejecutables o contenido de terceros. Se excluyen `.agents/` y `.codex/backup/` del primer índice y se revisan solo rutas conocidas.
4. **Publicación:** una URL y un repositorio públicos requieren revisión de secretos, licencia/procedencia de terceros, datos sintéticos y evidencia de pruebas; no se conecta GitHub en G1-A.
5. **Alcance:** V1 contiene 118 requisitos; la demo no puede representarse como cobertura total si solo prueba flujos verticales priorizados.

## Condición de salida G1-A

La salida de G1-A consiste en documentación coherente, integridad funcional verificada, hook inactivo, revisión de secretos y un índice Git revisado. El commit inicial sigue bloqueado hasta aprobación humana explícita del diff y de la lista de rutas staged.
