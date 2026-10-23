# G4-I01 — auditoría de concurrencia de recuperación

## Resultado

**Defecto reproducido; no corregido en esta instrucción.**

El 2026-10-22 se ejecutó `scripts/audit-g4-i01-token-concurrency.ps1` contra
`arquitectura_veterinaria` local, con dos conexiones independientes de `av_app`
y `COMMIT` real. El resultado fue:

```text
G4_I01_TOKEN_CONCURRENCY=DEFECT_REPRODUCED;ACTIVE_TOKENS=2;SEQUENTIALLY_USABLE=2;CLEANUP=PASS
```

No se imprimieron credenciales, hashes, tokens, UUID ni correos sintéticos.

## Aislamiento y limpieza

- El script crea un tenant y un cliente sintéticos con identificadores aleatorios.
- No consulta ni modifica las cuentas de Aurora, Bosque o SaaS.
- La preparación y las dos solicitudes usan funciones públicas autorizadas de
  `av_app`; la creación y limpieza del fixture usan solamente `av_schema_owner`.
- La limpieza elimina tokens, perfil, principal, auditoría, notificación y tenant
  únicamente por los identificadores aleatorios del fixture y se confirma antes
  de que el script termine.

## Causa técnica comprobada

`identity_auth.request_password_recovery` invalida los tokens activos existentes
y después inserta un token nuevo. Dos transacciones concurrentes que no encuentran
una fila activa al inicio pueden ejecutar ambos `UPDATE`, insertar hashes
distintos y confirmar. No existe un bloqueo de la identidad ni una invariante de
base que impida dos filas activas de propósito `PASSWORD_RECOVERY` para el mismo
principal. `reset_password_with_recovery` consume sólo el hash recibido; por eso
ambos enlaces confirmados resultaron utilizables secuencialmente.

## Propuesta para una autorización posterior

No se creó V011. Si se autoriza una corrección, preparar una nueva migración
auditada que, como mínimo:

1. serialice la emisión mediante bloqueo de la identidad objetivo antes de
   invalidar e insertar;
2. añada una invariante de defensa en profundidad para un único token activo por
   `(principal_id, purpose)`, comprobando primero los datos existentes;
3. preserve `SECURITY DEFINER`, propietario, `search_path`, RLS y privilegios
   mínimos vigentes;
4. añada pruebas con dos `COMMIT`, consumo del token más reciente, rechazo del
   previo, rollback y limpieza verificable.

Hasta esa autorización, RF-AUT-11 y RF-AUT-13 no pueden declararse cerrados y
G4-I01 no debe avanzar a G4-I02.
