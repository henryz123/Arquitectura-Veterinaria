# Base de datos

`migration/` es la \u00fanica fuente de esquema y se copia a `backend/target/classes/db/migration` durante la compilaci\u00f3n. Flyway nunca lee migraciones desde una segunda carpeta.

`demo/seed-synthetic.psql` no es una migraci\u00f3n ni debe ejecutarse en producci\u00f3n. Solicita localmente dos contrase\u00f1as sint\u00e9ticas, crea exactamente dos organizaciones ficticias y permite demostrar que el mismo correo puede pertenecer a tenants distintos. No guarda ni muestra contrase\u00f1as.

Antes de ejecutar migraciones, un administrador local debe crear una base exclusiva y un rol de aplicaci\u00f3n sin privilegios administrativos. El procedimiento interactivo, sin secretos en archivos, est\u00e1 en `docs/runbooks/G4-I00_ENTORNO_LOCAL.md`.
