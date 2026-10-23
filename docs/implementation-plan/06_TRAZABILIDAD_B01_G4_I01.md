# G4-I01 - trazabilidad verificable de B01

## Alcance y lectura

Esta matriz cubre los 35 requisitos de B01 definidos en
`arquitectura_veterinaria_v1_FINAL/B01_ADMINISTRACION_SAAS_Y_AUTENTICACION.md`.
No altera la especificacion funcional inmutable. **Verificado puntual** significa
que existe una prueba local concreta; **Parcial** indica que falta una parte de
la regla o que una ruta esta bloqueada. Ningun estado infiere funcionalidad de
B02 a B06.

La evidencia se actualizo el 2026-10-23 sobre la base local exclusiva
`arquitectura_veterinaria`, con Flyway desactivado para la aplicacion y pruebas
ordinarias. La suite `B01AdministrationDatabaseIntegrationTest` ejecuto 28/28
casos. Dos de esos casos son diagnosticos de defectos de SQL aplicado y no se
cuentan como exito funcional.

## Matriz RF -> evidencia

| RF | Estado | Evidencia actual y limite |
|---|---|---|
| RF-SAA-01 | Verificado puntual | Registro, verificacion, activacion y autenticacion de tenant sintetico. |
| RF-SAA-02 | Parcial | Se verificaron RUC valido, duplicado y reserva activa; faltan reloj controlable, vencimiento exacto y RUC opcional por el flujo completo. |
| RF-SAA-03 | Verificado puntual | Correo y contrasena inicial activan una sola veterinaria. |
| RF-SAA-04 | Verificado puntual | SaaS lista solo los campos administrativos proyectados. |
| RF-SAA-05 | Parcial | Suspension revoca sesiones y el release RUC bloquea el tenant; B02-B05 no existen para probar todas sus operaciones. |
| RF-SAA-06 | Parcial | Reactivacion posterior a resolucion sintetica funciona y no repone el RUC; falta una politica persistida que distinga formalmente una suspension por disputa. |
| RF-SAA-07 | Verificado puntual | RUC duplicado devuelve conflicto opaco, sin nombre ni RUC de la organizacion titular. |
| RF-SAA-08 | Verificado puntual | Solicitud, listado y resolucion acreditada por SaaS se ejercitaron en transaccion. |
| RF-SAA-09 | Verificado puntual | El correo de administrador inicial no puede iniciar otra solicitud. |
| RF-SAA-10 | Verificado puntual | Proyeccion `Organization` limitada a los campos administrativos aprobados. |
| RF-SAA-11 | Parcial | Reanudacion expresa invalida el enlace anterior; falta prueba reproducible del vencimiento exacto de 24 h. |
| RF-SAA-12 | Verificado puntual | Release excepcional exige confirmacion, suspende al titular, revoca su acceso y permite una nueva solicitud sin transferencia de datos. |
| RF-SAA-13 | Parcial | Se verifica rechazo sin atestacion humana. La aprobacion legitima esta bloqueada por defecto SQL V005 documentado, por lo que no se afirma recuperacion completa. |
| RF-AUT-01 | Verificado puntual | STAFF, CLIENT y SaaS autentican en su contexto. |
| RF-AUT-02 | Verificado puntual | Logout HTTP con CSRF invalida el contexto posterior. |
| RF-AUT-03 | Verificado puntual | V011 serializa recuperacion; se probaron caducidad, uso unico, aislamiento y concurrencia real. |
| RF-AUT-04 | Verificado puntual | CLIENT no verificado, STAFF desactivado y tenant suspendido no autentican. |
| RF-AUT-05 | Verificado puntual | CLIENT y STAFF con correo compartido no mezclan sesiones ni privilegios. |
| RF-AUT-06 | Verificado puntual | Bootstrap privado de un solo uso bajo `av_schema_owner`, sin ruta HTTP publica. |
| RF-AUT-07 | Verificado puntual | Contextos y rutas CLIENT/STAFF separados. |
| RF-AUT-08 | Verificado puntual | Recuperacion de Aurora no modifica CLIENT homonimo de Bosque. |
| RF-AUT-09 | Verificado puntual | Grant inicial solo se consume despues de verificar correo. |
| RF-AUT-10 | Verificado puntual | Sesion conserva tipo, tenant y roles. |
| RF-AUT-11 | Parcial | Reenvio de CLIENT, uso unico y limites superiores se cubren; falta prueba controlada del borde exacto de 24 h para todos los propositos. |
| RF-AUT-12 | Verificado puntual | `LoginAttemptThrottleTest` supero sus dos casos. |
| RF-AUT-13 | Verificado puntual | Logout, suspension, desactivacion y cambio de contrasena revocan solo las sesiones afectadas. |
| RF-USR-01 | Verificado puntual | Invitacion laboral sintetica en tenant activo. |
| RF-USR-02 | Verificado puntual | Aceptacion de invitacion, contrasena y autenticacion laboral. |
| RF-USR-03 | Parcial | Roles multiples en invitacion y proteccion del ultimo administrador existen; el reemplazo de roles de cuenta activa esta bloqueado por defecto SQL V004. |
| RF-USR-04 | Parcial | Desactivacion y revocacion de sesion se verificaron; la conservacion de autoria no es observable para `av_app` y requiere auditoria posterior. |
| RF-USR-05 | Verificado puntual | Mismo correo laboral en Aurora/Bosque conserva principals, roles y sesiones independientes; desactivar Aurora no afecta Bosque. |
| RF-CLI-01 | Verificado puntual | Registro CLIENT independiente por tenant, incluso con correo repetido. |
| RF-CLI-02 | Verificado puntual | Enlace CLIENT opaco pasa de pendiente a activa y bloquea autenticacion antes de verificar. |
| RF-CLI-03 | Parcial | `clientSpace` solo acepta el contexto CLIENT; mascotas, citas, grooming y pedidos pertenecen a B02-B05 y no existen todavia. |
| RF-CLI-04 | Parcial | Aislamiento de credenciales y recuperacion esta probado; no hay datos privados B02-B05 para una negativa E2E adicional. |

