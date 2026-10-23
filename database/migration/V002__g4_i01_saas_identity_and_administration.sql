create schema if not exists tenant_admin;

alter table tenancy.tenant
    drop constraint if exists tenant_status_check;
alter table tenancy.tenant
    drop constraint if exists ck_tenant_status;
alter table tenancy.tenant
    add constraint ck_tenant_status check (status in ('ACTIVO', 'SUSPENDIDO'));
alter table tenancy.tenant
    add column if not exists ruc varchar(11),
    add column if not exists commercial_address varchar(240),
    add column if not exists commercial_phone varchar(40),
    add column if not exists commercial_email varchar(254);
alter table tenancy.tenant
    add constraint ck_tenant_ruc_format check (ruc is null or ruc ~ '^[0-9]{11}$');
create unique index if not exists uq_tenant_ruc_present on tenancy.tenant (ruc) where ruc is not null;

create table identity_auth.principal (
    id uuid primary key,
    account_type varchar(16) not null check (account_type in ('STAFF', 'CLIENT', 'SAAS')),
    password_hash varchar(100),
    status varchar(32) not null check (status in ('INVITADA', 'PENDIENTE_VERIFICACION', 'ACTIVA', 'DESACTIVADA')),
    email_verified_at timestamptz,
    password_changed_at timestamptz,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp
);

create table identity_auth.account_profile (
    principal_id uuid primary key references identity_auth.principal(id),
    tenant_id uuid not null references tenancy.tenant(id),
    account_type varchar(16) not null check (account_type in ('STAFF', 'CLIENT')),
    normalized_email varchar(254) not null,
    status varchar(32) not null check (status in ('INVITADA', 'PENDIENTE_VERIFICACION', 'ACTIVA', 'DESACTIVADA')),
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    unique (tenant_id, account_type, normalized_email)
);
create index ix_account_profile_tenant_type_email on identity_auth.account_profile (tenant_id, account_type, normalized_email);

insert into identity_auth.principal (id, account_type, password_hash, status, email_verified_at, password_changed_at, created_at, updated_at)
select id, 'STAFF', password_hash,
       case when status = 'ACTIVA' then 'ACTIVA' else 'DESACTIVADA' end,
       created_at, created_at, created_at, created_at
from identity_auth.staff_account
on conflict (id) do nothing;

insert into identity_auth.account_profile (principal_id, tenant_id, account_type, normalized_email, status, created_at, updated_at)
select id, tenant_id, 'STAFF', normalized_email,
       case when status = 'ACTIVA' then 'ACTIVA' else 'DESACTIVADA' end,
       created_at, created_at
from identity_auth.staff_account
on conflict (principal_id) do nothing;

create table identity_auth.role_assignment (
    id uuid primary key,
    tenant_id uuid not null references tenancy.tenant(id),
    staff_principal_id uuid not null references identity_auth.account_profile(principal_id),
    role_code varchar(32) not null check (role_code in ('ADMIN_VETERINARIA', 'RECEPCION', 'VETERINARIO', 'CAJERO', 'GROOMING')),
    assigned_by_principal_id uuid,
    assigned_at timestamptz not null default current_timestamp,
    revoked_at timestamptz,
    revoked_by_principal_id uuid,
    unique (tenant_id, staff_principal_id, role_code)
);
create index ix_role_assignment_active on identity_auth.role_assignment (tenant_id, staff_principal_id) where revoked_at is null;

insert into identity_auth.role_assignment (id, tenant_id, staff_principal_id, role_code, assigned_at)
select gen_random_uuid(), tenant_id, id, role_code, created_at
from identity_auth.staff_account
on conflict (tenant_id, staff_principal_id, role_code) do nothing;

create table tenant_admin.saas_administrator (
    principal_id uuid primary key references identity_auth.principal(id),
    normalized_email varchar(254) not null unique,
    status varchar(32) not null check (status in ('ACTIVA', 'DESACTIVADA')),
    created_at timestamptz not null default current_timestamp
);

