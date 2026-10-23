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
ordinarias. V013 resolvio la ambiguedad de recuperacion administrativa, V014
normalizo exclusivamente nombres sinteticos historicos y V015 rota el CSRF de
una sesion vigente durante la restauracion del navegador. La suite
`B01AdministrationDatabaseIntegrationTest` ejecuto 30 casos correctos y
`StaffAuthenticationDatabaseIntegrationTest` ejecuto 3 casos correctos,
incluidos rotacion, revocacion y conservacion de la misma sesion opaca.

## Matriz RF -> evidencia

| RF | Estado | Evidencia actual y limite |
|---|---|---|
| RF-SAA-01 | Verificado puntual | Registro, verificacion, activacion y autenticacion de tenant sintetico. |
| RF-SAA-02 | Parcial | Se verificaron RUC valido, duplicado y reserva activa; faltan reloj controlable, vencimiento exacto y RUC opcional por el flujo completo. Es una brecha de regresion, no requiere V012. |
| RF-SAA-03 | Verificado puntual | Correo y contrasena inicial activan una sola veterinaria. |
| RF-SAA-04 | Verificado puntual | La consola SaaS Angular lista organizaciones y proyecta solo los campos administrativos aprobados. |
| RF-SAA-05 | Parcial | Suspension y reactivacion se ejecutan por API protegida y tienen formularios SaaS; B02-B05 no existen para probar todas sus operaciones dependientes. |
| RF-SAA-06 | Parcial | Reactivacion posterior a resolucion sintetica funciona y no repone el RUC; falta una politica persistida que distinga formalmente una suspension por disputa. Requiere decision y modelado posterior. |
| RF-SAA-07 | Verificado puntual | RUC duplicado devuelve conflicto opaco, sin nombre ni RUC de la organizacion titular. |
| RF-SAA-08 | Verificado puntual | Solicitud, listado y resolucion acreditada por SaaS se ejercitaron en transaccion y se exponen en la consola SaaS protegida. |
| RF-SAA-09 | Verificado puntual | El correo de administrador inicial no puede iniciar otra solicitud. |
| RF-SAA-10 | Verificado puntual | Proyeccion `Organization` limitada a los campos administrativos aprobados. |
| RF-SAA-11 | Parcial | Reanudacion expresa invalida el enlace anterior; falta prueba reproducible del vencimiento exacto de 24 h. Es una brecha de regresion, no requiere V012. |
| RF-SAA-12 | Verificado puntual | Release excepcional exige confirmacion, suspende al titular, revoca su acceso y permite una nueva solicitud sin transferencia de datos. |
| RF-SAA-13 | Verificado puntual | V013 corrigio el conflicto de recuperacion administrativa; aprobacion con atestacion, rechazo sin atestacion, ejecucion unica y consola SaaS quedan cubiertos por integracion. |
| RF-AUT-01 | Verificado puntual | STAFF, CLIENT y SaaS autentican en su contexto. |
| RF-AUT-02 | Verificado puntual | Logout HTTP con CSRF invalida el contexto posterior; V015 restituye un CSRF nuevo tras recarga sin almacenar sesion ni token persistente. |
| RF-AUT-03 | Verificado puntual | V011 serializa recuperacion; se probaron caducidad, uso unico, aislamiento y concurrencia real. |
| RF-AUT-04 | Verificado puntual | CLIENT no verificado, STAFF desactivado y tenant suspendido no autentican. |
| RF-AUT-05 | Verificado puntual | CLIENT y STAFF con correo compartido no mezclan sesiones ni privilegios. |
| RF-AUT-06 | Verificado puntual | Bootstrap privado de un solo uso bajo `av_schema_owner`, sin ruta HTTP publica. |
| RF-AUT-07 | Verificado puntual | Contextos y rutas CLIENT/STAFF separados. |
| RF-AUT-08 | Verificado puntual | Recuperacion de Aurora no modifica CLIENT homonimo de Bosque. |
| RF-AUT-09 | Verificado puntual | Grant inicial solo se consume despues de verificar correo. |
| RF-AUT-10 | Verificado puntual | Sesion conserva tipo, tenant y roles; la restauracion STAFF y SaaS recupera un CSRF nuevo con cabecera same-origin y sin exponer la cookie HttpOnly. |
| RF-AUT-11 | Parcial | Reenvio de CLIENT, uso unico y limites superiores se cubren; falta prueba controlada del borde exacto de 24 h para todos los propositos. Es una brecha de regresion, no requiere V012. |
| RF-AUT-12 | Verificado puntual | `LoginAttemptThrottleTest` supero sus dos casos. |
| RF-AUT-13 | Verificado puntual | Logout, suspension, desactivacion y cambio de contrasena revocan solo las sesiones afectadas. |
| RF-USR-01 | Verificado puntual | Invitacion laboral sintetica en tenant activo. |
| RF-USR-02 | Verificado puntual | Aceptacion de invitacion, contrasena y autenticacion laboral. |
| RF-USR-03 | Verificado puntual | V012 permite reemplazar roles multiples de una cuenta activa, revoca su sesion previa, protege al ultimo administrador y rechaza objetivos cross-tenant; la interfaz tenant consume el contrato protegido. |
| RF-USR-04 | Parcial | Desactivacion y revocacion de sesion se verificaron; la conservacion de autoria no es observable para `av_app` y requiere una decision de proyeccion/auditoria posterior. |
| RF-USR-05 | Verificado puntual | Mismo correo laboral en Aurora/Bosque conserva principals, roles y sesiones independientes; desactivar Aurora no afecta Bosque. |
| RF-CLI-01 | Verificado puntual | Registro CLIENT independiente por tenant, incluso con correo repetido. |
| RF-CLI-02 | Verificado puntual | Enlace CLIENT opaco pasa de pendiente a activa y bloquea autenticacion antes de verificar. |
| RF-CLI-03 | Parcial | `clientSpace` solo acepta el contexto CLIENT; mascotas, citas, grooming y pedidos pertenecen a B02-B05 y no existen todavia. Requiere interfaz y dependencias funcionales posteriores. |
| RF-CLI-04 | Parcial | Aislamiento de credenciales y recuperacion esta probado; no hay datos privados B02-B05 para una negativa E2E adicional. Requiere interfaz y dependencias funcionales posteriores. |

