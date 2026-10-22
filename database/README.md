# Base de datos

`migration/` es la \u00fanica fuente de esquema y se copia a `backend/target/classes/db/migration` durante la compilaci\u00f3n. Flyway nunca lee migraciones desde una segunda carpeta.

`demo/seed-synthetic.psql` no es una migraci\u00f3n ni debe ejecutarse en producci\u00f3n. Solicita localmente dos contrase\u00f1as sint\u00e9ticas, crea exactamente dos organizaciones ficticias y permite demostrar que el mismo correo puede pertenecer a tenants distintos. No guarda ni muestra contrase\u00f1as.

G4-I00 verific\u00f3 este flujo en una base exclusiva con `av_schema_owner` como propietario y `av_app` como rol de runtime sin privilegios administrativos ni `BYPASSRLS`. La reproducci\u00f3n interactiva, sin secretos en archivos, est\u00e1 en `docs/runbooks/G4-I00_ENTORNO_LOCAL.md`.
