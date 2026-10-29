-- B04 point-of-sale foundation. This migration is additive and tenant-local.
-- Stock continues to have one authority in stock_balance/stock_movement.
-- No functions, dynamic SQL, extensions, public permissions or destructive DDL.

create table inventory_sales.pos_sale (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    cashier_principal_id uuid not null,
    status varchar(32) not null default 'PREPARACION'
        check (status in ('PREPARACION', 'COMPLETADA', 'ANULADA', 'DEVOLUCION_PARCIAL', 'DEVUELTA')),
    operation_id uuid not null,
    total_amount_pen numeric(18, 2) not null default 0 check (total_amount_pen >= 0),
    created_at timestamptz not null default statement_timestamp(),
    completed_at timestamptz,
    updated_at timestamptz not null default statement_timestamp(),
    constraint ck_pos_sale_completion_state check (
        (status = 'PREPARACION' and completed_at is null)
        or (status <> 'PREPARACION' and completed_at is not null)
    ),
    constraint uq_pos_sale_tenant_id unique (tenant_id, id),
    constraint uq_pos_sale_tenant_operation unique (tenant_id, operation_id),
    constraint fk_pos_sale_cashier_same_tenant
        foreign key (tenant_id, cashier_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create table inventory_sales.pos_sale_line (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null,
    sale_id uuid not null,
    product_id uuid not null,
    product_sku_snapshot varchar(64) not null,
    product_title_snapshot varchar(160) not null,
    inventory_managed boolean not null,
    quantity integer not null check (quantity > 0 and quantity <= 1000000),
    unit_price_pen numeric(12, 2) not null check (unit_price_pen >= 0),
    subtotal_pen numeric(18, 2) not null check (subtotal_pen = quantity * unit_price_pen),
    created_at timestamptz not null default statement_timestamp(),
    constraint ck_pos_sale_line_sku check (char_length(btrim(product_sku_snapshot)) between 1 and 64),
    constraint ck_pos_sale_line_title check (char_length(btrim(product_title_snapshot)) between 1 and 160),
    constraint uq_pos_sale_line_tenant_id unique (tenant_id, id),
    constraint uq_pos_sale_line_sale_product unique (tenant_id, sale_id, product_id),
    constraint fk_pos_sale_line_sale_same_tenant
        foreign key (tenant_id, sale_id)
        references inventory_sales.pos_sale (tenant_id, id),
    constraint fk_pos_sale_line_product_same_tenant
        foreign key (tenant_id, product_id)
        references inventory_sales.product (tenant_id, id)
);

create table inventory_sales.pos_manual_payment (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null,
    sale_id uuid not null,
    payment_method varchar(16) not null
        check (payment_method in ('EFECTIVO', 'TRANSFERENCIA', 'OTRO')),
    declared_amount_pen numeric(18, 2) not null check (declared_amount_pen >= 0),
    status varchar(32) not null
        check (status in ('PENDIENTE_VALIDACION', 'VALIDADO', 'RECHAZADO')),
    declared_by_principal_id uuid not null,
    validated_by_principal_id uuid,
    declared_at timestamptz not null default statement_timestamp(),
    validated_at timestamptz,
    constraint ck_pos_manual_payment_validation check (
        (status = 'PENDIENTE_VALIDACION' and validated_by_principal_id is null and validated_at is null)
        or (status in ('VALIDADO', 'RECHAZADO') and validated_by_principal_id is not null and validated_at is not null)
    ),
    constraint uq_pos_manual_payment_tenant_id unique (tenant_id, id),
    constraint uq_pos_manual_payment_sale unique (tenant_id, sale_id),
    constraint fk_pos_manual_payment_sale_same_tenant
        foreign key (tenant_id, sale_id)
        references inventory_sales.pos_sale (tenant_id, id),
    constraint fk_pos_manual_payment_declared_by_same_tenant
        foreign key (tenant_id, declared_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id),
    constraint fk_pos_manual_payment_validated_by_same_tenant
        foreign key (tenant_id, validated_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

alter table inventory_sales.stock_movement
    add column pos_sale_id uuid;
alter table inventory_sales.stock_movement
    add constraint fk_stock_movement_pos_sale_same_tenant
        foreign key (tenant_id, pos_sale_id)
        references inventory_sales.pos_sale (tenant_id, id);

create index ix_pos_sale_tenant_status_completed
    on inventory_sales.pos_sale (tenant_id, status, completed_at desc, id desc);
create index ix_pos_sale_line_tenant_sale
    on inventory_sales.pos_sale_line (tenant_id, sale_id, id);
create index ix_pos_manual_payment_tenant_sale
    on inventory_sales.pos_manual_payment (tenant_id, sale_id, id);
create index ix_stock_movement_tenant_pos_sale
    on inventory_sales.stock_movement (tenant_id, pos_sale_id, id)
    where pos_sale_id is not null;

alter table inventory_sales.pos_sale enable row level security;
alter table inventory_sales.pos_sale_line enable row level security;
alter table inventory_sales.pos_manual_payment enable row level security;

create policy pos_sale_tenant_isolation on inventory_sales.pos_sale
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy pos_sale_line_tenant_isolation on inventory_sales.pos_sale_line
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy pos_manual_payment_tenant_isolation on inventory_sales.pos_manual_payment
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));

revoke all on inventory_sales.pos_sale, inventory_sales.pos_sale_line, inventory_sales.pos_manual_payment from public;
grant select, insert, update on inventory_sales.pos_sale to av_app;
grant select, insert on inventory_sales.pos_sale_line to av_app;
grant select, insert, update on inventory_sales.pos_manual_payment to av_app;
