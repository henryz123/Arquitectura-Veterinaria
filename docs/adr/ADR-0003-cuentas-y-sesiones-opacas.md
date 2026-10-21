# ADR-0003 — Cuentas separadas y sesiones opacas

**Estado:** aceptado; la porción de sesión laboral está materializada en G4-I00 y requiere integración real.
**Contexto V1:** una misma dirección puede existir en cuentas distintas, sin compartir credenciales ni sesiones; cambios/suspensión revocan solo el acceso afectado.

## Decisión

Modelar `StaffAccount` y `ClientAccount` como identidades de contexto separadas. Usar sesión opaca, almacenada como hash, revocable por cuenta/tenant y entregada en cookie `HttpOnly`. El backend sirve SPA/API del mismo origen en producción; mutaciones usan CSRF. G4-I00 implementa solo `StaffAccount`, el hash de sesión/CSRF, la cookie y la revocación; `ClientAccount` permanece fuera del incremento.

## Consecuencias

Se evita que un JWT auto-contenido vigente mantenga permisos tras una desactivación. La tabla de sesiones y rotación requieren limpieza/retención controlada. Un proveedor externo de identidad no se añade en V1.

## Validación

Pruebas para correo compartido, suspensión aislada, logout, cambio de contraseña, CSRF y rate limit sin enumeración.
