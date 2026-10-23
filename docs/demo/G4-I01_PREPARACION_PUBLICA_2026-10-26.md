# Preparacion de demostracion publica - 26/10/2026

## Estado real

No hay despliegue, URL publica, remoto Git ni repositorio GitHub configurado.
El proyecto no debe presentarse como listo para publicacion: B01 mantiene diez
requisitos parciales y B02--B06 no se han implementado ni verificado en este
incremento. Esta guia es una decision operativa pendiente, no un cambio de
infraestructura.

## Criterio para una demostracion de S/ 0

El camino viable es una demostracion efimera con datos exclusivamente
sinteticos, sin pago, sin dominio propio y con un repositorio publico solo
despues de una revision final de secretos, licencia y procedencia de terceros.
No sirve como produccion ni como repositorio de datos clinicos reales.

Render ofrece URL `onrender.com` para servicios web y sitios estaticos; sus
planes gratuitos pueden reiniciarse, pierden el sistema de archivos local y
una instancia web se suspende tras 15 minutos sin trafico. Una base Render
Postgres gratuita expira despues de 30 dias y no incluye backups. Esto hace
que sea aceptable solo para la presentacion y obliga a conservar una semilla
reproducible y a anunciar el posible arranque en frio. Fuentes:

- [Render: despliegue gratuito](https://render.com/docs/free)
- [Render: primer despliegue y URL publica](https://render.com/docs/your-first-deploy)

GitHub permite crear un repositorio publico, pero la visibilidad publica hace
que cualquier contenido subido sea accesible. El escaneo de secretos y la
proteccion de push ayudan, pero no reemplazan la revision local previa; se
deben habilitar como defensa adicional. Fuentes:

- [GitHub: crear un repositorio](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-new-repository)
- [GitHub: funcionalidades de seguridad](https://docs.github.com/en/code-security/getting-started/github-security-features)

## Secuencia propuesta, sin ejecutar

1. Cerrar primero los bloqueos de seguridad de B01 que se autoricen y
   re-ejecutar pruebas backend, frontend, OpenAPI, aislamiento tenant y E2E.
2. Realizar revision reproducible de Git: secretos, `.env*`, backups, hashes,
   dependencias, binarios, datos personales, archivos de terceros y licencia.
3. Decidir expresamente la composicion de demostracion: Angular como sitio
   estatico, Spring Boot como unico servicio web y PostgreSQL administrado
   temporal con una semilla sintetica idempotente. No usar disco local ni
   SQLite como estado de demostracion.
4. Preparar archivos de despliegue y variables solo despues de ADR/permiso;
   las credenciales se ingresaran en el panel del proveedor, nunca en Git.
5. Crear el repositorio remoto publico vacio sin inicializar README,
   `.gitignore` ni licencia remotos, para evitar un merge accidental. Conectar
   y publicar solo tras autorizacion separada.
6. Ejecutar el checklist de demostracion: build desde cero, migraciones sobre
   la base exclusiva de demo, semilla sintetica, login STAFF/CLIENT permitido,
   denegacion cross-tenant, CSRF, recorrido E2E y captura de evidencia.
7. El dia de la presentacion, abrir la URL con antelacion para absorber el
   arranque en frio, comprobar salud, y tener una grabacion local de respaldo
   que no revele secretos.

## Bloqueos antes de anunciar una URL

- V012 no esta aplicada ni sus rutas afectadas estan verificadas como
  funcionales.
- No existe configuracion revisada para compilar/desplegar Java 21 + Angular
  21 como servicios publicos.
- La revision de accesibilidad automatizada con axe aun requiere una
  dependencia compatible disponible localmente o una autorizacion de
  instalacion.
- Un servicio gratuito no ofrece garantia de disponibilidad, persistencia ni
  backup adecuada para datos reales; el alcance permanece academico y
  sintetico.

Por tanto, el hito del 26/10 es factible solo como avance controlado si se
autoriza la infraestructura y se superan esos controles. No se afirma que la
URL ni el repositorio existan todavia.
