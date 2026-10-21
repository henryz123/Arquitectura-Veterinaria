# ADR-0003 — Cuentas separadas y sesiones opacas

**Estado:** aceptado para implementación posterior.
**Contexto V1:** una misma dirección puede existir en cuentas distintas, sin compartir credenciales ni sesiones; cambios/suspensión revocan solo el acceso afectado.

## Decisión

Modelar `StaffAccount` y `ClientAccount` como identidades de contexto separadas. Usar sesión opaca, almacenada como hash, revocable por cuenta/tenant y entregada en cookie `HttpOnly`. El backend sirve SPA/API del mismo origen en producción; mutaciones usan CSRF.

## Consecuencias

Se evita que un JWT auto-contenido vigente mantenga permisos tras una desactivación. La tabla de sesiones y rotación requieren limpieza/retención controlada. Un proveedor externo de identidad no se añade en V1.

## Validación

Pruebas para correo compartido, suspensión aislada, logout, cambio de contraseña, CSRF y rate limit sin enumeración.
