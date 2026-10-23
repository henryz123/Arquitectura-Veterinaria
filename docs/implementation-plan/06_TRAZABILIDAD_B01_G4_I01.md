# G4-I01 — trazabilidad verificable de B01

## Alcance y lectura

Esta matriz cubre los 35 requisitos de B01 de
`arquitectura_veterinaria_v1_FINAL/B01_ADMINISTRACION_SAAS_Y_AUTENTICACION.md`.
No altera la especificación funcional inmutable ni declara un requisito cerrado
solo por existir código. El estado refleja evidencia local del 2026-10-22.

Estados: **Verificado puntual** (prueba real acotada), **Parcial** (evidencia
incompleta) y **No verificado** (sin evidencia suficiente).

## Matriz RF → evidencia

| RF | Estado | Evidencia actual / condición pendiente |
|---|---|---|
| RF-SAA-01 | Verificado puntual | Registro, verificación, activación y autenticación de tenant sintético. |
| RF-SAA-02 | Parcial | Validación de entradas; faltan duplicados, reservas y RUC. |
| RF-SAA-03 | Verificado puntual | Correo, contraseña inicial y autenticación del administrador de tenant. |
| RF-SAA-04 | Verificado puntual | Sesión SaaS real consulta `GET /tenants` con 200; el adaptador proyecta solo el registro administrativo permitido. |
| RF-SAA-05 | Parcial | La suspensión SaaS bloquea autenticación de Aurora y preserva Bosque en la regresión transaccional; falta una evidencia independiente de historial de todas las operaciones bloqueadas. |
| RF-SAA-06 | Parcial | La reactivación devuelve autenticación a Aurora sin afectar Bosque; queda pendiente el conflicto RUC previo. |
| RF-SAA-07 | No verificado | Faltan RUC duplicado y respuesta sin filtración. |
| RF-SAA-08 | No verificado | Existe ruta y autorización de revisión, pero no hay caso integrado de tramitación/resolución de RUC. |
| RF-SAA-09 | No verificado | Falta no reutilización de correo de administrador inicial. |
| RF-SAA-10 | Verificado puntual | `Organization` expone nombre, RUC opcional, dirección, teléfono, correo, fecha y estado; la sesión SaaS obtuvo 200 en el listado. |
| RF-SAA-11 | No verificado | Falta reserva, vencimiento y reanudación de RUC. |
| RF-SAA-12 | No verificado | Falta expediente excepcional, trazabilidad y liberación RUC. |
| RF-SAA-13 | No verificado | El bootstrap privado crea el primer administrador; la recuperación administrativa excepcional sigue sin prueba de legitimidad, decisión y credencial del titular. |
| RF-AUT-01 | Verificado puntual | Autenticación STAFF y CLIENT contextual. |
| RF-AUT-02 | Verificado puntual | HTTP real: logout sin CSRF devuelve 403, con token devuelve 204 y el contexto posterior devuelve 401 para SaaS y STAFF. |
| RF-AUT-03 | Verificado puntual | V010, vigencia >30 min rechazada y token de un uso. |
| RF-AUT-04 | Parcial | Contextos y estados acotados; faltan todos los estados denegados. |
| RF-AUT-05 | Verificado puntual | Dos CLIENT con igual correo en Aurora/Bosque conservan sesiones y credenciales independientes. |
| RF-AUT-06 | Verificado puntual | Bootstrap privado ejecutado una vez bajo `av_schema_owner`; estado/admin `1/1`, segundo intento rechazado y sin ruta HTTP pública. |
| RF-AUT-07 | Verificado puntual | Rutas y credenciales CLIENT/STAFF separadas. |
| RF-AUT-08 | Verificado puntual | Recuperación de Aurora no modifica cuenta independiente de Bosque con mismo correo. |
| RF-AUT-09 | Verificado puntual | Verificación y grant inicial se consumen una sola vez. |
| RF-AUT-10 | Verificado puntual | Sesiones conservan tipo de cuenta, tenant y roles. |
| RF-AUT-11 | Parcial | Invitación, roles, consumo único y reenvío cubiertos; faltan variantes. |
| RF-AUT-12 | Verificado puntual | `LoginAttemptThrottleTest` (2) pasó dentro de la suite backend final. |
| RF-AUT-13 | Parcial | Logout HTTP invalida sesiones y suspensión/reactivación prueba el aislamiento de autenticación; falta probar una sesión activa revocada y el cambio integral de contraseña. |
| RF-USR-01 | Verificado puntual | Invitación laboral sintética en Aurora. |
| RF-USR-02 | Verificado puntual | Aceptación, contraseña y autenticación del trabajador. |
| RF-USR-03 | Verificado puntual | Roles múltiples y protección del último administrador. |
| RF-USR-04 | Parcial | Falta desactivación, revocación y autoría completas. |
| RF-USR-05 | No verificado | Falta mismo correo laboral en dos veterinarias. |
| RF-CLI-01 | Verificado puntual | Registro CLIENT por tenant y dos cuentas de igual correo en tenants distintos. |
| RF-CLI-02 | Verificado puntual | Cliente pendiente pasa a activo con enlace opaco. |
| RF-CLI-03 | Parcial | CLIENT/STAFF separados; faltan datos propios y funciones completas. |
| RF-CLI-04 | Parcial | Aislamiento de recuperación probado; falta negativa de datos del portal cliente. |

## Totales y dictamen

| Estado | RF |
|---|---:|
| Verificado puntual | 19 |
| Parcial | 9 |
| No verificado | 7 |
| Total B01 | 35 |

B01 ejecutó 17 pruebas: 17 superadas, sin omisiones. El backend completo
ejecutó 23: 23 superadas, sin omisiones. Las pruebas HTTP reales cubrieron
sesión SaaS y STAFF, CSRF, logout y la denegación de perfil tenant/administración
laboral para SaaS. La concurrencia de recuperación se ejecutó con dos conexiones
`av_app` y `ROLLBACK`, sin token activo residual.

G4-I01 permanece parcial: faltan 16 RF parciales/no verificados, la prueba de
RUC, una revocación de sesión ya activa, un validador semántico OpenAPI y el E2E
de navegador autenticado. La sintaxis YAML y las 29 referencias internas del
contrato pasaron, pero no sustituyen un validador OpenAPI completo.
