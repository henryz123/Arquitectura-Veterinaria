-- B04 POS cancellations and physical returns. This migration is additive and tenant-local.
-- Stock remains authoritative in stock_balance/stock_movement; only inspected, saleable returns restock it.
-- No functions, dynamic SQL, extensions, public permissions or destructive DDL.

create table inventory_sales.pos_sale_cancellation (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    sale_id uuid not null,
    operation_id uuid not null,
    reason varchar(500) not null,
    items_left_premises boolean not null,
    stock_restocked boolean not null,
    cancelled_by_principal_id uuid not null,
    created_at timestamptz not null default statement_timestamp(),
    constraint ck_pos_sale_cancellation_reason check (char_length(btrim(reason)) between 3 and 500),
    constraint ck_pos_sale_cancellation_restock check (stock_restocked = not items_left_premises),
    constraint uq_pos_sale_cancellation_tenant_id unique (tenant_id, id),
    constraint uq_pos_sale_cancellation_sale unique (tenant_id, sale_id),
    constraint uq_pos_sale_cancellation_operation unique (tenant_id, operation_id),
    constraint fk_pos_sale_cancellation_sale_same_tenant
        foreign key (tenant_id, sale_id)
        references inventory_sales.pos_sale (tenant_id, id),
    constraint fk_pos_sale_cancellation_actor_same_tenant
        foreign key (tenant_id, cancelled_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create table inventory_sales.pos_return (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    sale_id uuid not null,
    operation_id uuid not null,
    status varchar(32) not null default 'SOLICITADA'
        check (status in ('SOLICITADA', 'RECIBIDA', 'INSPECCIONADA_APTA', 'INSPECCIONADA_NO_APTA', 'CERRADA')),
    reason varchar(500) not null,
    requested_by_principal_id uuid not null,
    received_by_principal_id uuid,
    inspected_by_principal_id uuid,
    closed_by_principal_id uuid,
    created_at timestamptz not null default statement_timestamp(),
    received_at timestamptz,
    inspected_at timestamptz,
    closed_at timestamptz,
    updated_at timestamptz not null default statement_timestamp(),
    constraint ck_pos_return_reason check (char_length(btrim(reason)) between 3 and 500),
    constraint ck_pos_return_timeline check (
        (status = 'SOLICITADA' and received_by_principal_id is null and inspected_by_principal_id is null
            and closed_by_principal_id is null and received_at is null and inspected_at is null and closed_at is null)
        or (status = 'RECIBIDA' and received_by_principal_id is not null and inspected_by_principal_id is null
            and closed_by_principal_id is null and received_at is not null and inspected_at is null and closed_at is null)
        or (status in ('INSPECCIONADA_APTA', 'INSPECCIONADA_NO_APTA') and received_by_principal_id is not null
            and inspected_by_principal_id is not null and closed_by_principal_id is null and received_at is not null
            and inspected_at is not null and closed_at is null)
        or (status = 'CERRADA' and received_by_principal_id is not null and inspected_by_principal_id is not null
            and closed_by_principal_id is not null and received_at is not null and inspected_at is not null and closed_at is not null)
    ),
    constraint uq_pos_return_tenant_id unique (tenant_id, id),
    constraint uq_pos_return_operation unique (tenant_id, operation_id),
    constraint fk_pos_return_sale_same_tenant
        foreign key (tenant_id, sale_id)
        references inventory_sales.pos_sale (tenant_id, id),
    constraint fk_pos_return_requested_by_same_tenant
        foreign key (tenant_id, requested_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id),
    constraint fk_pos_return_received_by_same_tenant
        foreign key (tenant_id, received_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id),
    constraint fk_pos_return_inspected_by_same_tenant
        foreign key (tenant_id, inspected_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id),
    constraint fk_pos_return_closed_by_same_tenant
        foreign key (tenant_id, closed_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create table inventory_sales.pos_return_line (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null,
    return_id uuid not null,
    sale_line_id uuid not null,
    requested_quantity integer not null check (requested_quantity > 0 and requested_quantity <= 1000000),
    received_quantity integer not null default 0 check (received_quantity >= 0 and received_quantity <= requested_quantity),
    restocked_quantity integer not null default 0 check (restocked_quantity >= 0 and restocked_quantity <= received_quantity),
    inspection_result varchar(16) not null default 'PENDIENTE'
        check (inspection_result in ('PENDIENTE', 'APTA', 'PARCIAL', 'NO_APTA')),
    constraint ck_pos_return_line_inspection check (
        (inspection_result = 'PENDIENTE' and restocked_quantity = 0)
        or (inspection_result = 'APTA' and received_quantity > 0 and restocked_quantity = received_quantity)
        or (inspection_result = 'PARCIAL' and restocked_quantity > 0 and restocked_quantity < received_quantity)
        or (inspection_result = 'NO_APTA' and restocked_quantity = 0)
    ),
    constraint uq_pos_return_line_tenant_id unique (tenant_id, id),
    constraint uq_pos_return_line_return_sale_line unique (tenant_id, return_id, sale_line_id),
    constraint fk_pos_return_line_return_same_tenant
        foreign key (tenant_id, return_id)
        references inventory_sales.pos_return (tenant_id, id),
    constraint fk_pos_return_line_sale_line_same_tenant
        foreign key (tenant_id, sale_line_id)
        references inventory_sales.pos_sale_line (tenant_id, id)
);

alter table inventory_sales.stock_movement
    add column pos_return_id uuid;
alter table inventory_sales.stock_movement
    add constraint fk_stock_movement_pos_return_same_tenant
        foreign key (tenant_id, pos_return_id)
        references inventory_sales.pos_return (tenant_id, id);

create index ix_pos_sale_cancellation_tenant_sale
    on inventory_sales.pos_sale_cancellation (tenant_id, sale_id, created_at desc);
create index ix_pos_return_tenant_sale_status
    on inventory_sales.pos_return (tenant_id, sale_id, status, created_at desc, id desc);
create index ix_pos_return_line_tenant_return
    on inventory_sales.pos_return_line (tenant_id, return_id, sale_line_id);
create index ix_stock_movement_tenant_pos_return
    on inventory_sales.stock_movement (tenant_id, pos_return_id, id)
    where pos_return_id is not null;

alter table inventory_sales.pos_sale_cancellation enable row level security;
alter table inventory_sales.pos_return enable row level security;
alter table inventory_sales.pos_return_line enable row level security;

create policy pos_sale_cancellation_tenant_isolation on inventory_sales.pos_sale_cancellation
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy pos_return_tenant_isolation on inventory_sales.pos_return
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy pos_return_line_tenant_isolation on inventory_sales.pos_return_line
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));

revoke all on inventory_sales.pos_sale_cancellation, inventory_sales.pos_return, inventory_sales.pos_return_line from public;
grant select, insert on inventory_sales.pos_sale_cancellation to av_app;
grant select, insert, update on inventory_sales.pos_return to av_app;
grant select, insert, update on inventory_sales.pos_return_line to av_app;
