# G2 — Seguridad, multitenancy y amenazas

## Política de autorización

Toda operación privada verifica, en este orden: sesión válida, tipo de cuenta, tenant de confianza, estado del tenant, rol efectivo, titularidad/relación con recurso y transición permitida. La ausencia de un permiso es denegación. Un rol no sustituye la comprobación de propiedad de un cliente ni permite revisar clínica fuera de su ámbito.

| Rol/contexto | Límite técnico esencial |
|---|---|
| Administrador SaaS | consulta y gestiona metadatos globales autorizados; sin expedientes clínicos ni detalle comercial cotidiano |
| Administrador veterinaria | tenant propio, con roles adicionales explícitos para acciones especializadas |
| Veterinario | escritura clínica; no autoridad automática de caja ni de roles |
| Recepción/grooming | agenda y datos mínimos; sin notas clínicas completas |
| Cajero | POS/pagos/pedidos autorizados; no ajustes manuales sin rol administrador |
| Cliente | solo cuenta, mascota enlazada, citas y comercio propios; sin contenido clínico completo |
| Visitante | catálogo público de un tenant activo, sin datos privados |

## Modelo de amenazas proporcional

| Amenaza | Control de diseño | Evidencia futura |
|---|---|---|
| IDOR/cross-tenant | `ActorContext`, autorización de recurso, RLS, claves por tenant y pruebas negativas | pruebas integración por rol/tenant |
| robo/reuso de sesión | cookie segura, hash de sesión en DB, rotación/invalidez, límite de intentos y CSRF | pruebas de login, logout y revocación |
| enumeración de cuentas | mensajes neutros, timing/ratelimit y no exponer estado de identidad | pruebas de error y límite |
| inyección SQL/XSS | parámetros ORM/JDBC, validación de entrada, encoding de Angular, CSP posterior | análisis estático + pruebas de seguridad |
| doble gasto/stock | bloqueo, idempotencia, restricciones y auditoría | concurrencia PostgreSQL |
| modificación clínica retroactiva | episodio finalizado inmutable y adenda atribuible | integración de dominio |
| secretos en Git/logs | `.gitignore`, configuración externa, redacción de logs y escaneo antes de publicar | escaneo de secretos y revisión humana |
| abuso de subida de archivos | V1 no habilita almacenamiento de archivos hasta especificar tipos/tamaño/escaneo/autorización | ADR y prueba futura |

## Datos, privacidad y registros

Solo datos sintéticos en repositorio, pruebas y demostración. Los logs incorporan `traceId`, resultado y metadatos mínimos, pero nunca contraseñas, tokens, cookies, diagnósticos clínicos ni contenido libre innecesario. La retención legal, consentimiento para datos reales y respuesta ante incidentes permanecen puertas externas para cualquier uso no académico.

## Gestión de secretos y configuración

El código futuro recibe secretos únicamente de variables/configuración externa del entorno. Archivos permitidos de ejemplo contienen nombres de variables, nunca valores. La configuración se separa por perfiles local, test y demo, con valores seguros por defecto y fallos de arranque si falta un secreto obligatorio. No se registra ni se acepta una credencial en argumentos, URL, chat, captura o Git.

## Controles a verificar antes de publicación

Revisión de dependencias, SCA/secret scanning, pruebas de autorización negativa, cabeceras de seguridad, CORS, CSRF, manejo uniforme de errores, rate limits en acceso público y revisión independiente. Agent Browser y su auditoría de accesibilidad se ejecutan solo cuando haya una aplicación y un navegador verificados.