create table tenant_admin.tenant_registration (
    id uuid primary key,
    commercial_name varchar(160) not null,
    commercial_address varchar(240) not null,
    commercial_phone varchar(40) not null,
    commercial_email varchar(254) not null,
    normalized_commercial_email varchar(254) not null,
    initial_administrator_email varchar(254) not null,
    normalized_initial_administrator_email varchar(254) not null unique,
    ruc varchar(11),
    ruc_reserved_until timestamptz,
    status varchar(32) not null check (status in ('PENDIENTE_CORREO', 'PENDIENTE_CONTRASENA', 'ACTIVA')),
    email_verified_at timestamptz,
    tenant_id uuid unique references tenancy.tenant(id),
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp,
    constraint ck_registration_ruc_format check (ruc is null or ruc ~ '^[0-9]{11}$'),
    constraint ck_registration_ruc_reservation check ((ruc is null and ruc_reserved_until is null) or ruc is not null)
);
create index ix_tenant_registration_ruc_expiry on tenant_admin.tenant_registration (ruc, ruc_reserved_until) where ruc is not null;

create table identity_auth.one_time_token (
    id uuid primary key,
    purpose varchar(48) not null check (purpose in ('REGISTRATION_EMAIL_VERIFICATION', 'REGISTRATION_COMPLETION', 'CLIENT_EMAIL_VERIFICATION', 'STAFF_INVITATION', 'PASSWORD_RECOVERY')),
    token_hash char(64) not null unique,
    principal_id uuid references identity_auth.principal(id),
    tenant_registration_id uuid references tenant_admin.tenant_registration(id),
    tenant_id uuid references tenancy.tenant(id),
    expires_at timestamptz not null,
    consumed_at timestamptz,
    invalidated_at timestamptz,
    created_at timestamptz not null default current_timestamp,
    constraint ck_one_time_token_target check (principal_id is not null or tenant_registration_id is not null)
);
create index ix_one_time_token_target_active on identity_auth.one_time_token (purpose, principal_id, tenant_registration_id, expires_at)
    where consumed_at is null and invalidated_at is null;

create table identity_auth.registration_completion_grant (
    id uuid primary key,
    tenant_registration_id uuid not null unique references tenant_admin.tenant_registration(id),
    token_hash char(64) not null unique,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    created_at timestamptz not null default current_timestamp
);

create table tenant_admin.ruc_review (
    id uuid primary key,
    ruc varchar(11) not null check (ruc ~ '^[0-9]{11}$'),
    requester_email varchar(254) not null,
    normalized_requester_email varchar(254) not null,
    contact_channel varchar(254) not null,
    request_reason varchar(500) not null,
    status varchar(32) not null check (status in ('PENDIENTE', 'EN_REVISION', 'INFO_REQUERIDA', 'RECHAZADA', 'ACREDITADA')),
    resolution_reason varchar(500),
    resolved_by_principal_id uuid references identity_auth.principal(id),
    resolved_at timestamptz,
    affected_tenant_id uuid references tenancy.tenant(id),
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp
);
create index ix_ruc_review_status_created on tenant_admin.ruc_review (status, created_at desc);

create table tenant_admin.administrative_recovery (
    id uuid primary key,
    tenant_id uuid not null references tenancy.tenant(id),
    applicant_email varchar(254) not null,
    normalized_applicant_email varchar(254) not null,
    evidence_reference varchar(300) not null,
    status varchar(32) not null check (status in ('SOLICITADA', 'VERIFICANDO', 'INFO_REQUERIDA', 'RECHAZADA', 'APROBADA', 'EJECUTADA')),
    decision_reason varchar(500),
    reviewed_by_principal_id uuid references identity_auth.principal(id),
    reviewed_at timestamptz,
    created_at timestamptz not null default current_timestamp,
    updated_at timestamptz not null default current_timestamp
);
create index ix_administrative_recovery_tenant_status on tenant_admin.administrative_recovery (tenant_id, status, created_at desc);

create table tenant_admin.audit_entry (
    id uuid primary key,
    tenant_id uuid references tenancy.tenant(id),
    actor_principal_id uuid references identity_auth.principal(id),
    action_code varchar(80) not null,
    resource_type varchar(80) not null,
    resource_id uuid,
    outcome varchar(24) not null check (outcome in ('SUCCESS', 'DENIED', 'PENDING')),
    reason varchar(500),
    created_at timestamptz not null default current_timestamp
);
create index ix_audit_entry_tenant_created on tenant_admin.audit_entry (tenant_id, created_at desc);

create table tenant_admin.notification_outbox (
    id uuid primary key,
    purpose varchar(48) not null,
    recipient_email varchar(254) not null,
    related_resource_id uuid not null,
    status varchar(24) not null check (status in ('PENDING', 'ACCEPTED_LOCAL', 'FAILED')),
    created_at timestamptz not null default current_timestamp,
    delivered_at timestamptz,
    failure_code varchar(80)
);

