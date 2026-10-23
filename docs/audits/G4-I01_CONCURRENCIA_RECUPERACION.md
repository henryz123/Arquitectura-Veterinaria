# G4-I01 — auditoría de concurrencia de recuperación

## Dictamen vigente

**Control efectivo tras V011.** La vulnerabilidad reproducida el 2026-10-22 se
corrigió exclusivamente en la base local `arquitectura_veterinaria` mediante
`V011__g4_i01_serialize_password_recovery_tokens.sql`.

- SHA-256 de V011: `508A558B90F33C74B1382696214DC45D167E389DED59E0A7F10B63968700167E`.
- Flyway la registró una vez como versión `011`, checksum `-1337847367`.
- No se alteraron V001–V010 ni se utilizó `flyway repair`.

El hallazgo histórico se conserva como evidencia de la causa:

```text
G4_I01_TOKEN_CONCURRENCY=DEFECT_REPRODUCED;ACTIVE_TOKENS=2;SEQUENTIALLY_USABLE=2;CLEANUP=PASS
```

La misma regresión, ejecutada tras V011 con conexiones independientes de
`av_app` y `COMMIT` real, produjo:

```text
G4_I01_TOKEN_CONCURRENCY=CONTROL_EFFECTIVE;ACTIVE_TOKENS=1;SEQUENTIALLY_USABLE=1;CLEANUP=PASS
```

El 2026-10-23 se repitió el guion contra un fixture sintético nuevo, tras los
cambios de aplicación post-V011, con el mismo resultado exacto. La limpieza fue
confirmada por el propio script antes de devolver `PASS`.

No se imprimieron credenciales, hashes, tokens, UUID ni correos sintéticos.

## Causa raíz confirmada

La versión anterior de `identity_auth.request_password_recovery` invalidaba
tokens activos e insertaba el nuevo token sin bloquear una fila estable del
principal ni imponer una invariante de filas activas. Dos transacciones podían
observar el mismo estado inicial, insertar hashes diferentes y confirmar; el
consumo posterior validaba el hash proporcionado y ambos enlaces eran usables.

## Diseño aplicado

V011 establece dos defensas complementarias, acotadas solo a
`PASSWORD_RECOVERY`:

1. `request_password_recovery` bloquea primero la fila estable de
   `identity_auth.principal`, después invalida el token anterior y crea el
   nuevo, dentro de la misma transacción.
2. El índice único parcial
   `uq_one_time_token_password_recovery_active_principal` impide más de una
   fila no consumida y no invalidada por `principal_id` para ese propósito.
   Su predicado usa únicamente estados persistentes; no depende del reloj.
3. `reset_password_with_recovery` toma el mismo orden `principal →
   one_time_token`: localiza el candidato, bloquea el principal y vuelve a
   validar/consumir con un `UPDATE` condicionado. Así evita el orden inverso
   que habría creado un deadlock con la emisión concurrente.
4. La vigencia se compara con `statement_timestamp()`. Conserva la ventana
   funcional de 30 minutos y no convierte una transacción larga en una
   extensión de vigencia.

El preflight encontró cero grupos duplicados activos y cero recuperaciones sin
principal. Por ello la migración no eliminó ni alteró registros existentes para
poder crear el índice.

## Pruebas reales posteriores

`scripts/verify-g4-i01-v011-password-recovery.ps1`, contra fixture aislado y
limpiado por identificadores aleatorios, reportó:

```text
G4_I01_V011_PASSWORD_RECOVERY=PASS;COMMIT_2=PASS;COMMIT_5=PASS;TENANT_ISOLATION=PASS;ROLLBACK=PASS;ERROR_ROLLBACK=PASS;EXPIRY=PASS;REUSE=PASS;DEADLOCK=PASS;REPEAT=PASS;CLEANUP=PASS
```

La ejecución cubrió dos y cinco `COMMIT` simultáneos sobre el mismo principal,
principales distintos, CLIENT con el mismo correo en Aurora/Bosque, `ROLLBACK`,
error controlado después de invalidar antes de insertar, enlace anterior/nuevo,
reutilización, límites de 30 minutos, expiración, orden concurrente
emisión/consumo y tres repeticiones. Cada comprobación dejó cero fixtures.

## Límites y riesgo residual

- La garantía aplicada cubre exclusivamente recuperación de contraseña. Otros
  propósitos de `one_time_token` no se ampliaron sin autorización.
- `CREATE UNIQUE INDEX` no concurrente puede esperar escrituras; es aceptable
  para la base local exclusiva y mantiene la migración atómica. No se infiere
  aptitud para una base compartida sin una ventana operativa revisada.
- RF-AUT-11 y RF-AUT-13 conservan evidencias pendientes ajenas a esta carrera;
  la corrección no los convierte automáticamente en requisitos cerrados.
