# B01 — Administración SaaS y Autenticación | Especificación funcional V1.0 FINAL

**Estado:** línea base funcional aprobada en conversación (CU-001 V0.6 y RF-001 V0.6), incorporada en paquete final sin modificar su alcance. **Origen:** reconstrucción documental fiel de lo aprobado en esta conversación; no es un archivo histórico previamente verificado del repositorio. **Procesos:** PB-01, PB-02, PB-10, PB-11.

## 1. Objetivo y alcance

Registro y activación de veterinarias, administración SaaS limitada, recuperación excepcional, autenticación segregada por contexto, trabajadores con roles acumulables y clientes independientes por veterinaria. **No** se concede al administrador SaaS acceso clínico ordinario.

## 2. Actores y ámbitos

- Registrante público (ACT-10); administrador SaaS (ACT-01); administrador veterinaria (ACT-02); trabajador invitado de cualquiera de los cinco roles (ACT-02..06); cliente autenticado (ACT-07).
- Accesos independientes: global SaaS, personal de veterinaria y cliente. Mismo email puede existir en distintos contextos y tenants; no implica identidad, contraseña ni autorización compartida.
- Un correo de **administrador registrante inicial** solo registra una veterinaria durante V1; sigue siendo posible ser empleado invitado de otra.
- El administrador de veterinaria no puede conceder privilegios globales ni editar clínica sin rol veterinario adicional.

## 3. Casos de uso: catálogo aprobado

| Código | Nombre | Actor principal |
|---|---|---|
| CU-SAA-01 | Registrar veterinaria | Representante registrante |
| CU-SAA-02 | Verificar correo, establecer contraseña y activar | Representante registrante |
| CU-SAA-03 | Consultar organizaciones | Administrador SaaS |
| CU-SAA-04 | Suspender veterinaria | Administrador SaaS |
| CU-SAA-05 | Reactivar veterinaria | Administrador SaaS |
| CU-SAA-06 | Solicitar revisión por RUC duplicado | Representante solicitante |
| CU-SAA-07 | Gestionar revisión por RUC duplicado | Administrador SaaS |
| CU-SAA-08 | Recuperar administración de veterinaria | Administrador SaaS con solicitante verificado |
| CU-AUT-01 | Iniciar sesión | Usuario registrado |
| CU-AUT-02 | Cerrar sesión | Usuario autenticado |
| CU-AUT-03 | Recuperar contraseña | Titular de cuenta |
| CU-USR-01 | Invitar y habilitar trabajador | Administrador veterinaria, trabajador invitado |
| CU-USR-02 | Asignar y modificar roles | Administrador veterinaria |
| CU-USR-03 | Desactivar trabajador | Administrador veterinaria |
| CU-CLI-01 | Registrar cuenta de cliente | Cliente |
| CU-CLI-02 | Acceder al espacio privado | Cliente autenticado |

## 4. Flujos funcionales, excepciones y postcondiciones

### CU-SAA-01 — Registrar veterinaria
**Actor:** Representante registrante. **Precondición:** Registro público disponible.
**Flujo:** Formulario con nombre comercial, dirección, teléfono, correo comercial, RUC opcional y correo del administrador; validar formato, correo inicial elegible y unicidad de RUC; reservar RUC 24 h; crear solicitud y enviar verificación.
**Alternativas, errores y controles:** Campos inválidos, duplicado de RUC, reserva ajena vigente, correo de registrante ya utilizado, envío fallido: solicitud no operativa; vencimiento de reserva requiere reanudación expresa y revalidación.
**Postcondición:** Solicitud pendiente, sin acceso a funciones del tenant..

### CU-SAA-02 — Verificar correo, establecer contraseña y activar
**Actor:** Representante registrante. **Precondición:** Registro pendiente, enlace válido.
**Flujo:** Verificar correo mediante enlace de un uso y 24 h; tras verificar, permitir configurar contraseña inicial; reevaluar RUC/registro; habilitar administrador y activar veterinaria conjuntamente.
**Alternativas, errores y controles:** Enlace expirado/usado, reserva RUC expirada/ocupada, contraseña incompleta, activación concurrente: negar activación y conservar estado explicable, sin duplicar tenant.
**Postcondición:** Tenant activo y cuenta de administrador inicial funcional..