alter table identity_auth.user_session
    drop constraint if exists user_session_account_type_check;
alter table identity_auth.user_session
    add column if not exists principal_id uuid,
    add column if not exists roles jsonb not null default '[]'::jsonb;
update identity_auth.user_session set principal_id = account_id where principal_id is null;
alter table identity_auth.user_session
    alter column principal_id set not null;
alter table identity_auth.user_session
    drop constraint if exists user_session_account_id_fkey;
alter table identity_auth.user_session
    add constraint fk_user_session_principal foreign key (principal_id) references identity_auth.principal(id),
    add constraint ck_user_session_account_type check (account_type in ('STAFF', 'CLIENT', 'SAAS'));
update identity_auth.user_session set roles = jsonb_build_array(role_code) where roles = '[]'::jsonb;

drop policy if exists session_lookup_or_tenant_access on identity_auth.user_session;
create policy session_lookup_or_tenant_access on identity_auth.user_session
    using (
        token_hash = current_setting('app.session_hash', true)
        or tenant_id::text = current_setting('app.tenant_id', true)
        or (tenant_id is null and account_type = 'SAAS'
            and principal_id::text = current_setting('app.principal_id', true))
    )
    with check (
        tenant_id::text = current_setting('app.tenant_id', true)
        or (tenant_id is null and account_type = 'SAAS'
            and principal_id::text = current_setting('app.principal_id', true))
    );

alter table identity_auth.account_profile enable row level security;
alter table identity_auth.role_assignment enable row level security;
create policy account_profile_tenant_isolation on identity_auth.account_profile
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy role_assignment_tenant_isolation on identity_auth.role_assignment
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));

create or replace function identity_auth.lookup_context_identity(
    p_account_type text,
    p_tenant_slug text,
    p_normalized_email text
)
returns table (
    principal_id uuid,
    account_type varchar,
    tenant_id uuid,
    tenant_slug varchar,
    tenant_commercial_name varchar,
    tenant_status varchar,
    account_status varchar,
    password_hash varchar,
    roles jsonb
)
language sql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin
as $$
    select p.id, p.account_type, a.tenant_id, t.slug, t.commercial_name, t.status,
           a.status, p.password_hash,
           coalesce(jsonb_agg(r.role_code order by r.role_code) filter (where r.revoked_at is null), '[]'::jsonb)
      from identity_auth.principal p
      join identity_auth.account_profile a on a.principal_id = p.id
      join tenancy.tenant t on t.id = a.tenant_id
      left join identity_auth.role_assignment r
        on r.staff_principal_id = p.id and r.tenant_id = a.tenant_id
     where p_account_type in ('STAFF', 'CLIENT')
       and p.account_type = p_account_type
       and t.slug = p_tenant_slug
       and a.normalized_email = p_normalized_email
     group by p.id, p.account_type, a.tenant_id, t.slug, t.commercial_name, t.status, a.status, p.password_hash
    union all
    select p.id, p.account_type, null::uuid, null::varchar, null::varchar, null::varchar,
           s.status, p.password_hash, jsonb_build_array('ADMIN_SAAS')
      from identity_auth.principal p
      join tenant_admin.saas_administrator s on s.principal_id = p.id
     where p_account_type = 'SAAS'
       and p.account_type = 'SAAS'
       and p_tenant_slug is null
       and s.normalized_email = p_normalized_email;
$$;

create or replace function identity_auth.resolve_session_context(p_token_hash text)
returns table (
    session_id uuid,
    principal_id uuid,
    tenant_id uuid,
    tenant_slug varchar,
    tenant_commercial_name varchar,
    tenant_status varchar,
    account_type varchar,
    roles jsonb,
    csrf_hash varchar,
    expires_at timestamptz
)
language sql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin
as $$
    select s.id, s.principal_id, s.tenant_id, t.slug, t.commercial_name, t.status,
           s.account_type,
           case when s.account_type = 'STAFF' then
                coalesce((select jsonb_agg(r.role_code order by r.role_code)
                            from identity_auth.role_assignment r
                           where r.tenant_id = s.tenant_id
                             and r.staff_principal_id = s.principal_id
                             and r.revoked_at is null), '[]'::jsonb)
                when s.account_type = 'SAAS' then jsonb_build_array('ADMIN_SAAS')
                else jsonb_build_array('CLIENT') end,
           s.csrf_hash, s.expires_at
      from identity_auth.user_session s
      join identity_auth.principal p on p.id = s.principal_id and p.status = 'ACTIVA'
      left join identity_auth.account_profile a on a.principal_id = p.id and a.status = 'ACTIVA'
      left join tenancy.tenant t on t.id = s.tenant_id and t.status = 'ACTIVO'
      left join tenant_admin.saas_administrator g on g.principal_id = p.id and g.status = 'ACTIVA'
     where s.token_hash = p_token_hash
       and s.revoked_at is null
       and s.expires_at > current_timestamp
       and ((s.account_type in ('STAFF', 'CLIENT') and a.tenant_id = s.tenant_id and t.id is not null)
            or (s.account_type = 'SAAS' and g.principal_id is not null));
