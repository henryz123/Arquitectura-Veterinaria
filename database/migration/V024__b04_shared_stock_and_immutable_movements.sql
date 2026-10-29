-- B04 shared stock foundation. This migration is additive and tenant-local:
-- it creates the only editable stock balance and its immutable movement ledger.
-- Sales, online confirmations and returns are implemented in later verticals.
-- No functions, dynamic SQL, extensions, public permissions or destructive DDL.

create table inventory_sales.stock_balance (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    product_id uuid not null,
    available_quantity integer not null default 0 check (available_quantity >= 0),
    version integer not null default 0 check (version >= 0),
    created_at timestamptz not null default statement_timestamp(),
    updated_at timestamptz not null default statement_timestamp(),
    constraint uq_stock_balance_tenant_product unique (tenant_id, product_id),
    constraint uq_stock_balance_tenant_id unique (tenant_id, id),
    constraint fk_stock_balance_product_same_tenant
        foreign key (tenant_id, product_id)
        references inventory_sales.product (tenant_id, id)
);

create index ix_stock_balance_tenant_product
    on inventory_sales.stock_balance (tenant_id, product_id, id);

create table inventory_sales.stock_movement (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    product_id uuid not null,
    movement_type varchar(32) not null
        check (movement_type in ('ALTA_INICIAL', 'AJUSTE_POSITIVO', 'AJUSTE_NEGATIVO', 'VENTA_POS',
                                'CONFIRMACION_ONLINE', 'CANCELACION_ONLINE', 'DEVOLUCION_APTA', 'RECONCILIACION')),
    quantity_delta integer not null check (quantity_delta <> 0),
    quantity_before integer not null check (quantity_before >= 0),
    quantity_after integer not null check (quantity_after >= 0 and quantity_after = quantity_before + quantity_delta),
    operation_id uuid not null,
    reason varchar(500) not null,
    actor_principal_id uuid not null,
    created_at timestamptz not null default statement_timestamp(),
    constraint ck_stock_movement_reason check (char_length(btrim(reason)) between 3 and 500),
    constraint uq_stock_movement_tenant_id unique (tenant_id, id),
    constraint uq_stock_movement_tenant_operation unique (tenant_id, operation_id),
    constraint fk_stock_movement_product_same_tenant
        foreign key (tenant_id, product_id)
        references inventory_sales.product (tenant_id, id),
    constraint fk_stock_movement_actor_same_tenant
        foreign key (tenant_id, actor_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create index ix_stock_movement_tenant_product_created
    on inventory_sales.stock_movement (tenant_id, product_id, created_at desc, id desc);
create index ix_stock_movement_tenant_operation
    on inventory_sales.stock_movement (tenant_id, operation_id);

alter table inventory_sales.stock_balance enable row level security;
alter table inventory_sales.stock_movement enable row level security;

create policy stock_balance_tenant_isolation on inventory_sales.stock_balance
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy stock_movement_tenant_isolation on inventory_sales.stock_movement
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));

revoke all on inventory_sales.stock_balance, inventory_sales.stock_movement from public;
grant select, insert, update on inventory_sales.stock_balance to av_app;
grant select, insert on inventory_sales.stock_movement to av_app;
