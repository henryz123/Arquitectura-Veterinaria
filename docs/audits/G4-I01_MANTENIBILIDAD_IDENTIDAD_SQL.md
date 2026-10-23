# G4-I01 — mantenibilidad y seguridad de identidad SQL (V001–V011)

## Alcance y método

Auditoría de las fuentes V001–V011 y contraste del catálogo local de
`arquitectura_veterinaria` el 2026-10-23. V011 fue inspeccionada, ejecutada en
un dry-run con `BEGIN`/`ROLLBACK`, revisada independientemente y aplicada una
sola vez mediante Flyway. No se modificaron V001–V010 ni se ejecutó
`flyway repair`.

`plpgsql_check` no está instalado; la revisión estática de ambigüedades y SQL
dinámico no equivale a una certificación exhaustiva de análisis de PL/pgSQL.

## Hallazgos cerrados

| Hallazgo | Evidencia | Estado |
|---|---|---|
| Salidas `RETURNS TABLE` ambiguas | V007, V008 y V010 califican columnas que podían colisionar con parámetros o salidas. | Corregido por versiones inmutables sucesivas. |
| Vigencia de tokens | V006/V009/V010 adoptaron `statement_timestamp()` donde corresponde a enlaces limitados. | Verificado por pruebas afectadas. |
| Doble recuperación activa | V011 bloquea primero el principal y añade el índice único parcial para recuperación. | Corregido y reproducido con `COMMIT_2`/`COMMIT_5`. |
| Orden de bloqueos | Emisión y consumo siguen `principal → one_time_token`; el consumo revalida al actualizar. | Sin deadlock en regresión concurrente repetida. |
| Propiedad y ACL V011 | Hash `508A558B90F33C74B1382696214DC45D167E389DED59E0A7F10B63968700167E`; owner `av_schema_owner`; `PUBLIC=false`, `av_app=true`. | Verificado en catálogo. |
| SQL privilegiado | V011 no incorpora `SET ROLE`, SQL dinámico ni DML directo para `av_app`; conserva `SECURITY DEFINER` y `pg_temp` al final del `search_path`. | Revisión independiente y catálogo posterior. |
| Residuos | Los fixtures de las regresiones de concurrencia finalizaron en cero. | Consulta de limpieza en los scripts. |

## Controles de aislamiento observados

- `av_schema_owner` y `av_app` no son superusuarios ni tienen `BYPASSRLS`.
- `av_app` no crea objetos en esquemas confiables y no lee ni actualiza
  directamente `principal`, `one_time_token` o `saas_administrator`.
- RLS sigue habilitado en tablas contextuales. Las tablas de identidad con RLS
  desactivado permanecen sin ACL directa de `av_app` y se alcanzan solo mediante
  funciones acotadas.
- Las pruebas usaron dos CLIENT sintéticos con igual correo en Aurora y Bosque;
  una recuperación o emisión en un tenant no modificó el otro.

## Riesgos y trabajo pendiente

| Severidad | Riesgo | Tratamiento |
|---|---|---|
| Bajo local | `CREATE UNIQUE INDEX` de V011 no es concurrente. | Válido para la base exclusiva local y migración atómica; revisar ventana operativa antes de cualquier entorno compartido. |
| Media | Límites de 24 h en transacciones largas de otros propósitos. | No se amplió V011 fuera de recuperación; requiere autorización y prueba específica. |
| Media | Dependencia de buzón local de notificaciones. | No llevar enlaces en memoria a una URL pública; definir adaptador seguro antes de despliegue. |
| Media | E2E/a11y de Agent Browser incompleta. | Conservar Playwright como evidencia E2E; resolver el CLI o usar auditoría a11y aprobada antes de publicación. |

## Reglas para cambios posteriores

1. No modificar V001–V011 ni reparar Flyway para ocultar diferencias.
2. Mantener owner, `SECURITY DEFINER` cuando corresponda, `search_path` seguro,
   `REVOKE PUBLIC` y `GRANT av_app` en la misma migración transaccional.
3. Toda nueva migración privilegiada requiere auditoría, autorización explícita,
   pruebas de concurrencia/tenant y verificación posterior de ACL.
