# G4-I01 - matriz de seguridad y pruebas

## Controles verificados

| Control | Resultado vigente | Evidencia |
|---|---|---|
| Historial Flyway | V001-V011 aplicadas una vez; ninguna fuente local difiere de su checksum esperado. | Consulta local de `flyway_schema_history` y manifiesto de fuentes. |
| Fuente V011 | SHA-256 `508A558B90F33C74B1382696214DC45D167E389DED59E0A7F10B63968700167E`. | Archivo aplicado y auditoria previa. |
| Roles y RLS | `av_app` y `av_schema_owner` no son superusuarios ni tienen `BYPASSRLS`; RLS permanece activa. | Catalogo PostgreSQL local previo y posterior a V011. |
| Funciones V011 | Owner `av_schema_owner`, `SECURITY DEFINER`, `search_path` fijo y sin `PUBLIC EXECUTE`. | `pg_proc` y ACL local. |
| Acceso a identidades | `av_app` usa funciones acotadas; no lee ni modifica hashes, `principal` u `one_time_token` directamente. | ACL y pruebas B01. |
| Flyway ordinario | Desactivado por defecto con `AV_FLYWAY_ENABLED=false`; target defensivo `010`. | `application.yml` y runbook. |
| Vigencias acotadas | El servicio B01 obtiene `current_timestamp` de PostgreSQL dentro de la transaccion antes de calcular TTL. | Adaptador JDBC y B01 28/28. |
| CSRF y sesiones | Logout sin token: 403; con token: 204; contexto posterior: 401. | E2E autenticada local. |
| Aislamiento tenant | Perfil ajeno oculta existencia con 404; sesiones Aurora/Bosque y CLIENT/STAFF se separan. | E2E y pruebas B01. |

## Resultados ejecutados el 2026-10-23

| Grupo | Resultado | Limite o advertencia |
|---|---|---|
| B01 integrado | 28/28 superadas, 0 omitidas. | Dos son diagnosticos de defectos V004/V005; no son exito de los RF afectados. |
| Backend | 34/34 pruebas superadas, 0 omitidas, con Java 21 y Flyway desactivado. | Mockito informa carga dinamica de agente; es advertencia no bloqueante que debe atenderse al actualizar dependencias. |
| Angular | 1/1 prueba superada; `ng build` superado (276.93 kB iniciales, 73.33 kB estimados). | Sin linea visual versionada, regresion visual comparativa es inconclusa. |
| E2E autenticada | `G4_I01_AUTHENTICATED_BROWSER_E2E=PASS`. | Playwright Core con Chrome local; no sustituye una auditoria WCAG completa. |
| Agent Browser | 0.27.0 funciona mediante `agent-browser.cmd`; snapshot semantico y foco email -> password comprobados a 375, 768 y 1440 px. | El shim `.ps1` queda bloqueado por ExecutionPolicy; `agent-browser a11y` no existe en esta version. |
| Accesibilidad manual | Labels nativos, `main`, heading, navegacion, orden de foco y errores `role=alert` comprobados. | Se corrigio el foco de `#e59d38` a `#9c4e13`; contraste minimo contra blanco/canvas: 5.98:1/5.64:1. |
| Concurrencia V011 | `CONTROL_EFFECTIVE; ACTIVE_TOKENS=1; SEQUENTIALLY_USABLE=1; CLEANUP=PASS` en fixture nuevo. | No se repitio una migracion ni se altero V011 en esta ronda. |

## Hallazgos abiertos

1. **Bloqueante funcional de administracion laboral:** V004 tiene una referencia
   ambigua a `normalized_email` en `replace_staff_roles`. No se puede modificar
   V004; una V012 auditada seria la via de correccion.
2. **Bloqueante funcional de recuperacion administrativa:** V005 tiene una
   referencia ambigua a `principal_id` en
   `review_administrative_recovery_for_session`. La aprobacion con atestacion
   humana no llega a emitir la invitacion hasta una migracion nueva auditada.
3. `LocalNotificationMailbox` es valido solo para el entorno sintetico local;
   no sirve como adaptador de notificaciones para URL publica.
4. B02-B06 no estan implementados. El portal CLIENT actual solo verifica el
   contexto B01 y no muestra datos clinicos ni comerciales.
5. No existe despliegue, URL publica ni remoto configurado. Su preparacion
   requiere revisiones de secretos, licencias, datos sinteticos y autorizacion.

## Decisiones de seguridad mantenidas

- No se aplicaron ni editaron migraciones V001-V011.
- No se uso superusuario ni se amplio el privilegio de `av_app`.
- No se imprimieron contrasenas, hashes, tokens, UUID ni correos secretos.
- Los datos creados por las pruebas B01 se ejecutaron bajo transaccion de prueba
  y rollback; no se declara evidencia de residuos persistentes.
