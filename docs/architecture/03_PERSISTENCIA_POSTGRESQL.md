# G2 — Persistencia PostgreSQL 18

## Alcance

Este documento conserva el modelo físico de intención de G2. G4-I00 materializa únicamente `database/migration/V001__g4_i00_identity_tenant.sql` para tenant, cuenta laboral y sesión; el resto de los grupos sigue siendo de intención. PostgreSQL 18.6 está localizado en el equipo, pero no se abrió conexión administrativa ni se creó una base exclusiva durante G4-I00.

## Convenciones físicas

- Claves primarias: UUID v7 o equivalente ordenable, definido por el adaptador de persistencia; no exponer detalles de generación como contrato externo.
- Todo dato privado de veterinaria lleva `tenant_id NOT NULL`. Las claves y relaciones fuertes incluyen el tenant o comprueban el mismo tenant mediante claves compuestas/restricciones.
- Importes: `numeric(12,2)` en PEN, con `CHECK` de no negatividad donde corresponda. Nunca `float` para precio, pago, saldo o tarifa.
- Instantes: `timestamptz` en UTC. Agenda: conservar zona `America/Lima` y datos de propuesta necesarios para reconstruir la intención local.
- Registro lógico por defecto; hechos clínicos, comerciales, movimientos y auditoría no se borran para corregir cifras.
- `created_at`, `updated_at`, actor y correlación son obligatorios en entidades que cambian estado.

## Grupos de tablas previstos

| Grupo | Tablas/relaciones de intención | Invariantes |
|---|---|---|
| Global | `saas_administrator`, `tenant`, `ruc_review`, `tenant_delivery_policy` | RUC y estado no exponen información ajena |
| Identidad | `staff_account`, `client_account`, `role_assignment`, `session`, `one_time_token` | cuentas de trabajador y cliente separadas por tenant/contexto |
| Mascotas y clínica | `owner`, `pet`, `pet_ownership_period`, `client_pet_link`, `clinical_episode`, `clinical_addendum`, `vaccination`, `prescription` | un principal vigente, continuidad clínica y privacidad |
| Agenda | `availability_block`, `service_definition`, `appointment`, `appointment_proposal`, `grooming_attendance` | no traslape de personal confirmado |
| Inventario | `product`, `inventory_balance`, `stock_movement`, `idempotency_record` | un saldo por tenant/producto; saldo no negativo; movimiento único |
| Comercio | `pos_sale`, `pos_sale_line`, `online_order`, `order_line`, `payment_record`, `return_record`, `return_line` | snapshots de precio; pago, entrega y stock separados |
| Transversal | `audit_entry`, `outbox_message`, `notification_delivery` | auditoría sin secretos; fallo de notificación no revierte negocio |

## Tenancy como defensa en profundidad

La aplicación autentica el actor y calcula el tenant permitido antes de crear una transacción. Cada transacción de tenant fija una variable local de conexión (`SET LOCAL app.tenant_id`) y opera con un rol de aplicación que no es propietario de tablas. Las tablas privadas deben tener RLS que compare ese valor y claves/relaciones que impidan un cruce accidental.

Las migraciones usan un rol técnico separado y controlado; el proceso de aplicación no puede ser dueño de tablas ni tener `BYPASSRLS`. V001 habilita RLS sin `FORCE` para permitir una única función `SECURITY DEFINER` de lookup de login con búsqueda parametrizada y `search_path` fijo. Las pruebas de integración deben demostrar que el pooling no filtra el contexto entre transacciones. No se confía en un `tenant_id` suministrado por Angular.

## Integridad y concurrencia

| Riesgo | Mecanismo previsto | Prueba mínima futura |
|---|---|---|
| Última unidad | bloqueo de `inventory_balance` por tenant/producto, `CHECK cantidad_vendible >= 0`, movimiento con identificador único | dos transacciones POS/web: solo una confirma |
| Reintento | `idempotency_record` por operación, actor/tenant y huella de petición | mismo `Idempotency-Key`: misma respuesta, un movimiento |
| Traslape | restricción temporal por empleado/intervalo confirmado, más comprobación de caso de uso | confirmaciones concurrentes se rechazan de forma determinista |
| Restock duplicado | referencia única de evento origen y línea/cantidad validada | doble devolución: una entrada de stock |
| Último administrador | restricción de negocio protegida por transacción y bloqueo adecuado | bajas/roles concurrentes no dejan tenant sin admin |

Los detalles exactos de índices, extensión necesaria para rangos y sentencia de exclusión se incluyen como aceptación de la primera migración, no como un fragmento SQL prematuro. Cada migración futura será inmutable, pequeña, reversible cuando sea seguro y validada contra una base nueva.

## Flyway y datos sintéticos

Flyway es el único mecanismo de evolución de esquema del producto. `database/migration/` es la única fuente y Maven la copia a `target/classes/db/migration`; Spring Boot 4.1.1 gestiona Flyway 12.4.0 y su módulo PostgreSQL. Los datos sintéticos van separados en `database/demo/seed-synthetic.psql`, no contienen contraseñas y solo se cargan mediante prompt interactivo de demostración.

## Índices de partida que deben justificarse

- `(tenant_id, normalized_email)` para identidades de contexto.
- `(tenant_id, status, created_at DESC)` en listados operativos.
- `(tenant_id, product_id)` único en saldo y `(tenant_id, operation_id)` en idempotencia.
- índices por `pet_id`, `owner_id`, `assigned_staff_id` y rango temporal de agenda según planes reales.
- índices de auditoría por tenant, recurso, actor y fecha, sin indexar contenido sensible libre.

La aceptación final de índices se basa en `EXPLAIN (ANALYZE, BUFFERS)` sobre datos sintéticos representativos, no en intuición.
