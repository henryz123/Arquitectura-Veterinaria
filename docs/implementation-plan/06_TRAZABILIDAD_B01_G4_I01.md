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
laboral para SaaS. Flyway validó V001–V010 contra la base local sin aplicar
una migración nueva.

Redocly CLI 2.67.1 validó formalmente la sintaxis semántica OpenAPI 3.1. El
contrato conserva drift funcional: no describe aún 23 endpoints B01 reales y
modela de forma incompleta la sesión SaaS. La validación del documento no se
interpreta como conformidad de implementación.

La E2E autenticada real Angular → Spring Boot → PostgreSQL superó login STAFF,
carga de perfil tenant protegido, logout con `X-CSRF-Token` y denegación de
`/workspace` tras revocar la sesión. Playwright Core 1.58.2 reutilizó el
Chromium instalado por Agent Browser; las credenciales quedaron solo en el
entorno del proceso. Agent Browser 0.27.0 sigue bloqueándose al cerrar su
sesión local, por lo que no se declara una auditoría propia de ese CLI como
superada.

## Revisión individual de los 16 RF incompletos

| RF | Diagnóstico de G4-I01 | Decisión de cierre de incremento |
|---|---|---|
| RF-SAA-02 | Hay validaciones y ruta de registro, pero no una prueba completa de RUC opcional, duplicados y reserva. | Pendiente: cubrir el ciclo RUC sin cambiar la V1. |
| RF-SAA-05 | La suspensión bloquea autenticación de Aurora y conserva la evidencia acotada; no hay módulos operativos B02–B05 que prueben todas las operaciones. | Parcial por límite de vertical. |
| RF-SAA-06 | La reactivación restaura Aurora sin afectar Bosque; falta el prerrequisito de resolución de conflicto RUC. | Pendiente RUC. |
| RF-SAA-07 | No se ejecutó un caso integrado de RUC duplicado con respuesta opaca. | Pendiente prueba de no filtración. |
| RF-SAA-08 | La ruta de revisión existe, pero no hay expediente completo con resolución SaaS verificado. | Pendiente prueba integrada. |
| RF-SAA-09 | La unicidad del administrador inicial está modelada, pero no hay prueba de una segunda solicitud. | Pendiente prueba negativa. |
| RF-SAA-11 | No se verificaron la reserva exacta de 24 horas, vencimiento y reanudación expresa. | Pendiente ciclo temporal RUC. |
| RF-SAA-12 | No se verificó la liberación excepcional con justificación, aviso y traza sin transferencia de datos. | Pendiente procedimiento excepcional. |
| RF-SAA-13 | No existe evidencia de recuperación administrativa excepcional con legitimidad y credencial creada por el titular. | Fuera del vertical actual; requiere expediente y aprobación posterior. |
| RF-AUT-04 | Se cubren algunos estados suspendidos/contextuales, no todas las combinaciones de no verificada, desactivada y tenant suspendido. | Pendiente matriz de estados negativa. |
| RF-AUT-11 | Vencimientos y reenvío tienen cobertura parcial; la auditoría concurrente con COMMIT detectó dos recuperaciones activas utilizables. | Riesgo crítico pendiente; no declarar cerrado. |
| RF-AUT-13 | Logout y parte de suspensión están cubiertos, pero falta prueba completa de revocación de sesiones activas ante todos los disparadores. | Pendiente tras corregir concurrencia. |
| RF-USR-04 | Se protege el último administrador, pero no hay evidencia integral de desactivación, revocación y conservación de autoría. | Pendiente caso completo. |
| RF-USR-05 | No se probó el mismo correo laboral en dos tenants con credenciales, roles y sesiones separadas. | Pendiente prueba multi-tenant. |
| RF-CLI-03 | Se separan autenticación y roles, pero las funciones/datos propios del portal dependen de B02–B05 no implementados. | Parcial por alcance protegido. |
| RF-CLI-04 | La recuperación es aislada por tenant, pero no hay datos privados del portal B02–B05 contra los cuales ejecutar la negativa. | Parcial por alcance protegido. |

G4-I01 permanece parcial: 16 RF siguen parciales/no verificados y el control de
concurrencia de recuperación tiene un defecto reproducido. No se autorizó ni se
aplicó V011; la corrección debe pasar por una nueva propuesta de migración y
pruebas de regresión antes de continuar a G4-I02.