## Totales y dictamen

| Estado | RF |
|---|---:|
| Verificado puntual | 27 |
| Parcial | 8 |
| No verificado | 0 |
| Total B01 | 35 |

El incremento no esta cerrado como B01 completo: 8 RF siguen parciales por
regresiones temporales, decisiones pendientes o dependencias funcionales de
B02--B05. El aumento de evidencia no convierte planificacion ni rutas no
implementadas en producto terminado.

## Clasificacion de los 9 requisitos parciales

| Categoria | RF | Limite para declararlo verificado |
|---|---|---|
| Requieren solo regresiones controladas adicionales | RF-SAA-02, RF-SAA-11, RF-AUT-11 | Reloj reproducible y pruebas de limites exactos, sin ampliar el contrato de 24 h/30 min. |
| Requieren interfaz y modulos B02--B05 | RF-SAA-05, RF-CLI-03, RF-CLI-04 | Flujos verticales reales de los modulos dependientes, seguridad y E2E. |
| Requieren decision o proyeccion adicional | RF-SAA-06, RF-USR-04 | Politica persistida de suspension por disputa y mecanismo autorizado para observar autoria, respectivamente. |

La clasificacion no cambia los conteos: 27 requisitos tienen evidencia puntual
y 8 permanecen parciales.

## Defectos corregidos y limites abiertos

V013 corrigio de forma incremental la ambiguedad de
`tenant_admin.review_administrative_recovery_for_session` sin editar V012. V015
resolvio la perdida del token CSRF despues de una recarga completa: rota un
hash opaco solo para la sesion vigente, con `SECURITY DEFINER`, `search_path`
seguro, `PUBLIC` revocado, `EXECUTE` solo para `av_app` y respuesta `no-store`.
No se desactivo CSRF ni se creo una ruta publica.

## Evidencia complementaria

- Backend B01: 30/30 correctos, mas 3/3 de autenticacion y CSRF; las
  transacciones de prueba revirtieron y las consultas posteriores no hallaron
  cuentas ni tenants sinteticos residuales.
- E2E post-cambio: `G4_I01_AUTHENTICATED_BROWSER_E2E=PASS` con STAFF Aurora y
  Bosque, perfil propio, denial cross-tenant 404, recarga STAFF/SaaS, CSRF,
  logout SaaS y STAFF. Confirma la cabecera `no-store` y rechaza tanto el
  bootstrap sin intencion como la renovacion despues de revocar SaaS.
- Angular: build superado y 7/7 pruebas unitarias superadas en 4 archivos.
- Contrato vigente: Redocly/OpenAPI valido; incorpora
  `POST /api/v1/auth/session-csrf-token` con cookie de sesion, cabecera de
  intencion y respuesta `Cache-Control: no-store`.
- V011 sigue con SHA-256
  `508A558B90F33C74B1382696214DC45D167E389DED59E0A7F10B63968700167E` y
  checksum Flyway `-1337847367`.