## Totales y dictamen

| Estado | RF |
|---|---:|
| Verificado puntual | 25 |
| Parcial | 10 |
| No verificado | 0 |
| Total B01 | 35 |

El incremento no esta cerrado como B01 completo: 10 RF siguen parciales y dos
son bloqueados por defectos SQL de migraciones ya aplicadas. El aumento de
evidencia no convierte planificacion ni rutas no implementadas en producto
terminado.

## Defectos bloqueantes descubiertos

1. V004: `identity_auth.replace_staff_roles` no puede ejecutar el primer
   `SELECT` porque `normalized_email` es ambiguo entre la columna y el
   parametro de salida. La prueba diagnostica recibe `BadSqlGrammarException`.
2. V005: `tenant_admin.review_administrative_recovery_for_session` no puede
   crear la invitacion aprobada porque `principal_id` es ambiguo entre la
   columna y el parametro de salida. La prueba diagnostica recibe
   `BadSqlGrammarException`.

No se modificaron V004/V005 ni se creo/aplico V012. Corregir estos defectos
requiere una migracion nueva, auditoria de privilegios y autorizacion expresa.

## Evidencia complementaria

- Backend B01: 28/28 superadas; transacciones de prueba con rollback.
- E2E post-cambio: `G4_I01_AUTHENTICATED_BROWSER_E2E=PASS` con STAFF Aurora y
  Bosque, perfil propio, denial cross-tenant 404, CSRF/logout SaaS y STAFF.
- Angular: build superado y 1/1 prueba unitaria superada.
- Contrato vigente: Redocly/OpenAPI valido y contraste 29 controlador/29
  contrato sin diferencias; no hay cambio de rutas en esta ronda.
- V011 sigue con SHA-256
  `508A558B90F33C74B1382696214DC45D167E389DED59E0A7F10B63968700167E` y
  checksum Flyway `-1337847367`.
