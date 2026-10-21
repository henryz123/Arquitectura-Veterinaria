# B02 — Propietarios, mascotas e historias clínicas | Especificación funcional V1.0 FINAL

**Estado:** CERRADO FUNCIONALMENTE para V1 académica. **Dependencias:** cuentas y roles del Bloque 1. **Procesos base:** PB-03, PB-05, PB-11 y PB-12 para cancelaciones no clínicas. **Actores:** administrador de veterinaria, recepcionista, veterinario, cliente autenticado. Todo ámbito es **una veterinaria**.

## 1. Objetivo y límites

Registrar propietarios y mascotas, vincular los registros presenciales a cuentas digitales con verificación, transferir responsable principal con historial y atender clínicamente sin que personal no veterinario altere la historia clínica. Permitir consultas vinculadas o no a citas, vacunación y prescripciones básicas.

**Fuera de V1:** hospitalización, cirugía quirúrgica avanzada, laboratorio integrado, receta electrónica con validez regulatoria externa, interoperabilidad con otras veterinarias, archivos multimedia clínicos de gran tamaño, firma digital legal avanzada y acceso clínico general de clientes. **Aprobado V1:** el acceso íntegro a historia clínica desde el portal cliente queda excluido mediante DEC-064.

## 2. Catálogo propuesto

| ID | Caso de uso y resultado |
|---|---|
| CU-B02-01 | Alta/edición administrativa de propietario | Propietario dentro del tenant, sin exigir cuenta online |
| CU-B02-02 | Alta/edición básica de mascota | Mascota con un único responsable principal vigente |
| CU-B02-03 | Solicitar vinculación digital | Solicitud pendiente identificada con cuenta y registro presencial |
| CU-B02-04 | Verificar/resolver vinculación | Vinculación aprobada/rechazada con evidencias mínimas y auditoría |
| CU-B02-05 | Transferir responsable principal | Nueva responsabilidad y conservación de períodos anteriores |
| CU-B02-06 | Registrar consulta clínica | Episodio con veterinario responsable y datos clínicos |
| CU-B02-07 | Consultar/rectificar historia clínica | Lectura autorizada; rectificación con traza |
| CU-B02-08 | Registrar vacuna y próxima dosis | Evento de vacunación y recordatorio opcional |
| CU-B02-09 | Elaborar indicación/receta básica | Prescripción registrada con identificación del veterinario |
| CU-B02-10 | Consultar mascotas propias | Cliente ve solo ficha básica y operaciones autorizadas |
| CU-B02-11 | Resolver posibles duplicados | Revisión humana y corrección no destructiva |

## 3. Modelo de datos lógico mínimo

- **Propietario**: id tenant-local, nombres/apellidos, contacto (teléfono/correo opcional), dirección opcional, condición activo/inactivo, origen presencial/digital, fecha de registro. No exigir DNI para registrar un animal; las verificaciones manuales se acreditan sin copiar documentos.
- **Mascota**: ID tenant-local, nombre, especie, raza opcional, sexo/opción desconocido, fecha de nacimiento exacta o edad estimada (con indicador de aproximación), color/señas opcionales, condición activo/fallecido, responsable principal actual, fecha de alta.
- **HistorialResponsabilidad**: mascota, propietario anterior/nuevo, fecha efectiva, fecha registro, trabajador que verificó, motivo general, períodos de vigencia sin superposición; conserva responsable pasado.
- **CuentaCliente↔Propietario**: vínculo dentro del tenant, estado, procedencia, fecha de aprobación. Regla propuesta: una cuenta cliente representa un propietario principal local; una mascota pertenece a un solo propietario principal vigente; un propietario puede tener varias mascotas.
- **SolicitudVinculación**: cuenta solicitante, registro propietario objetivo, mascota(s) consultadas solo en contexto autorizado, estado, comprobador, método general de verificación, motivo de rechazo, fechas. Nunca exponer la existencia de registros privados al público.
- **Consulta/Episodio**: mascota, veterinario actor, inicio/fin de atención, motivo, anamnesis, examen, hallazgos, diagnóstico libre, plan/tratamiento, observaciones, estado borrador/finalizada, referencia de cita opcional.
- **Vacunación**: mascota, episodio opcional, vacuna, fecha, dosis/lote opcionales, veterinario, próxima dosis opcional; evento de vacuna registrado en historia clínica.
- **Prescripción**: episodio, veterinario, medicamento en texto, presentación, dosis, vía, frecuencia, duración e instrucciones; fecha. **No** implica receta legal electrónica.
- **Rectificación**: registro objetivo, autor veterinario, fecha, motivo, contenido anterior relevante, contenido nuevo/adenda; jamás sobrescritura silenciosa de registros finalizados.
- **EventoAuditoría**: usuario, cuenta, tenant, acción, objeto, momento, motivo si procede, resultado.

