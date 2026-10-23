# G4-I01 - preparacion de demostracion y propuesta S/ 0

## Dictamen al 2026-10-23

> Actualizacion de congelacion: V012 ya corrigio el reemplazo de roles y esa
> operacion puede demostrarse con la regresion focal 3/3. La aprobacion de
> recuperacion administrativa permanece excluida por la ambiguedad residual de
> `tenant_id`; consultar `docs/status/G4-I01_PUNTO_REANUDACION_2026-10-23.md`.

**La demostracion local es parcial y no esta autorizada ni lista para URL
publica.** El vertical autentica STAFF, CLIENT y SaaS con datos sinteticos,
mantiene aislamiento tenant y comprueba logout/CSRF. No representa B02-B06. El
reemplazo de roles ya esta verificado despues de V012; la aprobacion de
recuperacion administrativa continua bloqueada por la ambiguedad residual de
`tenant_id` y no debe demostrarse como funcional.

## Evidencia local vigente

1. B01 integrado despues de V012: 30 casos ejecutados, 29 correctos y 1 error
   conocido de recuperacion administrativa. No se declara B01 completo ni se
   muestra esa ruta en la demostracion.
2. Backend temporal y Angular local: E2E post-cambio
   `G4_I01_AUTHENTICATED_BROWSER_E2E=PASS` con login STAFF Aurora/Bosque,
   perfil propio, 404 cross-tenant, logout y CSRF SaaS/STAFF.
3. Angular: build superado y 1/1 prueba unitaria superada.
4. Agent Browser 0.27.0: arbol semantico y foco `email -> password` verificados
   a 375, 768 y 1440 px mediante `agent-browser.cmd`.
5. Accesibilidad acotada: labels nativos, landmark `main`, heading, navegacion,
   errores `role=alert` y foco visible. El token de foco ahora es `#9c4e13`
   (5.98:1 sobre blanco y 5.64:1 sobre canvas); no sustituye auditoria WCAG
   completa ni linea visual comparativa.

## Guion local permitido

1. Confirmar que Flyway esta desactivado y que se usa la base local exclusiva.
2. Iniciar backend con JDK 21 y Angular con `ng.cmd serve --host 127.0.0.1
   --port 4200 --proxy-config proxy.conf.json`.
3. Entrar a Aurora como STAFF sintetico y mostrar el nombre del tenant propio.
4. Cerrar sesion y comprobar que el area protegida devuelve denegacion.
5. Repetir el acceso con Bosque solo para evidenciar contexto distinto; no
   revelar cookies, CSRF, tokens ni contrasenas.
6. Declarar explicitamente que CLIENT solo tiene el contexto B01 y que las
   funciones de mascotas, agenda, grooming, POS y pedidos pertenecen a bloques
   aun no implementados.

No mostrar como exito: aprobacion de recuperacion administrativa, notificacion
real por correo, pagos, datos reales ni supuestas funciones de B02-B06.

## Propuesta tecnica S/ 0 para una futura URL publica

**Opcion propuesta, sujeta a autorizacion posterior:** un unico Web Service
Free de Render que entregue Spring Boot y los artefactos Angular en el mismo
origen, con PostgreSQL Free de Neon. Mantiene un solo producto y evita redisenar
la autenticacion actual basada en cookie/CSRF para dos origenes distintos.

- Render ofrece Web Services gratuitos con URL `onrender.com`, TLS gestionado y
  hasta 750 horas de instancia al mes; se suspenden tras 15 minutos sin trafico
  y el primer acceso posterior puede tardar alrededor de un minuto. Es aceptable
  solo como avance academico, no como produccion. [Limitaciones Free de
  Render](https://render.com/docs/free)
- No usar Render Postgres Free para la entrega final: su propia documentacion
  indica expiracion a los 30 dias. [Limitaciones de almacenamiento de
  Render](https://render.com/docs/free)
- Neon anuncio para su plan Free 1 GB por proyecto y 100 CU-hours mensuales por
  proyecto. Es la propuesta de persistencia sin costo, condicionada a revisar
  sus limites al crear la cuenta. [Plan Free de Neon, octubre de
  2026](https://neon.com/blog/neon-free-plan-1-gb-per-project)

Una separacion inmediata Angular estatico + API en otro origen se **descarta por
ahora**: exigiría revisar cookies `Secure`/`SameSite`, CORS y CSRF. La opcion de
mismo origen solo requiere una futura integracion aprobada del build Angular en
el empaquetado del backend; hoy esa integracion no existe y no se ha creado
scaffolding ni configuracion de despliegue.

## Puerta previa a cualquier despliegue

1. Autorizar y auditar una migracion nueva que corrija V004/V005; aplicar solo
   en la base autorizada y repetir B01, seguridad y concurrencia.
2. Decidir y probar el empaquetado Angular -> Spring Boot en el mismo origen,
   con health check, `PORT` de plataforma y `AV_SECURE_COOKIE=true` solo bajo
   HTTPS.
3. Sustituir o aislar `LocalNotificationMailbox`; nunca exponer enlaces opacos
   en logs o interfaz publica.
4. Ejecutar revision de secretos, licencias, datos sinteticos, OpenAPI, E2E y
   accesibilidad antes de crear remoto/publicar GitHub.
5. Despertar el servicio antes de la exposicion academica y preparar una
   contingencia local por el cold start del plan Free.

No se creo cuenta, remoto, servicio, URL ni despliegue durante G4-I01.
