-- B04 catalog foundation. This migration is additive and tenant-local: it
-- introduces administrative categories and products only. Stock, movements,
-- sales, payments and orders are intentionally deferred to later migrations.
-- It creates no SECURITY DEFINER function, dynamic SQL, extension, public
-- permission, elevated runtime role or destructive alteration.

create schema inventory_sales;

revoke all on schema inventory_sales from public;
revoke create on schema inventory_sales from av_app;
grant usage on schema inventory_sales to av_app;

create table inventory_sales.product_category (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    name varchar(120) not null,
    status varchar(16) not null default 'ACTIVA'
        check (status in ('ACTIVA', 'INACTIVA')),
    created_by_principal_id uuid not null,
    created_at timestamptz not null default statement_timestamp(),
    updated_at timestamptz not null default statement_timestamp(),
    constraint ck_product_category_name check (char_length(btrim(name)) between 1 and 120),
    constraint uq_product_category_tenant_id unique (tenant_id, id),
    constraint fk_product_category_actor_same_tenant
        foreign key (tenant_id, created_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create unique index uq_product_category_tenant_name
    on inventory_sales.product_category (tenant_id, lower(btrim(name)));
create index ix_product_category_tenant_status_name
    on inventory_sales.product_category (tenant_id, status, name, id);

create table inventory_sales.product (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    category_id uuid,
    sku varchar(64) not null,
    title varchar(160) not null,
    description varchar(2000),
    sale_price_pen numeric(12, 2) not null,
    status varchar(24) not null default 'ACTIVO_NO_PUBLICADO'
        check (status in ('ACTIVO_PUBLICADO', 'ACTIVO_NO_PUBLICADO', 'INACTIVO')),
    inventory_managed boolean not null default true,
    image_url varchar(500),
    created_by_principal_id uuid not null,
    created_at timestamptz not null default statement_timestamp(),
    updated_at timestamptz not null default statement_timestamp(),
    constraint ck_product_sku check (char_length(btrim(sku)) between 1 and 64),
    constraint ck_product_title check (char_length(btrim(title)) between 1 and 160),
    constraint ck_product_description check (description is null or char_length(btrim(description)) between 1 and 2000),
    constraint ck_product_sale_price_pen check (sale_price_pen >= 0),
    constraint ck_product_image_url check (image_url is null or char_length(btrim(image_url)) between 1 and 500),
    constraint uq_product_tenant_id unique (tenant_id, id),
    constraint fk_product_category_same_tenant
        foreign key (tenant_id, category_id)
        references inventory_sales.product_category (tenant_id, id),
    constraint fk_product_actor_same_tenant
        foreign key (tenant_id, created_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create unique index uq_product_tenant_sku
    on inventory_sales.product (tenant_id, lower(btrim(sku)));
create index ix_product_tenant_status_title
    on inventory_sales.product (tenant_id, status, title, id);
create index ix_product_tenant_category
    on inventory_sales.product (tenant_id, category_id, title, id);

alter table inventory_sales.product_category enable row level security;
alter table inventory_sales.product enable row level security;

create policy product_category_tenant_isolation on inventory_sales.product_category
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy product_tenant_isolation on inventory_sales.product
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));

revoke all on all tables in schema inventory_sales from public;
grant select, insert, update on inventory_sales.product_category to av_app;
grant select, insert, update on inventory_sales.product to av_app;