### CU-SAA-03 — Consultar organizaciones
**Actor:** Administrador SaaS. **Precondición:** Sesión global autorizada.
**Flujo:** Listar/filtrar organizaciones; visualizar solamente nombre comercial, RUC si existe, dirección, teléfono, correo comercial, fecha de registro y estado.
**Alternativas, errores y controles:** Acceso desde cuenta tenant o cliente, petición de información clínica, exportación no autorizada: denegar.
**Postcondición:** Consulta administrativa sin escritura..

### CU-SAA-04 — Suspender veterinaria
**Actor:** Administrador SaaS. **Precondición:** Tenant existente.
**Flujo:** Solicitar suspensión con motivo y confirmación; comprobar permiso; registrar acto; cambiar estado y revocar sesiones afectadas; bloquear operaciones públicas protegidas e internas, conservando datos.
**Alternativas, errores y controles:** Repetición, tenant inexistente, ausencia de permiso: respuesta segura e idempotente; revisión RUC aplica controles excepcionales.
**Postcondición:** Tenant suspendido; otros tenants permanecen operativos..

### CU-SAA-05 — Reactivar veterinaria
**Actor:** Administrador SaaS. **Precondición:** Tenant suspendido y elegible.
**Flujo:** Revisar impedimentos; si no deriva de disputa de RUC, autorizar y registrar reactivación; si deriva de conflicto, exigir nueva resolución acreditada y RUC sin conflicto.
**Alternativas, errores y controles:** Restauración automática de un RUC liberado o de usuarios previamente desactivados: prohibida.
**Postcondición:** Tenant activo cuando proceda; accesos previos sujetos a permisos actuales..

### CU-SAA-06 — Solicitar revisión por RUC duplicado
**Actor:** Representante solicitante. **Precondición:** Registro público bloqueado por RUC.
**Flujo:** Mostrar error sin exponer tercero; recibir datos mínimos y canal verificado de respuesta; registrar solicitud y estado.
**Alternativas, errores y controles:** Repeticiones abusivas o datos no válidos: limitar/rechazar; jamás revelar quién registró el RUC.
**Postcondición:** Expediente de revisión pendiente..

### CU-SAA-07 — Gestionar revisión por RUC duplicado
**Actor:** Administrador SaaS. **Precondición:** Expediente y sesión global válidos.
**Flujo:** Comprobar legitimidad manualmente; solicitar evidencia suficiente; decidir con motivo; si disputa acreditada y se elige resolución excepcional, confirmar expresamente suspensión y liberación del RUC; notificar a la veterinaria afectada sin datos privados del reclamante.
**Alternativas, errores y controles:** Prueba insuficiente, recurso disputado, doble resolución o fallo de notificación: no liberar indebidamente y registrar resultado; nunca transferir datos o acceso clínico.
**Postcondición:** Expediente motivado y auditado; si aplica, tenant suspendido y RUC liberado..

### CU-SAA-08 — Recuperar administración de veterinaria
**Actor:** Administrador SaaS con solicitante verificado. **Precondición:** Pérdida de acceso administrativo y expediente de recuperación.
**Flujo:** Verificar manualmente vínculo legítimo con tenant usando evidencia mínima aprobada; registrar resolución; habilitar invitación excepcional a cuenta administradora, cuya contraseña crea el titular; auditar y revocar cuentas/sesiones potencialmente comprometidas cuando corresponda.
**Alternativas, errores y controles:** Identidad insuficiente, conflicto de legitimidad o tenant suspendido: no restaurar acceso operativo ni levantar suspensión; recuperación no permite ver clínica.
**Postcondición:** Administración restablecida únicamente para solicitud acreditada..

