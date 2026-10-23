# G3 — Plan orquestado de implementación

## Estado

G3 define una ruta para implementar el producto definitivo. No equivale a autorización de ejecutar G4: cada incremento exige autorización expresa, preflight de herramientas y una tarea con contrato cerrado. La primera tarea propuesta se encuentra en `02_INCREMENTOS_Y_RUTA_CRITICA.md`.

## Documentos

1. `01_BACKLOG_INTEGRAL_V1.md`: cobertura de las 118 filas RF, mediante perfiles de ejecución resueltos.
2. `02_INCREMENTOS_Y_RUTA_CRITICA.md`: orden, demo y dependencias.
3. `03_CONTRATO_DE_TAREA.md`: plantilla obligatoria de 18 campos.
4. `04_TRAZABILIDAD_Y_GAPS.md`: controles de cobertura, incluidos tres gaps heredados.
5. `05_ORQUESTACION_Y_OWNERSHIP.md`: responsables, skills y exclusión de escrituras incompatibles.
6. `06_TRAZABILIDAD_B01_G4_I01.md`: estado verificable de los 35 RF de B01
   durante G4-I01; no sustituye la matriz funcional inmutable ni declara cierre.

## Definición de terminado transversal

Una tarea solo se cierra si enlaza RF/CU/criterios V1, cumple autorización/tenant/transición, tiene pruebas proporcionalmente ejecutadas, errores previsibles, evidencia sintética, revisión independiente y diff sin secretos. No se cierra por crear archivos ni por una respuesta de un agente.
