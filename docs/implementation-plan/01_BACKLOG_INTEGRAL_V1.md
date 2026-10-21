# G3 — Backlog integral V1 (118 requisitos)

La matriz V1 inmutable (`arquitectura_veterinaria_v1_FINAL/08_TRAZABILIDAD_Y_PRUEBAS.csv`) conserva descripción, caso de uso y criterio funcional original. Esta matriz añade ejecución técnica sin reinterpretar esos campos. Cada fila hereda **todos** los campos del perfil indicado: prioridad, dependencia, módulo, archivos objetivo, responsable, skills, contrato, pruebas, evidencia, riesgo y DoD.

## Perfiles de ejecución resueltos

| Perfil | Prioridad / incremento / dependencias | Módulo, propietario y skills | Contrato y archivos objetivo (no creados) | Pruebas, evidencia, riesgo y DoD |
|---|---|---|---|---|
| P-REG | P0 · I01 · I00 | `tenant-admin` + `identity-access`; backend; `ecc:contract-first`, `ecc:java-coding-standards`, `ecc:security` | `/api/v1/tenants`, `/api/v1/ruc-reviews`; `backend/.../tenant-admin`, `identity-access`, `docs/contracts/openapi-v1.yaml` | unidad/DB/contrato/autorización; salida de test+traceId; alto; tenant/RUC/estado/auditoría y CA V1 reales |
| P-AUTH | P0 · I01 · I00 | `identity-access`; backend+security; `ecc:backend-patterns`, `ecc:api-design`, `ecc:agentic-engineering` | `/api/v1/auth/*`; `backend/.../identity-access`, `frontend/.../features/auth` | unidad/sesión/CSRF/negativas; evidencia de pruebas; crítico; cuenta contextual sin filtración ni sesión compartida |
| P-STAFF | P0 · I01 · P-AUTH | `identity-access`; backend+frontend; `ecc:contract-first`, `ecc:angular-developer`, `ecc:security` | `/api/v1/staff-accounts`; módulos identidad/UI de personal | unidad/integración de roles/último admin; evidencia de autorización; crítico; roles/revocación/casos V1 |
| P-CLIENT | P0 · I01 · P-AUTH | `identity-access`; backend+frontend; `ecc:contract-first`, `ecc:angular-developer`, `ecc:frontend-a11y` | `/api/v1/client-accounts`; identidad/UI de cliente | unidad/integración/portal negativo; evidencia sintética; alto; cliente solo ve datos propios |
| P-CARE | P1 · I02 · I01 | `owner-pet-care`; backend+frontend; `ecc:hexagonal-architecture`, `ecc:api-design`, `ecc:accessibility` | `/api/v1/owners`, `/pets`, `/clinical-episodes`; módulo care/UI | unidad, PostgreSQL, permisos y E2E; evidencia clínica sintética; crítico; titularidad, adendas y privacidad |
| P-SCHEDULE | P1 · I03 · I02 | `scheduling`; backend+frontend+DB; `ecc:postgres-patterns`, `ecc:contract-first`, `ecc:e2e-testing` | `/api/v1/availability`, `/appointments`, `/grooming-services`; módulo agenda/UI | unidad, integración rango/lock, E2E; reporte concurrencia; crítico; sin traslape y cambios trazables |
| P-INVENTORY | P0 · I04 · I01 | `catalog-inventory`; backend+DB; `ecc:postgres-patterns`, `ecc:api-design`, `ecc:agent-eval` | `/api/v1/products`, `/inventory`; módulo stock, migraciones futuras | unidad, PostgreSQL concurrencia/idem; log de movimientos; crítico; saldo/movimiento único/auditoría |
| P-POS | P0 · I04 · P-INVENTORY | `commerce`; backend+frontend; `ecc:contract-first`, `ecc:angular-developer`, `ecc:e2e-testing` | `/api/v1/pos-sales`; commerce/UI POS | unidad, contrato, stock real y E2E; recibo interno sintético; crítico; pago manual y venta no duplicada |
| P-ORDER | P1 · I05 · P-INVENTORY,P-CLIENT | `commerce`; backend+frontend; `ecc:api-design`, `ecc:postgres-patterns`, `ecc:browser-qa` | `/api/v1/public/tenants/{slug}`, `/carts`, `/orders`; commerce/UI | unidad, stock/concurrencia, contrato/E2E; estado pedido; crítico; snapshots, pago y devolución V1 |
| P-CROSS | P1 · I06 · I01-I05 | `notifications-reporting` + todos; architect/QA/security; `ecc:architecture-decision-records`, `ecc:e2e-testing`, `ecc:accessibility` | dashboard/report/audit/outbox; contratos y operaciones | integración transversal, a11y, seguridad; matriz/evidencia; alto; tenant, auditoría, tiempos y datos sintéticos |