### CU-AUT-01 — Iniciar sesión
**Actor:** Usuario registrado. **Precondición:** Cuenta y contexto determinados.
**Flujo:** Acceder por ruta SaaS/personal/cliente; determinar tenant si aplica; verificar credenciales, correo, estado de cuenta/tenant, permisos y controles contra intentos abusivos.
**Alternativas, errores y controles:** Correo idéntico en otras cuentas no crea sesión cruzada; errores no enumeran cuentas; sesión suspendida/desactivada se deniega.
**Postcondición:** Sesión asociada a identidad, tipo y tenant inequívocos..

### CU-AUT-02 — Cerrar sesión
**Actor:** Usuario autenticado. **Precondición:** Sesión activa.
**Flujo:** Solicitar salida; invalidar acceso actual; rechazar peticiones protegidas posteriores con esa sesión.
**Alternativas, errores y controles:** Cierre repetido no reactiva credenciales.
**Postcondición:** Sesión finalizada..

### CU-AUT-03 — Recuperar contraseña
**Actor:** Titular de cuenta. **Precondición:** Contexto de identidad específico.
**Flujo:** Solicitar recuperación con respuesta pública no enumerativa; enviar enlace de un uso válido 30 min; validar token y contexto; establecer contraseña; revocar sesiones de esa cuenta.
**Alternativas, errores y controles:** Token caducado/reutilizado/reemplazado o cuenta de otro tenant con igual correo: denegar modificación ajena.
**Postcondición:** Credencial de una sola cuenta cambiada y sesiones afectadas revocadas..

### CU-USR-01 — Invitar y habilitar trabajador
**Actor:** Administrador veterinaria, trabajador invitado. **Precondición:** Tenant activo y admin autorizado.
**Flujo:** Registrar correo y roles; emitir invitación de un uso por 24 h; empleado acepta y crea contraseña propia; activar cuenta laboral propia del tenant.
**Alternativas, errores y controles:** Invitación reemplazada/caducada/duplicada o tenant suspendido: no habilitar; correo usado en otro tenant o cuenta cliente no impide alta.
**Postcondición:** Trabajador activo con roles aprobados en su tenant..

### CU-USR-02 — Asignar y modificar roles
**Actor:** Administrador veterinaria. **Precondición:** Trabajador del mismo tenant.
**Flujo:** Seleccionar empleado, agregar/quitar roles del catálogo de cinco; validar límites y preservar último administrador activo; registrar cambios y revocar permisos obsoletos.
**Alternativas, errores y controles:** Intento de asignar admin SaaS, intervenir otro tenant o quitar último admin: denegar.
**Postcondición:** Permisos actualizados solo en el tenant correspondiente..

### CU-USR-03 — Desactivar trabajador
**Actor:** Administrador veterinaria. **Precondición:** Cuenta laboral del mismo tenant.
**Flujo:** Comprobar que no sea el último administrador activo; desactivar cuenta, revocar sesiones, conservar referencias históricas.
**Alternativas, errores y controles:** Si es último administrador o pertenece a otro tenant: denegar; operaciones históricas no se eliminan.
**Postcondición:** Cuenta desactivada localmente, sin afectar otras del mismo correo..

### CU-CLI-01 — Registrar cuenta de cliente
**Actor:** Cliente. **Precondición:** Veterinaria activa.
**Flujo:** Registrar correo, contraseña y datos mínimos; verificar unicidad en contexto cliente+tenant; crear cuenta pendiente, enviar enlace de verificación por 24 h; activar al verificar.
**Alternativas, errores y controles:** Token caducado, correo de cliente duplicado local, tenant suspendido: no acceder; una cuenta laboral u otra veterinaria con mismo correo no bloquea por sí sola.
**Postcondición:** Cuenta cliente verificada dentro de una veterinaria..

### CU-CLI-02 — Acceder al espacio privado
**Actor:** Cliente autenticado. **Precondición:** Cuenta cliente verificada, tenant activo.
**Flujo:** Mostrar mascotas básicas, citas, grooming, pedidos y perfil conforme a autorización sobre recurso; delegar operaciones a sus propios bloques.
**Alternativas, errores y controles:** Cambiar IDs para acceder a otros propietarios/tenants, pedir clínica completa o usar credencial de trabajador: denegar.
**Postcondición:** Acceso limitado a información propia autorizada..