$$;

create or replace function identity_auth.lookup_public_tenant(p_tenant_slug text)
returns table (tenant_id uuid, tenant_slug varchar, tenant_status varchar)
language sql
security definer
set search_path = pg_catalog, tenancy
as $$
    select id, slug, status
      from tenancy.tenant
     where slug = p_tenant_slug and status = 'ACTIVO';
$$;

create or replace function tenant_admin.is_ruc_available(p_ruc varchar, p_excluded_registration_id uuid)
returns boolean
language sql
security definer
set search_path = pg_catalog, tenancy, tenant_admin
as $$
    select not exists (select 1 from tenancy.tenant t where t.ruc = p_ruc)
       and not exists (
            select 1
              from tenant_admin.tenant_registration r
             where r.ruc = p_ruc
               and r.id is distinct from p_excluded_registration_id
               and r.status <> 'ACTIVA'
               and r.ruc_reserved_until > current_timestamp
       );
$$;

create or replace function tenant_admin.assert_global_session(p_session_id uuid)
returns void
language plpgsql
security definer
set search_path = pg_catalog, identity_auth, tenant_admin
as $$
begin
    if not exists (
        select 1
          from identity_auth.user_session s
          join identity_auth.principal p on p.id = s.principal_id and p.status = 'ACTIVA'
          join tenant_admin.saas_administrator g on g.principal_id = p.id and g.status = 'ACTIVA'
         where s.id = p_session_id and s.account_type = 'SAAS'
           and s.revoked_at is null and s.expires_at > current_timestamp
    ) then
        raise exception 'global session is not authorized' using errcode = '42501';
    end if;
end;
$$;

create or replace function tenant_admin.list_organizations(p_session_id uuid)
returns table (
    tenant_id uuid,
    commercial_name varchar,
    ruc varchar,
    commercial_address varchar,
    commercial_phone varchar,
    commercial_email varchar,
    registered_at timestamptz,
    status varchar
)
language plpgsql
security definer
set search_path = pg_catalog, tenancy, tenant_admin
as $$
begin
    perform tenant_admin.assert_global_session(p_session_id);
    return query
        select t.id, t.commercial_name, t.ruc, t.commercial_address, t.commercial_phone,
               t.commercial_email, t.created_at, t.status
          from tenancy.tenant t
         order by t.created_at desc;
end;
$$;

create or replace function tenant_admin.change_tenant_status(
    p_session_id uuid,
    p_tenant_id uuid,
    p_new_status varchar,
    p_reason varchar
)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin
as $$
declare
    v_actor uuid;
    v_current_status varchar;
begin
    perform tenant_admin.assert_global_session(p_session_id);
    select principal_id into v_actor from identity_auth.user_session where id = p_session_id;
    select status into v_current_status from tenancy.tenant where id = p_tenant_id for update;
    if v_current_status is null then
        return false;
    end if;
    if p_new_status not in ('ACTIVO', 'SUSPENDIDO') then
        raise exception 'invalid tenant status' using errcode = '22023';
    end if;
    if v_current_status = p_new_status then
        return true;
    end if;
    update tenancy.tenant set status = p_new_status where id = p_tenant_id;
    if p_new_status = 'SUSPENDIDO' then
        update identity_auth.user_session set revoked_at = current_timestamp
         where tenant_id = p_tenant_id and revoked_at is null;
    end if;
    insert into tenant_admin.audit_entry (id, tenant_id, actor_principal_id, action_code, resource_type, resource_id, outcome, reason)
    values (gen_random_uuid(), p_tenant_id, v_actor,
            case when p_new_status = 'SUSPENDIDO' then 'TENANT_SUSPENDED' else 'TENANT_REACTIVATED' end,
            'TENANT', p_tenant_id, 'SUCCESS', p_reason);
    return true;