## 4. Reglas funcionales y invariantes

1. **Unicidad de tenant:** todos los registros clínicos y de propiedad comparten veterinaria con la mascota; no se enlazan mascotas de otra veterinaria.
2. **Un único responsable principal actual:** no hay dos propietarios simultáneamente vigentes; las transferencias son operaciones autorizadas y consistentes.
3. **Registro presencial:** recepción o administrador puede crear propietario/mascota sin cuenta, no necesita elegir contraseña ni inventar correo.
4. **Cliente digital:** crea o modifica solo datos básicos propios; no reescribe los datos clínicos ni reasigna mascota de un tercero. Una mascota ya controlada digitalmente por otra cuenta no puede ser reclamada por coincidencia de nombres.
5. **Vinculación manual [CONFIRMADO]:** el cliente solicita; recepción/administración comprueba identidad y relación; registra comprobador/fecha/resultado, sin copias de documentos; si insuficiente, pendiente o rechazo motivado. La aprobación exige que no exista vínculo digital incompatible.
6. **Transferencia [CONFIRMADO]:** solo recepción/administración tras verificación; registra anterior, nuevo, quién, cuándo y motivo; no sustituye autores ni responsables pasados de episodios. **PROP-V1-02:** revoca el acceso de control del propietario anterior sobre la mascota actual y habilita al nuevo, sin regalar automáticamente acceso a historia clínica sensible.
7. **Derechos de lectura [DEC-064]:** clientes ven ficha básica, estado de citas/servicios y sus registros comerciales propios, **no el contenido íntegro de consulta, diagnóstico ni receta**. El nuevo propietario no hereda registros clínicos privados desde una cuenta anterior; veterinarios autorizados del tenant sí disponen del historial para continuidad de atención.
8. **Clínica [CONFIRMADO]:** solo usuarios con rol veterinario crean/corrigen consultas, vacunas, recetas. Administrador sin rol veterinario no las edita. Recepción puede observar datos de agenda e identificación básica, no notas clínicas completas.
9. **Integridad clínica [RO-10]:** borrador editable por su autor; finalización fija identidad y fecha; cambios posteriores como adenda atribuible a veterinario, sin eliminar el contenido previo. No se permite borrar episodios clínicos en V1.
10. **Consultas sin cita [RO-03]:** permitir atención espontánea mediante episodio clínico sin reserva previa; evitar obligar a crear citas ficticias.
11. **Mascota fallecida:** impedir nuevas reservas ordinarias; preservar consulta/vacuna/pedidos anteriores. Cambio de estado con justificación y trazabilidad; acceso clínico para registrar eventual cierre.
12. **Duplicados [RO-02]:** señales de potencial duplicación sin combinación automática. Bloquear fusiones de historias en V1; permitir marcar un registro duplicado como inactivo/referenciado después de revisión de un administrador o recepcionista. Si tiene datos clínicos, requiere evaluación veterinaria antes de cambiar referencias. No borrar evidencia.
13. **Datos mínimos:** ausencia de raza, fecha de nacimiento exacta o correo no impide un registro presencial. Campos clínicos obligatorios al finalizar: motivo, evaluación y veterinario responsable; diagnósticos pueden mantenerse como no concluyentes.

## 5. Flujos principales y alternativos

### F-B02-01 — Alta presencial

Recepción busca posibles registros en su tenant → registra propietario o selecciona existente → registra mascota → asigna responsable principal → confirma → queda disponible para cita/consulta. **Error:** asociación cruzada de tenant, ausencia de nombre o especie, propietario inexistente. **No automatizar deduplicación por teléfono**.

### F-B02-02 — Alta digital

Cliente autenticado abre «Mis mascotas» → crea ficha básica → sistema asocia a su propietario digital local → verifica derechos → guarda. **Alternativa:** cliente ya tiene registro presencial: solicitar vinculación, no registrar una mascota duplicada automáticamente.