## 5. Estados y transiciones

| Objeto | Estados de negocio V1 | Control obligatorio |
|---|---|---|
| Registro SaaS | PENDIENTE_CORREO → PENDIENTE_CONTRASEÑA → ACTIVO ↔ SUSPENDIDO (solo tras aprobación) | Sin acceso tenant antes de ambas verificaciones; suspensión no borra datos |
| Reserva de RUC | DISPONIBLE → RESERVADO_24H → VENCIDO o ASOCIADO; LIBERADO_EXCEPCIONAL | Vencimiento efectivo; nueva reserva solo a petición y disponibilidad; unicidad concurrente |
| Reclamación RUC | PENDIENTE → EN_REVISION → INFO_REQUERIDA/RECHAZADA/ACREDITADA | Respuesta sin filtración; excepcional requiere evidencia, confirmación, traza y notificación |
| Cuenta trabajador | INVITADA → ACTIVA → DESACTIVADA | Desactivación revoca sesiones y preserva historia; último admin protegido |
| Cuenta cliente | PENDIENTE_VERIFICACION → ACTIVA | Operaciones privadas solo con email verificado y tenant activo |
| Recuperación administrativa | SOLICITADA → VERIFICANDO → INFO_REQUERIDA/RECHAZADA/APROBADA → EJECUTADA | Evidencia manual, cuenta invitada al titular, revisión de sesiones antiguas |

## 6. Matriz de permisos del bloque

| Operación | Registrante | SaaS admin | Admin vet | Otro empleado | Cliente |
|---|---|---|---|---|---|
| Registrar veterinaria | Sí | No por rol | No por rol | No por rol | No por rol |
| Consultar datos globales limitados y suspender/reactivar | No | Sí | No | No | No |
| Resolver disputa RUC / recuperación administrativa | Solicita | Resuelve en expediente excepcional | Solicita | No | No |
| Invitar trabajadores, asignar roles, desactivar | No | No ordinariamente | Sí dentro de su tenant | Solo si también tiene rol admin vet | No |
| Iniciar sesión/cerrar/recuperar propia | Según cuenta | Sí | Sí | Sí | Sí |
| Acceder al portal cliente | Solo con cuenta cliente | Solo con cuenta cliente separada | Solo con cuenta cliente separada | Solo con cuenta cliente separada | Sí |
| Crear o editar clínica | No | No | Solo con rol veterinario adicional | Solo rol veterinario | No |

## 7. Requisitos funcionales aprobados (RF-001 V0.6)

