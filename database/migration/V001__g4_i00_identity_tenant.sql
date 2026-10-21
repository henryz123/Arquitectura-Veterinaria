create schema if not exists tenancy;
create schema if not exists identity_auth;

create table tenancy.tenant (
    id uuid primary key,
    slug varchar(80) not null unique,
    commercial_name varchar(160) not null,
    status varchar(16) not null check (status in ('ACTIVO', 'SUSPENDIDO')),
    created_at timestamptz not null default current_timestamp,
    constraint ck_tenant_slug check (slug ~ '^[a-z0-9]+(?:-[a-z0-9]+)*$')
);

create table identity_auth.staff_account (
    id uuid primary key,
    tenant_id uuid not null references tenancy.tenant(id),
    normalized_email varchar(254) not null,
    password_hash varchar(100) not null,
    role_code varchar(64) not null,
    status varchar(16) not null check (status in ('ACTIVA', 'INACTIVA')),
    created_at timestamptz not null default current_timestamp,
    unique (tenant_id, normalized_email)
);

create index ix_staff_account_tenant_email on identity_auth.staff_account (tenant_id, normalized_email);

create table identity_auth.user_session (
    id uuid primary key,
    account_id uuid not null references identity_auth.staff_account(id),
    tenant_id uuid not null references tenancy.tenant(id),
    account_type varchar(16) not null check (account_type in ('STAFF', 'CLIENT')),
    role_code varchar(64) not null,
    token_hash char(64) not null unique,
    csrf_hash char(64) not null,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null default current_timestamp
);

create index ix_user_session_token_active on identity_auth.user_session (token_hash, expires_at)
    where revoked_at is null;

alter table tenancy.tenant enable row level security;
alter table identity_auth.staff_account enable row level security;
alter table identity_auth.user_session enable row level security;

create policy tenant_context_isolation on tenancy.tenant
    using (id::text = current_setting('app.tenant_id', true));

create policy staff_account_tenant_isolation on identity_auth.staff_account
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));

create policy session_lookup_or_tenant_access on identity_auth.user_session
    using (
        token_hash = current_setting('app.session_hash', true)
        or tenant_id::text = current_setting('app.tenant_id', true)
    )
    with check (tenant_id::text = current_setting('app.tenant_id', true));

create function identity_auth.lookup_staff_identity(p_tenant_slug text, p_normalized_email text)
returns table (
    account_id uuid,
    tenant_id uuid,
    tenant_slug varchar,
    tenant_commercial_name varchar,
    tenant_status varchar,
    account_status varchar,
    password_hash varchar,
    role_code varchar
)
language sql
security definer
set search_path = pg_catalog, tenancy, identity_auth
as $$
    select a.id, t.id, t.slug, t.commercial_name, t.status, a.status, a.password_hash, a.role_code
    from tenancy.tenant t
    join identity_auth.staff_account a on a.tenant_id = t.id
    where t.slug = p_tenant_slug and a.normalized_email = p_normalized_email
$$;

revoke all on function identity_auth.lookup_staff_identity(text, text) from public;
