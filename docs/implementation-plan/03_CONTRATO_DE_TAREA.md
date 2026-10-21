# G3 — Contrato obligatorio de tarea

Cada tarea G4 se registra antes de editar con los siguientes 18 campos:

1. Identificador y título.
2. Descripción y objetivo verificable.
3. Contexto de negocio y tenant.
4. Fuente V1: RF, CU, criterios y regla aplicable.
5. ADR/documentación de referencia.
6. Dependencias satisfechas y preflight.
7. Resultado esperado y no-resultados.
8. Responsable de escritura y revisor independiente.
9. Skills/herramientas realmente disponibles que se evaluarán.
10. Archivos autorizados y propietario de cada conjunto compartido.
11. Archivos/procesos prohibidos.
12. Comandos permitidos y entorno de datos sintéticos.
13. Contratos API/eventos/migraciones afectados.
14. Criterios de aceptación técnicos y funcionales.
15. Pruebas obligatorias: unidad, integración, contrato, seguridad, concurrencia, E2E según riesgo.
16. Evidencia exigida y ubicación sin secretos.
17. Riesgos, límites, rollback y condición de bloqueo.
18. Condiciones de cierre, diff revisado y actualización de trazabilidad.

## Regla de autorización

Una aprobación de G3 no habilita una tarea no descrita ni expande archivos autorizados. La tarea se detiene ante contradicción V1, hallazgo de secreto, migración irreversible no aprobada, permisos insuficientes, proveedor/coste o publicación externa.