| ID | Exigencia |
|---|---|
| RF-SAA-01 | Permitir registro público de veterinaria con datos comerciales y del administrador inicial. |
| RF-SAA-02 | Validar campos, RUC opcional y las reglas de duplicados/reservas. |
| RF-SAA-03 | Activar automáticamente solo tras correo del administrador verificado y contraseña inicial configurada. |
| RF-SAA-04 | Consultar organizaciones globalmente solo mediante campos administrativos permitidos. |
| RF-SAA-05 | Suspender organizaciones, bloquear operaciones y conservar historial. |
| RF-SAA-06 | Reactivar organizaciones cuando corresponda, con resolución previa si hubo conflicto RUC. |
| RF-SAA-07 | Bloquear registro ordinario de RUC duplicado y ofrecer revisión sin revelar terceros. |
| RF-SAA-08 | Permitir tramitar revisiones de RUC por administrador SaaS autorizado. |
| RF-SAA-09 | Impedir reutilizar el correo del administrador registrante inicial para registrar otra veterinaria en V1. |
| RF-SAA-10 | Limitar detalle global a nombre comercial, RUC opcional, dirección, teléfono, correo comercial, fecha de registro y estado. |
| RF-SAA-11 | Reservar RUC 24 horas y reanudar por acción expresa tras vencimiento, con validación de disponibilidad. |
| RF-SAA-12 | Permitir suspensión y liberación excepcional de RUC con justificación, confirmación, aviso y traza, sin transferir datos. |
| RF-SAA-13 | Recuperar excepcionalmente la capacidad administrativa tras verificación manual de legitimidad, decisión trazable y credenciales creadas por titular, sin acceso clínico del admin SaaS. |
| RF-AUT-01 | Autenticar dentro del contexto de cuenta/tenant. |
| RF-AUT-02 | Permitir cerrar sesión e invalidar el acceso. |
| RF-AUT-03 | Ofrecer recuperación de contraseña vinculada a la identidad concreta. |
| RF-AUT-04 | Denegar operaciones a cuentas no verificadas, empleados desactivados o tenants suspendidos. |
| RF-AUT-05 | Impedir que el correo compartido confiera permisos o sesiones entre cuentas. |
| RF-AUT-06 | Crear primer administrador SaaS mediante inicialización privada. |
| RF-AUT-07 | Ofrecer rutas separadas para empleados y clientes con credenciales independientes. |
| RF-AUT-08 | Recuperar cuenta sin modificar otras identidades con mismo correo. |
| RF-AUT-09 | Establecer contraseña inicial admin solo después de verificar correo con enlace seguro. |
| RF-AUT-10 | Identificar tipo de cuenta y tenant antes de conceder permisos. |
| RF-AUT-11 | Usar enlaces de una sola vez: 24 h para verificación/invitaciones, 30 min recuperación; reenvío invalida anterior. |
| RF-AUT-12 | Limitar intentos abusivos sin facilitar enumeración de usuarios. |
| RF-AUT-13 | Revocar sesiones afectadas por desactivación de empleado, suspensión de tenant o cambio de contraseña, sin afectar cuentas ajenas. |
| RF-USR-01 | Administración de invitaciones por email para cuentas laborales. |
| RF-USR-02 | Aceptar invitación válida y permitir al empleado definir su contraseña. |
| RF-USR-03 | Asignar múltiples roles y bloquear la retirada del último administrador activo. |
| RF-USR-04 | Desactivar trabajadores y revocar accesos con conservación de autorías; proteger último administrador. |
| RF-USR-05 | Permitir cuentas laborales separadas entre veterinarias con correo compartido sin compartir credenciales, permisos o sesiones. |
| RF-CLI-01 | Registrar cuentas independientes de clientes por veterinaria. |
| RF-CLI-02 | Verificar correo de clientes antes de operaciones privadas. |
| RF-CLI-03 | Permitir solo funciones y datos propios autorizados. |
| RF-CLI-04 | Impedir compartir permisos/datos privados automáticamente entre clientes de distintos tenants. |

## 8. Criterios de aceptación diseñados (RF-001 V0.6)