end;
$$;

create or replace function tenant_admin.resolve_ruc_review(
    p_session_id uuid,
    p_review_id uuid,
    p_new_status varchar,
    p_reason varchar,
    p_release_ruc boolean
)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin
as $$
declare
    v_actor uuid;
    v_ruc varchar;
    v_tenant_id uuid;
    v_tenant_email varchar;
begin
    perform tenant_admin.assert_global_session(p_session_id);
    if p_new_status not in ('EN_REVISION', 'INFO_REQUERIDA', 'RECHAZADA', 'ACREDITADA') then
        raise exception 'invalid ruc review state' using errcode = '22023';
    end if;
    select ruc into v_ruc from tenant_admin.ruc_review where id = p_review_id for update;
    if v_ruc is null then
        return false;
    end if;
    select principal_id into v_actor from identity_auth.user_session where id = p_session_id;
    if p_release_ruc and p_new_status <> 'ACREDITADA' then
        raise exception 'ruc release requires accredited review' using errcode = '22023';
    end if;
    if p_release_ruc then
        select id, commercial_email into v_tenant_id, v_tenant_email
          from tenancy.tenant where ruc = v_ruc for update;
        if v_tenant_id is null then
            raise exception 'there is no tenant holding the ruc' using errcode = '23503';
        end if;
        update tenancy.tenant set status = 'SUSPENDIDO', ruc = null where id = v_tenant_id;
        update identity_auth.user_session set revoked_at = current_timestamp
         where tenant_id = v_tenant_id and revoked_at is null;
        insert into tenant_admin.notification_outbox (id, purpose, recipient_email, related_resource_id, status, delivered_at)
        values (gen_random_uuid(), 'RUC_EXCEPTION_NOTICE', coalesce(v_tenant_email, 'no-reply@demo.invalid'),
                p_review_id, 'ACCEPTED_LOCAL', current_timestamp);
        insert into tenant_admin.audit_entry (id, tenant_id, actor_principal_id, action_code, resource_type, resource_id, outcome, reason)
        values (gen_random_uuid(), v_tenant_id, v_actor, 'RUC_EXCEPTIONAL_RELEASE', 'RUC_REVIEW', p_review_id, 'SUCCESS', p_reason);
    end if;
    update tenant_admin.ruc_review
       set status = p_new_status,
           resolution_reason = p_reason,
           resolved_by_principal_id = v_actor,
           resolved_at = current_timestamp,
           affected_tenant_id = coalesce(v_tenant_id, affected_tenant_id),
           updated_at = current_timestamp
     where id = p_review_id;
    return true;
end;
$$;

revoke all on function identity_auth.lookup_context_identity(text, text, text) from public;
revoke all on function identity_auth.resolve_session_context(text) from public;
revoke all on function identity_auth.lookup_public_tenant(text) from public;
revoke all on function tenant_admin.is_ruc_available(varchar, uuid) from public;
revoke all on function tenant_admin.list_organizations(uuid) from public;
revoke all on function tenant_admin.change_tenant_status(uuid, uuid, varchar, varchar) from public;
revoke all on function tenant_admin.resolve_ruc_review(uuid, uuid, varchar, varchar, boolean) from public;

grant usage on schema tenant_admin to av_app;
grant select, insert, update on tenancy.tenant to av_app;
grant insert, update on identity_auth.principal to av_app;
grant select, insert, update on identity_auth.account_profile, identity_auth.role_assignment, identity_auth.one_time_token,
    identity_auth.registration_completion_grant, identity_auth.user_session to av_app;
grant select, insert, update on tenant_admin.tenant_registration, tenant_admin.ruc_review,
    tenant_admin.administrative_recovery, tenant_admin.audit_entry, tenant_admin.notification_outbox to av_app;
grant execute on function identity_auth.lookup_context_identity(text, text, text) to av_app;
grant execute on function identity_auth.resolve_session_context(text) to av_app;
grant execute on function identity_auth.lookup_public_tenant(text) to av_app;
grant execute on function tenant_admin.is_ruc_available(varchar, uuid) to av_app;
grant execute on function tenant_admin.list_organizations(uuid) to av_app;
grant execute on function tenant_admin.change_tenant_status(uuid, uuid, varchar, varchar) to av_app;
grant execute on function tenant_admin.resolve_ruc_review(uuid, uuid, varchar, varchar, boolean) to av_app;
