# G3 — Trazabilidad, cobertura y gaps heredados

## Cobertura verificable

La matriz G3 contiene 118 filas, una por RF de V1: B01=35, B02=16, B03=16, B04=15, B05=20 y B06=16. Cada una conserva CU y criterios del CSV inmutable. Los perfiles del backlog aportan módulos, owner, dependencias, contratos, pruebas, evidencia, riesgo y DoD.

La V1 declara 68 CU, mientras su matriz canónica enlaza 65 CU distintos a RF. No se inventan relaciones para los tres casos siguientes:

| CU declarado sin RF explícito en matriz | Tratamiento G3 | Condición de cierre |
|---|---|---|
| CU-B03-09 — confirmar/contraproponer grooming | contexto operativo `PENDIENTE_DE_MAPEO_FUNCIONAL` | decisión del responsable que confirme el/los RF aplicables |
| CU-B04-07 — devolución física POS | contexto operativo `PENDIENTE_DE_MAPEO_FUNCIONAL` | decisión del responsable que confirme el/los RF aplicables |
| CU-B06-07 — reactivar tenant sin reejecutar historia | contexto operativo `PENDIENTE_DE_MAPEO_FUNCIONAL` | decisión del responsable que confirme el/los RF aplicables |

Estos gaps no alteran V1 ni se declaran cubiertos por similitud. Las tareas que los toquen incorporan el CU como contexto y permanecen abiertas hasta mapeo explícito.

## Evidencia por nivel

`RF → CU/CA V1 → tarea G4 → commit/diff → prueba ejecutada → evidencia sintética → revisión`. Un criterio V1 puede aparecer en varios RF; la evidencia enumera todas las relaciones, no solo la primera. B01 conserva fuentes históricas agrupadas, por lo que una tarea debe enlazar además el párrafo/tabla V1 concreto y no atribuir una DEC individual sin prueba de procedencia.

## G4-I00 — estado de evidencia limitado

G4-I00 aporta código y pruebas unitarias para un vertical técnico de sesión laboral, token opaco/CSRF, throttle local, contexto de sesión y perfil del tenant propio. La evidencia de compilación y pruebas se registra en `docs/audits/G4-I00_PRECHECK.md`.

No se marca ningún RF como cerrado todavía. `RF-AUT-01`, `RF-AUT-04`, `RF-AUT-05`, `RF-AUT-10`, `RF-AUT-13` y porciones de `RF-B06-001/002` solo podrán asociarse cuando existan: migración ejecutada sobre base exclusiva, login con datos sintéticos, autorización real, rechazo cross-tenant y E2E registrados. El vertical no cubre cuentas de cliente, suspensión, recuperación, alta de tenant ni capacidades de I01–I06.