| ID | Resultado esperado |
|---|---|
| CA-SAA-01 | Admite correos diferentes para negocio y administrador inicial. |
| CA-SAA-02 | Nombre comercial repetido no rechaza por sí solo. |
| CA-SAA-03 | RUC duplicado bloquea registro y permite reclamación. |
| CA-SAA-04 | Respuesta por conflicto RUC no filtra datos de otra organización. |
| CA-SAA-05 | Correo usado para registro inicial no puede registrar otro tenant V1. |
| CA-SAA-06 | SaaS admin solo visualiza siete campos administrativos aprobados. |
| CA-SAA-07 | Correo verificado sin contraseña no activa tenant. |
| CA-SAA-08 | Suspender bloquea operaciones y preserva registros. |
| CA-SAA-09 | Reclamación no transfiere permisos/datos automáticamente. |
| CA-SAA-10 | Reserva de RUC vencida ya no bloquea nuevas solicitudes por sí sola. |
| CA-SAA-11 | Activación revalida asociación de RUC. |
| CA-SAA-12 | Revisión guarda responsable, motivo y resultado. |
| CA-SAA-13 | Revisión no abre datos operativos ni clínicos. |
| CA-SAA-14 | Reserva RUC vence exactamente a las 24 horas. |
| CA-SAA-15 | Repetir activación no duplica tenants ni cuentas. |
| CA-SAA-16 | Liberación excepcional requiere autorización administrativa. |
| CA-SAA-17 | Liberación excepcional no elimina historial ni transfiere datos. |
| CA-SAA-18 | Reanudación expresa emite nueva reserva solo si sigue disponible. |
| CA-SAA-19 | No se renuevan automáticamente reservas vencidas. |
| CA-SAA-20 | Suspensión y liberación excepcionales requieren confirmación y aviso sin datos de reclamante. |
| CA-SAA-21 | Tenant suspendido por RUC no se reactiva sin nueva resolución documentada. |
| CA-SAA-22 | Recuperación de administración exige evidencia de legitimidad y expediente. |
| CA-SAA-23 | SaaS admin no obtiene acceso clínico mediante recuperación. |
| CA-SAA-24 | Recuperación no permite alta pública de administrador global ni evita la verificación. |
| CA-AUT-01 | Correo compartido entre trabajador y cliente no comparte privilegios. |
| CA-AUT-02 | Cliente no accede al área interna con sus credenciales. |
| CA-AUT-03 | Recuperación afecta solo cuenta específica. |
| CA-AUT-04 | Trabajador desactivado no opera y conserva historia. |
| CA-AUT-05 | Cliente no verificado no compra ni reserva. |
| CA-AUT-06 | Usuario sin rol SaaS global no administra tenants. |
| CA-AUT-07 | No es posible establecer contraseña inicial sin verificar email. |
| CA-AUT-08 | Token inválido/vencido/usado no establece credenciales. |
| CA-AUT-09 | Sesión identifica tenant y tipo de cuenta inequívocos. |
| CA-AUT-10 | Tenant no queda activo con correo o contraseña inicial pendiente. |
| CA-AUT-11 | Verificación vence a las 24 horas. |
| CA-AUT-12 | Invitación vence a las 24 horas. |
| CA-AUT-13 | Recuperación contraseña vence a los 30 minutos. |
| CA-AUT-14 | Reenvío o uso único invalida token anterior. |
| CA-AUT-15 | Abuso de login activa controles sin enumeración. |
| CA-AUT-16 | Suspensión/desactivación/reinicio de contraseña revoca solo sesiones afectadas. |
| CA-USR-01 | Invitación válida habilita trabajador con contraseña propia. |
| CA-USR-02 | Invitación usada o inválida no habilita. |
| CA-USR-03 | Mismo correo permite cuentas laborales en distintos tenants. |
| CA-USR-04 | Roles de tenant A no conceden permisos en B. |
| CA-USR-05 | Desactivar cuenta A no desactiva cuenta B de igual correo. |
| CA-USR-06 | Último administrador no puede desactivarse por vía ordinaria. |
| CA-USR-07 | Último rol administrativo no puede retirarse por vía ordinaria. |
| CA-USR-08 | Recuperación excepcional requiere autorización; no elude verificación. |
| CA-CLI-01 | Cuenta cliente no accede a datos privados de otro tenant. |

## 9. Reglas y riesgos especiales

- Todos los enlaces de verificación e invitación duran 24 h; recuperación de contraseña, 30 min; todos son de un uso; un reenvío invalida el previo **de esa cuenta y operación**.
- Los intentos abusivos se restringen con límites verificables que se fijarán como parámetros de seguridad antes de la implementación; nunca revelar correos o cuentas ajenos.
- Toda suspensión de tenant, desactivación laboral o restablecimiento de contraseña revoca sesiones relevantes, sin afectar otras cuentas del mismo correo.
- RUC opcional para demo no significa titularidad tributaria acreditada. Liberación por disputa exige evidencia, no transfiere datos; reactivación queda condicionada a nuevo expediente.
- Recuperación excepcional del admin vet **no** faculta al SaaS admin a revisar clínica. Antes de implementar, definir evidencia mínima, avisos, contención de accesos antiguos y auditoría reforzada.
- La aprobación funcional no equivale a ejecución de pruebas de seguridad, ni a decisión tecnológica sobre sesiones/tokens.

## 10. Estado de esta versión

**Aprobación:** CU-001 V0.6 y RF-001 V0.6 aprobados por el responsable del proyecto en conversación. Esta es su materialización en el paquete funcional V1; se conserva la trazabilidad y no se declara implementación.
