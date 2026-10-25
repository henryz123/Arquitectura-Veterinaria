# B03 — V022: auditoría y verificación de servicios y disponibilidad

**Fecha:** 2026-10-25
**Alcance:** migración local aditiva para el primer vertical B03. No modifica la especificación funcional V1 ni migraciones V001–V021.

## SQL exacto y alcance

- Archivo: `database/migration/V022__b03_schedule_foundation.sql`.
- SHA-256: `1CCDE1A6BDD3512371CC59D21BCE7D61182C22BCA3EC3FC5651285EB6391929D`.
- Objetos nuevos: esquema `appointment_management`; servicios, horario operativo, disponibilidad profesional, bloqueos, solicitudes, propuestas, reservas vigentes e históricas, eventos de cambio y atención grooming.
- La migración es aditiva y transaccional. No contiene `DROP`, `DELETE`, `TRUNCATE`, extensión PostgreSQL, SQL dinámico, `SECURITY DEFINER`, función, cambio de rol, `BYPASSRLS` ni permiso `PUBLIC`.
- Se descartó `btree_gist`: el catálogo confirmó que no está instalada y no se requiere para este incremento. El vertical posterior serializará la confirmación por profesional dentro de la transacción y revalidará el intervalo; V022 no afirma todavía que exista una confirmación atómica implementada.

## Preflight y aplicación

1. Se comprobó que la siguiente versión era V022, con V001–V021 correctas una vez en `flyway_schema_history`.
2. Se verificó el target exclusivo `jdbc:postgresql://localhost:5432/arquitectura_veterinaria` y el rol de migración `av_schema_owner`.
3. El preflight ejecutó el SQL exacto en `BEGIN` y terminó en `ROLLBACK`. Después, `to_regnamespace('appointment_management')` devolvió `false`.
4. Flyway validó 22 migraciones y aplicó V022 una sola vez. Checksum Flyway: `2011436639`.

## Seguridad y aislamiento efectivos

- Las nueve tablas son propiedad de `av_schema_owner`, con RLS activa y una política tenant-local por tabla.
- `PUBLIC` no tiene `USAGE` ni `CREATE` en el esquema, ni `SELECT`, `INSERT` o `UPDATE` sobre sus tablas.
- `av_app` tiene `USAGE` de esquema sin `CREATE`; conserva solo `SELECT/INSERT/UPDATE` por tabla cuando es necesario, no tiene `DELETE`, y `schedule_change_event` es append-only para el rol de aplicación.
- No hay función en `appointment_management`. `av_schema_owner` y `av_app` siguen sin superusuario, `BYPASSRLS`, `CREATEROLE` ni `CREATEDB`.
- Las claves foráneas compuestas atan profesional, actor, propietario, mascota y servicio al tenant efectivo. La aplicación establece el contexto JDBC confiable antes de consultas directas; los roles de profesional se vuelven a consultar desde la frontera V020 antes de guardar disponibilidad.

## Evidencia funcional del vertical

- Backend: `mvnw.cmd '-Dtest=SchedulingAccessPolicyTest,SchedulingServiceTest' test` — 6 correctas, 0 fallos, Java 21; Flyway no se habilitó en esta regresión.
- Frontend: `npm.cmd run test -- --watch=false` — 5 archivos y 11 pruebas correctas. `npm.cmd run build` fue correcto; conserva una advertencia no bloqueante de presupuesto inicial de 507.48 kB frente a 500 kB.
- Contrato: `npm.cmd run validate:contract` validó `docs/contracts/openapi-v1.yaml`.
- Navegador: `npm.cmd run test:e2e:b03-services-availability` devolvió `B03_SERVICES_AVAILABILITY_BROWSER_E2E=PASS`. Autentica administración sintética Aurora, crea o reutiliza un servicio y una disponibilidad idempotentes por Angular → Spring Boot → PostgreSQL, comprueba persistencia y confirma que Bosque no puede ver el servicio de Aurora.
- Agent Browser inspeccionó la pantalla pública de acceso: árbol con `main`, encabezado, campos con etiquetas nativas y acciones de cambio de contexto. La versión instalada no expone el subcomando `a11y`; no se afirma una auditoría automática completa de accesibilidad.

## Límite y próximo vertical

V022 habilita persistencia y UI real de catálogo/disponibilidad, pero B03 sigue parcial. Aún faltan solicitud de cita/cliente, calendario de reservas, confirmación con control transaccional de solapes, contrapropuestas, aceptación CLIENT, reprogramación, cancelación, ejecución grooming, notificaciones e historial de cambios visible. El siguiente incremento implementará solicitud y confirmación atómica antes de declarar cualquier protección de cupos como completada.