**Convención de archivos:** cada perfil autoriza solamente los directorios objetivo indicados, sus pruebas homólogas, contrato OpenAPI y documentación de evidencia. Una tarea concreta reduce esa lista; no permite crear el árbol ahora. `security` o `browser-qa` se evalúan solo si están realmente disponibles; su nombre no declara un agente ejecutado.

## Matriz completa RF → perfil técnico

| ID de tarea | Bloque | RF V1 | CU V1 | Criterios V1 | Perfil |
|---|---|---|---|---|---|
| G3-RF-001 | B01 | RF-SAA-01 | CU-SAA-01 | CA-SAA-01;CA-SAA-02 | P-REG |
| G3-RF-002 | B01 | RF-SAA-02 | CU-SAA-01 | CA-SAA-03;CA-SAA-14;CA-SAA-02 | P-REG |
| G3-RF-003 | B01 | RF-SAA-03 | CU-SAA-02 | CA-SAA-07;CA-SAA-15;CA-SAA-11 | P-REG |
| G3-RF-004 | B01 | RF-SAA-04 | CU-SAA-03 | CA-SAA-06 | P-REG |
| G3-RF-005 | B01 | RF-SAA-05 | CU-SAA-04 | CA-SAA-08 | P-REG |
| G3-RF-006 | B01 | RF-SAA-06 | CU-SAA-05 | CA-SAA-21 | P-REG |
| G3-RF-007 | B01 | RF-SAA-07 | CU-SAA-06 | CA-SAA-03;CA-SAA-04 | P-REG |
| G3-RF-008 | B01 | RF-SAA-08 | CU-SAA-07 | CA-SAA-12;CA-SAA-09;CA-SAA-13 | P-REG |
| G3-RF-009 | B01 | RF-SAA-09 | CU-SAA-01 | CA-SAA-05 | P-REG |
| G3-RF-010 | B01 | RF-SAA-10 | CU-SAA-03 | CA-SAA-06 | P-REG |
| G3-RF-011 | B01 | RF-SAA-11 | CU-SAA-01;CU-SAA-02 | CA-SAA-10;CA-SAA-14;CA-SAA-18;CA-SAA-19 | P-REG |
| G3-RF-012 | B01 | RF-SAA-12 | CU-SAA-07 | CA-SAA-16;CA-SAA-17;CA-SAA-20 | P-REG |
| G3-RF-013 | B01 | RF-SAA-13 | CU-SAA-08 | CA-SAA-22;CA-SAA-23;CA-SAA-24 | P-REG |
| G3-RF-014 | B01 | RF-AUT-01 | CU-AUT-01 | CA-AUT-09 | P-AUTH |
| G3-RF-015 | B01 | RF-AUT-02 | CU-AUT-02 | CA-AUT-16 | P-AUTH |
| G3-RF-016 | B01 | RF-AUT-03 | CU-AUT-03 | CA-AUT-03 | P-AUTH |
| G3-RF-017 | B01 | RF-AUT-04 | CU-AUT-01 | CA-AUT-04;CA-AUT-05 | P-AUTH |
| G3-RF-018 | B01 | RF-AUT-05 | CU-AUT-01 | CA-AUT-01;CA-AUT-02 | P-AUTH |
| G3-RF-019 | B01 | RF-AUT-06 | CU-SAA-08 | CA-AUT-06 | P-AUTH |
| G3-RF-020 | B01 | RF-AUT-07 | CU-AUT-01 | CA-AUT-02 | P-AUTH |
| G3-RF-021 | B01 | RF-AUT-08 | CU-AUT-03 | CA-AUT-03 | P-AUTH |
| G3-RF-022 | B01 | RF-AUT-09 | CU-SAA-02 | CA-AUT-07;CA-AUT-10 | P-AUTH |
| G3-RF-023 | B01 | RF-AUT-10 | CU-AUT-01 | CA-AUT-09 | P-AUTH |
| G3-RF-024 | B01 | RF-AUT-11 | CU-AUT-03;CU-USR-01;CU-SAA-02;CU-CLI-01 | CA-AUT-11;CA-AUT-12;CA-AUT-13;CA-AUT-14;CA-AUT-08 | P-AUTH |
| G3-RF-025 | B01 | RF-AUT-12 | CU-AUT-01 | CA-AUT-15 | P-AUTH |
| G3-RF-026 | B01 | RF-AUT-13 | CU-AUT-03;CU-USR-03;CU-SAA-04 | CA-AUT-16 | P-AUTH |
| G3-RF-027 | B01 | RF-USR-01 | CU-USR-01 | CA-USR-01 | P-STAFF |
| G3-RF-028 | B01 | RF-USR-02 | CU-USR-01 | CA-USR-01;CA-USR-02 | P-STAFF |
| G3-RF-029 | B01 | RF-USR-03 | CU-USR-02 | CA-USR-07 | P-STAFF |
| G3-RF-030 | B01 | RF-USR-04 | CU-USR-03 | CA-USR-05;CA-USR-06;CA-USR-08 | P-STAFF |
| G3-RF-031 | B01 | RF-USR-05 | CU-USR-01;CU-AUT-01 | CA-USR-03;CA-USR-04;CA-USR-05 | P-STAFF |
| G3-RF-032 | B01 | RF-CLI-01 | CU-CLI-01 | CA-CLI-01 | P-CLIENT |
| G3-RF-033 | B01 | RF-CLI-02 | CU-CLI-01 | CA-AUT-05 | P-CLIENT |
| G3-RF-034 | B01 | RF-CLI-03 | CU-CLI-02 | CA-CLI-01 | P-CLIENT |
| G3-RF-035 | B01 | RF-CLI-04 | CU-CLI-02 | CA-CLI-01 | P-CLIENT |
| G3-RF-036 | B02 | RF-B02-001 | CU-B02-01 | AT-B02-01 | P-CARE |
| G3-RF-037 | B02 | RF-B02-002 | CU-B02-02 | AT-B02-02 | P-CARE |
| G3-RF-038 | B02 | RF-B02-003 | CU-B02-10 | AT-B02-02;AT-B02-08 | P-CARE |
| G3-RF-039 | B02 | RF-B02-004 | CU-B02-03 | AT-B02-03;AT-B02-13 | P-CARE |
| G3-RF-040 | B02 | RF-B02-005 | CU-B02-04 | AT-B02-03;AT-B02-13 | P-CARE |
| G3-RF-041 | B02 | RF-B02-006 | CU-B02-04 | AT-B02-03 | P-CARE |
| G3-RF-042 | B02 | RF-B02-007 | CU-B02-05 | AT-B02-07;AT-B02-09 | P-CARE |
| G3-RF-043 | B02 | RF-B02-008 | CU-B02-06 | AT-B02-04;AT-B02-05 | P-CARE |
| G3-RF-044 | B02 | RF-B02-009 | CU-B02-06 | AT-B02-10;AT-B02-14 | P-CARE |
| G3-RF-045 | B02 | RF-B02-010 | CU-B02-08 | AT-B02-11 | P-CARE |
| G3-RF-046 | B02 | RF-B02-011 | CU-B02-09 | AT-B02-11 | P-CARE |
| G3-RF-047 | B02 | RF-B02-012 | CU-B02-07 | AT-B02-10 | P-CARE |
| G3-RF-048 | B02 | RF-B02-013 | CU-B02-10 | AT-B02-08;AT-B02-12 | P-CARE |
| G3-RF-049 | B02 | RF-B02-014 | CU-B02-11 | AT-B02-03;AT-B02-13 | P-CARE |
| G3-RF-050 | B02 | RF-B02-015 | CU-B02-05 | AT-B02-07;AT-B02-09 | P-CARE |
| G3-RF-051 | B02 | RF-B02-016 | CU-B02-07 | AT-B02-06 | P-CARE |
| G3-RF-052 | B03 | RF-B03-001 | CU-B03-01 | AT-B03-01;AT-B03-03;AT-B03-04 | P-SCHEDULE |
| G3-RF-053 | B03 | RF-B03-002 | CU-B03-02 | AT-B03-01;AT-B03-09 | P-SCHEDULE |
| G3-RF-054 | B03 | RF-B03-003 | CU-B03-03 | AT-B03-01;AT-B03-14;AT-B03-15 | P-SCHEDULE |
| G3-RF-055 | B03 | RF-B03-004 | CU-B03-04 | AT-B03-05;AT-B03-06 | P-SCHEDULE |
| G3-RF-056 | B03 | RF-B03-005 | CU-B03-05 | AT-B03-05;AT-B03-08 | P-SCHEDULE |
| G3-RF-057 | B03 | RF-B03-006 | CU-B03-04 | AT-B03-02;AT-B03-03;AT-B03-05 | P-SCHEDULE |
| G3-RF-058 | B03 | RF-B03-007 | CU-B03-01 | AT-B03-04;AT-B03-03 | P-SCHEDULE |
| G3-RF-059 | B03 | RF-B03-008 | CU-B03-04 | AT-B03-02 | P-SCHEDULE |
| G3-RF-060 | B03 | RF-B03-009 | CU-B03-06 | AT-B03-07;AT-B03-08;AT-B03-13 | P-SCHEDULE |
| G3-RF-061 | B03 | RF-B03-010 | CU-B03-08 | AT-B03-09 | P-SCHEDULE |
| G3-RF-062 | B03 | RF-B03-011 | CU-B03-10 | AT-B03-10 | P-SCHEDULE |
| G3-RF-063 | B03 | RF-B03-012 | CU-B03-11 | AT-B03-11;AT-B03-12 | P-SCHEDULE |
| G3-RF-064 | B03 | RF-B03-013 | CU-B03-07 | AT-B03-10 | P-SCHEDULE |
| G3-RF-065 | B03 | RF-B03-014 | CU-B03-07 | AT-B03-13 | P-SCHEDULE |
| G3-RF-066 | B03 | RF-B03-015 | CU-B03-12 | AT-B03-07 | P-SCHEDULE |
| G3-RF-067 | B03 | RF-B03-016 | CU-B03-05 | AT-B03-05;AT-B03-06;AT-B03-07 | P-SCHEDULE |
| G3-RF-068 | B04 | RF-B04-001 | CU-B04-01 | AT-B04-06;AT-B04-07 | P-INVENTORY |
| G3-RF-069 | B04 | RF-B04-002 | CU-B04-02 | AT-B04-02;AT-B04-03 | P-INVENTORY |
| G3-RF-070 | B04 | RF-B04-003 | CU-B04-02 | AT-B04-01;AT-B04-13 | P-INVENTORY |
| G3-RF-071 | B04 | RF-B04-004 | CU-B04-04 | AT-B04-02;AT-B04-04;AT-B04-12 | P-POS |
| G3-RF-072 | B04 | RF-B04-005 | CU-B04-04 | AT-B04-02;AT-B04-03 | P-INVENTORY |
| G3-RF-073 | B04 | RF-B04-006 | CU-B04-03 | AT-B04-09;AT-B04-10;AT-B04-13 | P-INVENTORY |
| G3-RF-074 | B04 | RF-B04-007 | CU-B04-04 | AT-B04-07;AT-B04-12 | P-POS |
| G3-RF-075 | B04 | RF-B04-008 | CU-B04-04 | AT-B04-05;AT-B04-14 | P-POS |
| G3-RF-076 | B04 | RF-B04-009 | CU-B04-05 | AT-B04-12 | P-POS |
| G3-RF-077 | B04 | RF-B04-010 | CU-B04-06 | AT-B04-08;AT-B04-09;AT-B04-10 | P-POS |
| G3-RF-078 | B04 | RF-B04-011 | CU-B04-02 | AT-B04-01 | P-INVENTORY |
| G3-RF-079 | B04 | RF-B04-012 | CU-B04-01 | AT-B04-06;AT-B04-07 | P-INVENTORY |
| G3-RF-080 | B04 | RF-B04-013 | CU-B04-08 | AT-B04-13 | P-POS |
| G3-RF-081 | B04 | RF-B04-014 | CU-B04-03 | AT-B04-11 | P-INVENTORY |
| G3-RF-082 | B04 | RF-B04-015 | CU-B04-04 | AT-B04-05;AT-B04-14 | P-POS |
| G3-RF-083 | B05 | RF-B05-001 | CU-B05-01 | AT-B05-17;AT-B05-20 | P-ORDER |
| G3-RF-084 | B05 | RF-B05-002 | CU-B05-02 | AT-B05-01;AT-B05-02 | P-ORDER |
| G3-RF-085 | B05 | RF-B05-003 | CU-B05-03 | AT-B05-02;AT-B05-08 | P-ORDER |
| G3-RF-086 | B05 | RF-B05-004 | CU-B05-12 | AT-B05-16 | P-ORDER |
| G3-RF-087 | B05 | RF-B05-005 | CU-B05-04 | AT-B05-03;AT-B05-05;AT-B05-06 | P-ORDER |
| G3-RF-088 | B05 | RF-B05-006 | CU-B05-04 | AT-B05-03;AT-B05-04;AT-B05-05 | P-ORDER |
| G3-RF-089 | B05 | RF-B05-007 | CU-B05-04 | AT-B05-06;AT-B05-07 | P-ORDER |
| G3-RF-090 | B05 | RF-B05-008 | CU-B05-05 | AT-B05-07;AT-B05-08 | P-ORDER |
| G3-RF-091 | B05 | RF-B05-009 | CU-B05-07 | AT-B05-19 | P-ORDER |
| G3-RF-092 | B05 | RF-B05-010 | CU-B05-06 | AT-B05-18 | P-ORDER |
| G3-RF-093 | B05 | RF-B05-011 | CU-B05-08 | AT-B05-09 | P-ORDER |
| G3-RF-094 | B05 | RF-B05-012 | CU-B05-09 | AT-B05-10;AT-B05-11;AT-B05-12 | P-ORDER |
| G3-RF-095 | B05 | RF-B05-013 | CU-B05-09 | AT-B05-10;AT-B05-11 | P-ORDER |
| G3-RF-096 | B05 | RF-B05-014 | CU-B05-10 | AT-B05-12;AT-B05-13 | P-ORDER |
| G3-RF-097 | B05 | RF-B05-015 | CU-B05-10 | AT-B05-14;AT-B05-15 | P-ORDER |
| G3-RF-098 | B05 | RF-B05-016 | CU-B05-11 | AT-B05-08;AT-B05-16 | P-ORDER |
| G3-RF-099 | B05 | RF-B05-017 | CU-B05-11 | AT-B05-17 | P-ORDER |
| G3-RF-100 | B05 | RF-B05-018 | CU-B05-11 | AT-B05-19 | P-ORDER |
| G3-RF-101 | B05 | RF-B05-019 | CU-B05-03 | AT-B05-20 | P-ORDER |
| G3-RF-102 | B05 | RF-B05-020 | CU-B05-04 | AT-B05-04;AT-B05-21 | P-ORDER |
| G3-RF-103 | B06 | RF-B06-001 | CU-B06-06 | AT-B06-10;AT-B06-16 | P-CROSS |
| G3-RF-104 | B06 | RF-B06-002 | CU-B06-09 | AT-B06-01;AT-B06-02;AT-B06-03;AT-B06-08;AT-B06-09 | P-CROSS |
| G3-RF-105 | B06 | RF-B06-003 | CU-B06-05 | AT-B06-05;AT-B06-17 | P-CROSS |
| G3-RF-106 | B06 | RF-B06-004 | CU-B06-09 | AT-B06-07 | P-CROSS |
| G3-RF-107 | B06 | RF-B06-005 | CU-B06-09 | AT-B06-05;AT-B06-17 | P-CROSS |
| G3-RF-108 | B06 | RF-B06-006 | CU-B06-03 | AT-B06-13 | P-CROSS |
| G3-RF-109 | B06 | RF-B06-007 | CU-B06-02 | AT-B06-06 | P-CROSS |
| G3-RF-110 | B06 | RF-B06-008 | CU-B06-04 | AT-B06-06 | P-CROSS |
| G3-RF-111 | B06 | RF-B06-009 | CU-B06-01 | AT-B06-10 | P-CROSS |
| G3-RF-112 | B06 | RF-B06-010 | CU-B06-01 | AT-B06-12 | P-CROSS |
| G3-RF-113 | B06 | RF-B06-011 | CU-B06-09 | AT-B06-04;AT-B06-12 | P-CROSS |
| G3-RF-114 | B06 | RF-B06-012 | CU-B06-09 | AT-B06-11 | P-CROSS |
| G3-RF-115 | B06 | RF-B06-013 | CU-B06-08 | AT-B06-14 | P-CROSS |
| G3-RF-116 | B06 | RF-B06-014 | CU-B06-09 | AT-B06-15 | P-CROSS |
| G3-RF-117 | B06 | RF-B06-015 | CU-B06-03 | AT-B06-13;AT-B06-15;AT-B06-18 | P-CROSS |
| G3-RF-118 | B06 | RF-B06-016 | CU-B06-09 | AT-B06-07;AT-B06-17 | P-CROSS |

La tabla tiene exactamente 118 filas de requisitos y no modifica V1. El detalle funcional de cada ID se resuelve siempre desde la fila homóloga de V1, no desde una inferencia del perfil.