### F-B02-03 — Vinculación

Cliente selecciona «Vincular registros presenciales» y solicita revisión sin exposición de coincidencias privadas → personal recibe solicitud → verifica manualmente identidad y relación → aprueba o rechaza con motivo → si aprueba, enlaza cuenta y propietario dentro del tenant; impedir aprobaciones repetidas y conflictivas. Si se cancela/deniega, permanece sin acceso.

### F-B02-04 — Transferencia

Personal autorizado abre mascota → indica nuevo propietario local → verifica cambio → presenta consecuencias de acceso → confirma → sistema cierra período anterior, abre uno nuevo y actualiza propietario principal en una operación indivisible → registra auditoría. **Error:** dos transferencias concurrentes, autoasignación a propietario de otro tenant, mascota previamente inactiva en situación no autorizada.

### F-B02-05 — Consulta clínica

Veterinario autenticado selecciona mascota propia del tenant → abre nuevo episodio (con cita si existe) → registra anamnesis, hallazgos, diagnóstico/impresión, manejo e indicaciones → puede registrar vacuna o receta → guarda borrador → finaliza con identificación de autor y fecha. **Error:** veterinario ajeno al tenant, mascota no encontrada, finalización sin información mínima, doble creación accidental por reintento.

### F-B02-06 — Corrección de atención

Veterinario consulta episodio finalizado → solicita adenda → indica motivo y texto correctivo → confirma → conserva versión previa y registra la adenda. No sobreescribe autor ni fecha originales.

### F-B02-07 — Vacuna/receta

Veterinario registra fecha, vacuna/medicamento e instrucciones, opcionalmente próxima dosis → queda asociada al episodio o historia → recordatorio opcional generado si hay fecha/canal autorizado. **No emitir recetas electrónicas válidas legalmente sin una verificación específica**.

## 6. Matriz de acceso funcional

| Acción | Admin vet | Recepción | Veterinario | Groomer | Cajero | Cliente |
|---|---|---|---|---|---|---|
| Crear/editar propietario básico presencial | Sí | Sí | Solo con rol adicional | No | No | Solo perfil propio |
| Crear/editar mascota básica presencial | Sí | Sí | Solo con rol adicional | No | No | Solo mascotas propias autorizadas |
| Aprobar vinculación/transferencia | Sí | Sí | Solo con rol adicional | No | No | Solicitar solamente |
| Ver historia clínica completa del tenant | Solo si también veterinario | No | Sí | No | No | No (DEC-064) |
| Crear/corregir consulta/vacuna/receta | Solo si también veterinario | No | Sí | No | No | No |
| Ver ficha básica necesaria para su trabajo | Sí | Sí | Sí | Solo tareas asignadas | Solo lo necesario para venta | Solo propios |

Los permisos se acumulan cuando un empleado tiene varios roles, pero **nunca atraviesan tenants**.

## 7. Requisitos funcionales candidatos

| ID | Requisito |
|---|---|
| RF-B02-001 | Permitir registrar propietarios presencialmente sin cuenta digital |
| RF-B02-002 | Permitir crear mascotas básicas con un responsable principal |
| RF-B02-003 | Permitir a un cliente crear/editar solo fichas básicas autorizadas |
| RF-B02-004 | Permitir solicitar vinculación sin revelar registros de terceros |
| RF-B02-005 | Exigir verificación manual para aprobar vinculación y registrar resultado |
| RF-B02-006 | Impedir vínculos digitales contradictorios y aprobaciones repetidas |
| RF-B02-007 | Permitir transferir responsable con autorización e historial inalterable |
| RF-B02-008 | Restringir edición clínica a usuarios con rol veterinario |
| RF-B02-009 | Registrar episodios clínicos con autor/fecha y vínculo opcional a cita |
| RF-B02-010 | Registrar vacuna y próxima dosis opcional |
| RF-B02-011 | Registrar prescripción básica con instrucciones |
| RF-B02-012 | Conservar rectificaciones clínicas y autoría histórica |
| RF-B02-013 | Restringir lectura clínica según el modelo de privacidad aprobado |
| RF-B02-014 | Detectar potenciales duplicados sin fusionarlos automáticamente |
| RF-B02-015 | Registrar y consultar cambios de responsable sin pérdida de historia |
| RF-B02-016 | Mantener aislamiento tenant incluso mediante IDs manipulados |

## 8. Criterios de aceptación representativos

