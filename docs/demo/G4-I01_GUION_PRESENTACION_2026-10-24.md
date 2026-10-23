# G4-I01 - guion academico de 5 a 7 minutos

## Mensaje de apertura (0:00 - 0:45)

"Arquitectura - Veterinaria es un SaaS B2B academico para veterinarias independientes
del Peru. El problema que aborda es centralizar, por tenant, la administracion,
autenticacion y los futuros procesos clinicos y comerciales, sin mezclar informacion
entre veterinarias. Esta presentacion muestra un avance local real con datos sinteticos;
no presenta el producto V1 completo."

## Objetivo y arquitectura (0:45 - 1:45)

"La solucion se organiza como un monolito modular con DDD estrategico y arquitectura
hexagonal selectiva. El backend usa Java 21 y Spring Boot 4.1.1; el frontend usa Angular
21 y TypeScript; PostgreSQL 18 conserva los datos. Las sesiones, roles y el tenant se
validan en el servidor. Flyway esta desactivado durante la ejecucion normal para no
cambiar el esquema automaticamente."

## Alcance V1 y avance real (1:45 - 2:35)

"La V1 planifica seis bloques: B01 administracion, SaaS y autenticacion; B02
propietarios, mascotas y clinica; B03 citas y grooming; B04 inventario y POS; B05
ecommerce y pedidos; y B06 integracion transversal. Hoy el vertical demostrable pertenece
a B01. La trazabilidad contiene 26 requisitos B01 con evidencia puntual y 9 parciales. La
suite B01 mas reciente ejecuto 30 casos: 29 correctos y un error conocido. Por eso no se
declara B01 ni G4-I01 como completos."

## Demostracion en vivo (2:35 - 4:45)

1. Abra `http://127.0.0.1:4200/login/veterinaria-aurora` y senale los campos etiquetados,
   el boton y los enlaces Aurora/Bosque.
2. Ingrese la cuenta STAFF sintetica de Aurora sin leer ni proyectar la contrasena. Explique
   que la pagina de trabajo procede de una API protegida.
3. Muestre `Veterinaria Aurora`, su slug y estado. Esto evidencia un contexto tenant
   autenticado, no una maqueta estatica.
4. Pulse "Cerrar sesion" y muestre que la ruta protegida regresa al login.
5. Abra el contexto Bosque, use su credencial sintetica y muestre que el nombre y slug son
   distintos. Explique que el E2E comprobo tambien que Bosque recibe 404 al solicitar el
   perfil de Aurora.

No navegar a recuperacion administrativa, pagos, mascotas, agenda, grooming, inventario,
POS ni pedidos. El reemplazo seguro de roles esta cubierto por pruebas de integracion, no
por una pantalla presentable todavia.

## Evidencia y limitaciones (4:45 - 5:45)

"La ejecucion de preparacion verifico PostgreSQL 18.6, salud HTTP de Spring Boot, Angular,
un E2E autenticado con Aurora y Bosque, logout con CSRF, aislamiento tenant, acceso SaaS
por API y validacion Redocly del contrato OpenAPI. Las capturas provienen de ese E2E y
estan disponibles localmente. La limitacion tecnica pendiente es una ambiguedad `tenant_id`
en la aprobacion de recuperacion administrativa. Esa ruta no se muestra ni se considera
completa."

## Cierre (5:45 - 6:30)

"El siguiente paso, despues de esta presentacion, sera auditar y solicitar por separado
una V013 que redefina solo la funcion afectada; luego se repetiran las pruebas de seguridad
y B01. Posteriormente se retomaran los bloques B02 a B06 y, antes de publicar GitHub el 30
de octubre, se hara una revision de secretos, licencias y datos sinteticos."