| ID | Entrada/acción | Resultado esperado |
|---|---|---|
| AT-B02-01 | Registrar propietario sin correo | Registro válido, sin cuenta creada implícitamente |
| AT-B02-02 | Crear segunda mascota para mismo propietario | Dos mascotas, mismo responsable válido |
| AT-B02-03 | Dos clientes reclaman mascota presencial | No hay vinculación automática; solo una decisión verificada y auditada |
| AT-B02-04 | Recepción intenta modificar diagnóstico | Denegado, sin cambio en historia |
| AT-B02-05 | Admin sin rol veterinario intenta registrar vacuna | Denegado |
| AT-B02-06 | Veterinario de tenant B consulta episodio de A por ID | Denegado sin filtración |
| AT-B02-07 | Cambio de propietario aprobado | Un único responsable actual, historial pasado preservado |
| AT-B02-08 | Propietario anterior vuelve a solicitar edición de mascota transferida | Denegado; su historial comercial propio no se elimina |
| AT-B02-09 | Dos transferencias concurrentes | Una transición válida; la otra debe revalidarse |
| AT-B02-10 | Veterinario rectifica episodio finalizado | Adenda conserva original, fecha y autor |
| AT-B02-11 | Usuario reintenta guardar misma vacuna por fallo de red | No crea duplicado por mismo identificador de operación |
| AT-B02-12 | Cliente intenta descargar historia clínica completa por API | Denegado mientras no se apruebe un acceso específico |
| AT-B02-13 | Intento de vinculación insuficientemente verificado | Pendiente/rechazo motivado, sin acceso |
| AT-B02-14 | Crear atención sin cita previa | Episodio válido asociado a mascota y veterinario |

## 9. Dependencias, decisiones resueltas y puertas posteriores

- **D-B02-01 (RESUELTO):** DEC-064 confirma que el portal no muestra expediente clínico íntegro. Derechos legales se gestionarán por proceso específico previo a uso real.
- **D-B02-02 (CERRADO V1):** duplicados se revisan manualmente sin fusión destructiva; corregir referencias clínicas requiere revisión veterinaria.
- **G:** revisión de requisitos de datos personales y controles de acceso antes de datos clínicos reales.

**Condición de cierre B02:** flujo sin ambigüedad de propietario actual, ningún acceso clínico indebido, rectificaciones sin destrucción y pruebas AT-B02 aceptadas como diseño. No se han ejecutado pruebas.

## 10. Cierre funcional V1 — autorización clínica, derechos y transferencia

**DEC-064 (aprobación explícita):** los clientes autenticados ven su ficha básica de mascota, sus citas, reservas y pedidos; **no** visualizan ni descargan la historia clínica completa, diagnósticos, narrativas o prescripciones mediante el portal en V1. El acceso completo de veterinarios sigue limitado a rol veterinario y tenant. La prohibición del portal **no anula** posibles derechos legales de acceso a datos personales, que se atenderán por procedimiento de privacidad separado antes de operar con datos reales.

**Transferencias:** el propietario anterior pierde el control operativo vigente de la mascota desde la transferencia autorizada. Conserva acceso a sus transacciones y solicitudes históricas propias, pero no a nuevas operaciones de esa mascota. El nuevo propietario recibe facultades de gestión básica vigentes, no acceso retroactivo irrestricto a información personal ni expedientes históricos del responsable anterior. Veterinarios del tenant mantienen continuidad asistencial por necesidad clínica y autorización de rol; deben quedar auditados los accesos sensibles.

**Presencial-digital:** una cuenta no puede vincularse a registros de terceros por coincidencia de nombre, correo o teléfono; trabajador de recepción/admin verifica y registra autor, fecha, método general y resultado sin almacenar copias de DNI. En conflictos de identidad se deja pendiente o rechaza, sin acceso parcial.

**Corrección clínica:** un episodio finalizado se preserva; solo rol veterinario agrega rectificación atribuible, con motivo y contenido anterior accesible a auditores clínicos autorizados. Una consulta sin cita es válida para atención espontánea. No se automatiza fusión destructiva de historiales duplicados.

**Regla de cierre:** los datos y relaciones son tenant-locales. La eliminación ordinaria de historia clínica finalizada no forma parte de V1. Casos de derechos de titulares, retención legal y acceso por representante legítimo se evalúan antes de uso real; no se improvisan en Codex.
