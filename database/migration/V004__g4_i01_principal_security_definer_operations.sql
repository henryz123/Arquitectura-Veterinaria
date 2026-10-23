-- G4-I01: bounded identity operations for the ordinary runtime role.
-- This migration is intentionally transactional.  av_app receives no direct
-- privilege over identities, credentials, opaque tokens, sessions or roles.

revoke create on schema public from public;
revoke create on schema public from av_app;
revoke create on schema identity_auth from public;
revoke create on schema identity_auth from av_app;
revoke create on schema tenant_admin from public;
revoke create on schema tenant_admin from av_app;

revoke all on table tenancy.tenant from av_app;
revoke all on table identity_auth.staff_account from av_app;
revoke all on table identity_auth.principal from av_app;
revoke all on table identity_auth.account_profile from av_app;
revoke all on table identity_auth.role_assignment from av_app;
revoke all on table identity_auth.one_time_token from av_app;
revoke all on table identity_auth.registration_completion_grant from av_app;
revoke all on table identity_auth.user_session from av_app;
revoke all on table tenant_admin.saas_administrator from av_app;
revoke all on table tenant_admin.tenant_registration from av_app;
revoke all on table tenant_admin.audit_entry from av_app;
revoke all on table tenant_admin.notification_outbox from av_app;
revoke all on table tenant_admin.ruc_review from av_app;
revoke all on table tenant_admin.administrative_recovery from av_app;

-- Authentication moves the password comparison into PostgreSQL.  The
-- application receives session context only, never a stored password hash.
revoke all on function identity_auth.lookup_context_identity(text, text, text) from av_app;

create table tenant_admin.saas_bootstrap_state (
    singleton boolean primary key default true check (singleton),
    principal_id uuid not null unique references identity_auth.principal(id),
    initialized_at timestamptz not null default current_timestamp,
    initialized_by_db_role varchar(63) not null
);

create or replace function identity_auth.require_tenant_administrator(p_session_token_hash text)
returns table (actor_principal_id uuid, tenant_id uuid)
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
begin
    return query
    select s.principal_id, s.tenant_id
      from identity_auth.user_session s
      join identity_auth.principal p on p.id = s.principal_id and p.account_type = 'STAFF' and p.status = 'ACTIVA'
      join tenancy.tenant t on t.id = s.tenant_id and t.status = 'ACTIVO'
     where s.token_hash = p_session_token_hash
       and s.account_type = 'STAFF'
       and s.revoked_at is null
       and s.expires_at > current_timestamp
       and exists (
            select 1
              from identity_auth.role_assignment r
             where r.tenant_id = s.tenant_id
               and r.staff_principal_id = s.principal_id
               and r.role_code = 'ADMIN_VETERINARIA'
               and r.revoked_at is null
       );
end;
$$;

create or replace function tenant_admin.require_global_administrator(p_session_token_hash text)
returns uuid
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_principal_id uuid;
begin
    select s.principal_id into v_principal_id
      from identity_auth.user_session s
      join identity_auth.principal p on p.id = s.principal_id and p.account_type = 'SAAS' and p.status = 'ACTIVA'
      join tenant_admin.saas_administrator g on g.principal_id = p.id and g.status = 'ACTIVA'
     where s.token_hash = p_session_token_hash
       and s.account_type = 'SAAS'
       and s.tenant_id is null
       and s.revoked_at is null
       and s.expires_at > current_timestamp;
    if v_principal_id is null then
        raise exception 'global session is not authorized' using errcode = '42501';
    end if;
    return v_principal_id;
end;
$$;

create or replace function identity_auth.authenticate_and_create_session(
    p_account_type varchar,
    p_tenant_slug varchar,
    p_normalized_email varchar,
    p_password text,
    p_token_hash text,
    p_csrf_hash text,
    p_expires_at timestamptz
)
returns table (
    session_id uuid,
    principal_id uuid,
    tenant_id uuid,
    tenant_slug varchar,
    tenant_commercial_name varchar,
    tenant_status varchar,
    account_type varchar,
    roles jsonb,
    expires_at timestamptz
)
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_principal_id uuid;
    v_tenant_id uuid;
    v_tenant_slug varchar;
    v_tenant_name varchar;
    v_tenant_status varchar;
    v_account_status varchar;
    v_password_hash varchar;
    v_roles jsonb := '[]'::jsonb;
    v_session_id uuid := gen_random_uuid();
begin
    if p_account_type not in ('STAFF', 'CLIENT', 'SAAS')
       or p_normalized_email is null
       or p_password is null
       or p_token_hash !~ '^[0-9a-f]{64}$'
       or p_csrf_hash !~ '^[0-9a-f]{64}$'
       or p_expires_at <= current_timestamp or p_expires_at > current_timestamp + interval '8 hours' then
        return;
    end if;

    if p_account_type = 'SAAS' then
        if p_tenant_slug is not null then
            return;
        end if;
        select p.id, p.status, p.password_hash
          into v_principal_id, v_account_status, v_password_hash
          from identity_auth.principal p
          join tenant_admin.saas_administrator g on g.principal_id = p.id and g.status = 'ACTIVA'
         where p.account_type = 'SAAS' and g.normalized_email = p_normalized_email;
        v_roles := jsonb_build_array('ADMIN_SAAS');
    else
        select p.id, a.tenant_id, t.slug, t.commercial_name, t.status, a.status, p.password_hash,
               case when p_account_type = 'STAFF' then coalesce(jsonb_agg(r.role_code order by r.role_code)
                    filter (where r.revoked_at is null), '[]'::jsonb) else jsonb_build_array('CLIENT') end
          into v_principal_id, v_tenant_id, v_tenant_slug, v_tenant_name, v_tenant_status, v_account_status, v_password_hash, v_roles
          from identity_auth.principal p
          join identity_auth.account_profile a on a.principal_id = p.id
          join tenancy.tenant t on t.id = a.tenant_id
          left join identity_auth.role_assignment r on r.tenant_id = a.tenant_id and r.staff_principal_id = p.id
         where p.account_type = p_account_type
           and a.account_type = p_account_type
           and t.slug = p_tenant_slug
           and a.normalized_email = p_normalized_email
         group by p.id, a.tenant_id, t.slug, t.commercial_name, t.status, a.status, p.password_hash;
    end if;

    if v_principal_id is null
       or v_account_status <> 'ACTIVA'
       or (p_account_type <> 'SAAS' and v_tenant_status <> 'ACTIVO')
       or v_password_hash is null
       or public.crypt(p_password, v_password_hash) <> v_password_hash then
        return;
    end if;

    insert into identity_auth.user_session
        (id, account_id, principal_id, tenant_id, account_type, role_code, roles, token_hash, csrf_hash, expires_at)
    values
        (v_session_id, v_principal_id, v_principal_id, v_tenant_id, p_account_type,
         coalesce(v_roles ->> 0, p_account_type), v_roles, p_token_hash, p_csrf_hash, p_expires_at);

    return query select v_session_id, v_principal_id, v_tenant_id, v_tenant_slug, v_tenant_name,
                        v_tenant_status, p_account_type, v_roles, p_expires_at;
end;
$$;

create or replace function identity_auth.revoke_session_by_token(p_session_token_hash text)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
begin
    update identity_auth.user_session
       set revoked_at = current_timestamp
     where token_hash = p_session_token_hash
       and revoked_at is null;
    return found;
end;
$$;

create or replace function tenant_admin.start_tenant_registration(
    p_commercial_name varchar,
    p_commercial_address varchar,
    p_commercial_phone varchar,
    p_commercial_email varchar,
    p_normalized_commercial_email varchar,
    p_initial_administrator_email varchar,
    p_normalized_initial_administrator_email varchar,
    p_ruc varchar,
    p_reserved_until timestamptz,
    p_verification_token_hash text,
    p_token_expires_at timestamptz
)
returns table (registration_id uuid, registration_status varchar, ruc_reserved_until timestamptz)
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_registration_id uuid := gen_random_uuid();
begin
    if p_verification_token_hash !~ '^[0-9a-f]{64}$' or p_token_expires_at <= current_timestamp
       or p_token_expires_at > current_timestamp + interval '24 hours'
       or (p_ruc is not null and (p_reserved_until is null or p_reserved_until <= current_timestamp
           or p_reserved_until > current_timestamp + interval '24 hours'))
       or p_initial_administrator_email is null or p_normalized_initial_administrator_email is null then
        raise exception 'invalid registration request' using errcode = '22023';
    end if;
    if p_ruc is not null then
        perform pg_advisory_xact_lock(hashtextextended(p_ruc, 0));
        if exists (select 1 from tenancy.tenant t where t.ruc = p_ruc)
           or exists (select 1 from tenant_admin.tenant_registration r where r.ruc = p_ruc and r.status <> 'ACTIVA'
                        and r.ruc_reserved_until > current_timestamp) then
            raise exception 'ruc is not available' using errcode = '23505';
        end if;
    end if;
    insert into tenant_admin.tenant_registration
        (id, commercial_name, commercial_address, commercial_phone, commercial_email, normalized_commercial_email,
         initial_administrator_email, normalized_initial_administrator_email, ruc, ruc_reserved_until, status)
    values
        (v_registration_id, p_commercial_name, p_commercial_address, p_commercial_phone, p_commercial_email,
         p_normalized_commercial_email, p_initial_administrator_email, p_normalized_initial_administrator_email,
         p_ruc, case when p_ruc is null then null else p_reserved_until end, 'PENDIENTE_CORREO');
    insert into identity_auth.one_time_token
        (id, purpose, token_hash, tenant_registration_id, expires_at)
    values (gen_random_uuid(), 'REGISTRATION_EMAIL_VERIFICATION', p_verification_token_hash, v_registration_id, p_token_expires_at);
    insert into tenant_admin.notification_outbox (id, purpose, recipient_email, related_resource_id, status, delivered_at)
    values (gen_random_uuid(), 'REGISTRATION_EMAIL_VERIFICATION', p_initial_administrator_email, v_registration_id, 'ACCEPTED_LOCAL', current_timestamp);
    return query select v_registration_id, 'PENDIENTE_CORREO'::varchar,
                        case when p_ruc is null then null else p_reserved_until end;
end;
$$;

create or replace function tenant_admin.resume_tenant_registration(
    p_registration_id uuid,
    p_normalized_initial_administrator_email varchar,
    p_token_hash text,
    p_expires_at timestamptz
)
returns table (registration_id uuid, registration_status varchar, ruc_reserved_until timestamptz, link_purpose varchar)
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_registration tenant_admin.tenant_registration%rowtype;
    v_purpose varchar;
begin
    if p_token_hash !~ '^[0-9a-f]{64}$' or p_expires_at <= current_timestamp
       or p_expires_at > current_timestamp + interval '24 hours' then
        raise exception 'registration resumption is not valid' using errcode = '22023';
    end if;
    select * into v_registration
      from tenant_admin.tenant_registration
     where id = p_registration_id
       and normalized_initial_administrator_email = p_normalized_initial_administrator_email
     for update;
    if v_registration.id is null or v_registration.status = 'ACTIVA' then
        raise exception 'registration resumption is not valid' using errcode = '22023';
    end if;
    if v_registration.ruc is not null and (v_registration.ruc_reserved_until is null or v_registration.ruc_reserved_until <= current_timestamp) then
        perform pg_advisory_xact_lock(hashtextextended(v_registration.ruc, 0));
        if exists (select 1 from tenancy.tenant t where t.ruc = v_registration.ruc)
           or exists (select 1 from tenant_admin.tenant_registration r where r.ruc = v_registration.ruc
                       and r.id <> v_registration.id and r.status <> 'ACTIVA' and r.ruc_reserved_until > current_timestamp) then
            raise exception 'ruc is not available' using errcode = '23505';
        end if;
        update tenant_admin.tenant_registration
           set ruc_reserved_until = p_expires_at, updated_at = current_timestamp
         where id = v_registration.id;
        v_registration.ruc_reserved_until := p_expires_at;
    end if;
    v_purpose := case when v_registration.status = 'PENDIENTE_CORREO'
                      then 'REGISTRATION_EMAIL_VERIFICATION' else 'REGISTRATION_COMPLETION' end;
    update identity_auth.one_time_token
       set invalidated_at = current_timestamp
     where tenant_registration_id = v_registration.id
       and purpose = v_purpose and consumed_at is null and invalidated_at is null;
    insert into identity_auth.one_time_token (id, purpose, token_hash, tenant_registration_id, expires_at)
    values (gen_random_uuid(), v_purpose, p_token_hash, v_registration.id, p_expires_at);
    insert into tenant_admin.notification_outbox (id, purpose, recipient_email, related_resource_id, status, delivered_at)
    values (gen_random_uuid(), v_purpose, v_registration.initial_administrator_email, v_registration.id, 'ACCEPTED_LOCAL', current_timestamp);
    return query select v_registration.id, v_registration.status, v_registration.ruc_reserved_until, v_purpose;
end;
$$;

create or replace function tenant_admin.verify_tenant_registration(
    p_verification_token_hash text,
    p_completion_grant_hash text,
    p_completion_expires_at timestamptz
)
returns table (registration_id uuid, registration_status varchar)
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_registration_id uuid;
begin
    if p_verification_token_hash !~ '^[0-9a-f]{64}$' or p_completion_grant_hash !~ '^[0-9a-f]{64}$'
       or p_completion_expires_at <= current_timestamp or p_completion_expires_at > current_timestamp + interval '15 minutes' then
        raise exception 'invalid verification link' using errcode = '22023';
    end if;
    update identity_auth.one_time_token tok
       set consumed_at = current_timestamp
     where tok.token_hash = p_verification_token_hash
       and tok.purpose = 'REGISTRATION_EMAIL_VERIFICATION'
       and tok.consumed_at is null and tok.invalidated_at is null and tok.expires_at > current_timestamp
     returning tok.tenant_registration_id into v_registration_id;
    if v_registration_id is null then
        raise exception 'verification link is not valid' using errcode = '22023';
    end if;
    update tenant_admin.tenant_registration
       set status = 'PENDIENTE_CONTRASENA', email_verified_at = current_timestamp, updated_at = current_timestamp
     where id = v_registration_id and status = 'PENDIENTE_CORREO';
    if not found then
        raise exception 'registration is not eligible' using errcode = '22023';
    end if;
    insert into identity_auth.registration_completion_grant (id, tenant_registration_id, token_hash, expires_at)
    values (gen_random_uuid(), v_registration_id, p_completion_grant_hash, p_completion_expires_at)
    on conflict (tenant_registration_id) do update set token_hash = excluded.token_hash, expires_at = excluded.expires_at, consumed_at = null;
    return query select v_registration_id, 'PENDIENTE_CONTRASENA'::varchar;
end;
$$;

create or replace function tenant_admin.complete_tenant_registration(
    p_completion_grant_hash text,
    p_password_hash varchar,
    p_tenant_slug varchar
)
returns table (tenant_id uuid, tenant_slug varchar, commercial_name varchar)
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_registration tenant_admin.tenant_registration%rowtype;
    v_tenant_id uuid := gen_random_uuid();
    v_principal_id uuid := gen_random_uuid();
begin
    if p_completion_grant_hash !~ '^[0-9a-f]{64}$' or p_password_hash is null
       or p_tenant_slug !~ '^[a-z0-9]+(?:-[a-z0-9]+)*$' then
        raise exception 'invalid completion grant' using errcode = '22023';
    end if;
    select r.* into v_registration
      from identity_auth.registration_completion_grant g
      join tenant_admin.tenant_registration r on r.id = g.tenant_registration_id
     where g.token_hash = p_completion_grant_hash and g.consumed_at is null and g.expires_at > current_timestamp
     for update of g, r;
    if v_registration.id is null or v_registration.status <> 'PENDIENTE_CONTRASENA' then
        raise exception 'completion grant is not valid' using errcode = '22023';
    end if;
    if v_registration.ruc is not null then
        perform pg_advisory_xact_lock(hashtextextended(v_registration.ruc, 0));
        if v_registration.ruc_reserved_until is null or v_registration.ruc_reserved_until <= current_timestamp
           or exists (select 1 from tenancy.tenant t where t.ruc = v_registration.ruc) then
            raise exception 'ruc reservation is not available' using errcode = '23505';
        end if;
    end if;
    insert into tenancy.tenant (id, slug, commercial_name, status, ruc, commercial_address, commercial_phone, commercial_email)
    values (v_tenant_id, p_tenant_slug, v_registration.commercial_name, 'ACTIVO', v_registration.ruc,
            v_registration.commercial_address, v_registration.commercial_phone, v_registration.commercial_email);
    insert into identity_auth.principal (id, account_type, password_hash, status, email_verified_at, password_changed_at)
    values (v_principal_id, 'STAFF', p_password_hash, 'ACTIVA', current_timestamp, current_timestamp);
    insert into identity_auth.account_profile (principal_id, tenant_id, account_type, normalized_email, status)
    values (v_principal_id, v_tenant_id, 'STAFF', v_registration.normalized_initial_administrator_email, 'ACTIVA');
    insert into identity_auth.role_assignment (id, tenant_id, staff_principal_id, role_code, assigned_by_principal_id)
    values (gen_random_uuid(), v_tenant_id, v_principal_id, 'ADMIN_VETERINARIA', v_principal_id);
    update identity_auth.registration_completion_grant set consumed_at = current_timestamp where token_hash = p_completion_grant_hash;
    update tenant_admin.tenant_registration set status = 'ACTIVA', tenant_id = v_tenant_id, updated_at = current_timestamp where id = v_registration.id;
    insert into tenant_admin.audit_entry (id, tenant_id, actor_principal_id, action_code, resource_type, resource_id, outcome)
    values (gen_random_uuid(), v_tenant_id, v_principal_id, 'TENANT_REGISTERED', 'TENANT', v_tenant_id, 'SUCCESS');
    return query select v_tenant_id, p_tenant_slug, v_registration.commercial_name;
end;
$$;

create or replace function identity_auth.register_client(
    p_tenant_slug varchar,
    p_normalized_email varchar,
    p_password_hash varchar,
    p_verification_token_hash text,
    p_token_expires_at timestamptz
)
returns uuid
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_tenant_id uuid;
    v_principal_id uuid := gen_random_uuid();
begin
    if p_verification_token_hash !~ '^[0-9a-f]{64}$' or p_password_hash is null or p_token_expires_at <= current_timestamp
       or p_token_expires_at > current_timestamp + interval '24 hours' then
        raise exception 'invalid client registration' using errcode = '22023';
    end if;
    select id into v_tenant_id from tenancy.tenant where slug = p_tenant_slug and status = 'ACTIVO';
    if v_tenant_id is null then
        raise exception 'tenant is not available' using errcode = '22023';
    end if;
    if exists (select 1 from identity_auth.account_profile where tenant_id = v_tenant_id and account_type = 'CLIENT'
               and normalized_email = p_normalized_email) then
        raise exception 'client account is not available' using errcode = '23505';
    end if;
    insert into identity_auth.principal (id, account_type, password_hash, status, password_changed_at)
    values (v_principal_id, 'CLIENT', p_password_hash, 'PENDIENTE_VERIFICACION', current_timestamp);
    insert into identity_auth.account_profile (principal_id, tenant_id, account_type, normalized_email, status)
    values (v_principal_id, v_tenant_id, 'CLIENT', p_normalized_email, 'PENDIENTE_VERIFICACION');
    insert into identity_auth.one_time_token (id, purpose, token_hash, principal_id, tenant_id, expires_at)
    values (gen_random_uuid(), 'CLIENT_EMAIL_VERIFICATION', p_verification_token_hash, v_principal_id, v_tenant_id, p_token_expires_at);
    insert into tenant_admin.audit_entry (id, tenant_id, actor_principal_id, action_code, resource_type, resource_id, outcome)
    values (gen_random_uuid(), v_tenant_id, v_principal_id, 'CLIENT_REGISTERED', 'CLIENT_ACCOUNT', v_principal_id, 'PENDING');
    return v_principal_id;
end;
$$;

create or replace function identity_auth.verify_client_email(p_verification_token_hash text)
returns uuid
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_principal_id uuid;
    v_tenant_id uuid;
begin
    update identity_auth.one_time_token tok
       set consumed_at = current_timestamp
     where tok.token_hash = p_verification_token_hash and tok.purpose = 'CLIENT_EMAIL_VERIFICATION'
       and tok.consumed_at is null and tok.invalidated_at is null and tok.expires_at > current_timestamp
     returning tok.principal_id, tok.tenant_id into v_principal_id, v_tenant_id;
    if v_principal_id is null then
        raise exception 'verification link is not valid' using errcode = '22023';
    end if;
    update identity_auth.principal set status = 'ACTIVA', email_verified_at = current_timestamp, updated_at = current_timestamp
     where id = v_principal_id and account_type = 'CLIENT' and status = 'PENDIENTE_VERIFICACION';
    update identity_auth.account_profile set status = 'ACTIVA', updated_at = current_timestamp
     where principal_id = v_principal_id and tenant_id = v_tenant_id and account_type = 'CLIENT' and status = 'PENDIENTE_VERIFICACION';
    if not found then
        raise exception 'client account is not eligible' using errcode = '22023';
    end if;
    insert into tenant_admin.audit_entry (id, tenant_id, actor_principal_id, action_code, resource_type, resource_id, outcome)
    values (gen_random_uuid(), v_tenant_id, v_principal_id, 'CLIENT_EMAIL_VERIFIED', 'CLIENT_ACCOUNT', v_principal_id, 'SUCCESS');
    return v_principal_id;
end;
$$;

create or replace function identity_auth.resend_client_email_verification(
    p_tenant_slug varchar,
    p_normalized_email varchar,
    p_verification_token_hash text,
    p_token_expires_at timestamptz
)
returns uuid
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_principal_id uuid;
    v_tenant_id uuid;
begin
    if p_verification_token_hash !~ '^[0-9a-f]{64}$' or p_token_expires_at <= current_timestamp
       or p_token_expires_at > current_timestamp + interval '24 hours' then
        return null;
    end if;
    select a.principal_id, a.tenant_id into v_principal_id, v_tenant_id
      from identity_auth.account_profile a
      join tenancy.tenant t on t.id = a.tenant_id and t.status = 'ACTIVO'
     where t.slug = p_tenant_slug and a.account_type = 'CLIENT' and a.status = 'PENDIENTE_VERIFICACION'
       and a.normalized_email = p_normalized_email;
    if v_principal_id is null then
        return null;
    end if;
    update identity_auth.one_time_token set invalidated_at = current_timestamp
     where purpose = 'CLIENT_EMAIL_VERIFICATION' and principal_id = v_principal_id and consumed_at is null and invalidated_at is null;
    insert into identity_auth.one_time_token (id, purpose, token_hash, principal_id, tenant_id, expires_at)
    values (gen_random_uuid(), 'CLIENT_EMAIL_VERIFICATION', p_verification_token_hash, v_principal_id, v_tenant_id, p_token_expires_at);
    insert into tenant_admin.notification_outbox (id, purpose, recipient_email, related_resource_id, status, delivered_at)
    values (gen_random_uuid(), 'CLIENT_EMAIL_VERIFICATION', p_normalized_email, v_principal_id, 'ACCEPTED_LOCAL', current_timestamp);
    return v_principal_id;
end;
$$;

create or replace function identity_auth.verify_email_link(
    p_verification_token_hash text,
    p_completion_grant_hash text,
    p_completion_expires_at timestamptz
)
returns table (verification_status varchar, verified_account_type varchar, resource_id uuid)
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_purpose varchar;
    v_registration_id uuid;
    v_principal_id uuid;
    v_tenant_id uuid;
begin
    select purpose, tenant_registration_id, principal_id, tenant_id
      into v_purpose, v_registration_id, v_principal_id, v_tenant_id
      from identity_auth.one_time_token
     where token_hash = p_verification_token_hash and consumed_at is null and invalidated_at is null and expires_at > current_timestamp
     for update;
    if v_purpose in ('REGISTRATION_EMAIL_VERIFICATION', 'REGISTRATION_COMPLETION') then
        if p_completion_grant_hash !~ '^[0-9a-f]{64}$' or p_completion_expires_at <= current_timestamp
           or p_completion_expires_at > current_timestamp + interval '15 minutes' then
            raise exception 'verification link is not valid' using errcode = '22023';
        end if;
        update identity_auth.one_time_token set consumed_at = current_timestamp where token_hash = p_verification_token_hash;
        update tenant_admin.tenant_registration set status = 'PENDIENTE_CONTRASENA',
               email_verified_at = coalesce(email_verified_at, current_timestamp), updated_at = current_timestamp
         where id = v_registration_id
           and (status = 'PENDIENTE_CORREO' or (v_purpose = 'REGISTRATION_COMPLETION' and status = 'PENDIENTE_CONTRASENA'));
        if not found then
            raise exception 'registration is not eligible' using errcode = '22023';
        end if;
        insert into identity_auth.registration_completion_grant (id, tenant_registration_id, token_hash, expires_at)
        values (gen_random_uuid(), v_registration_id, p_completion_grant_hash, p_completion_expires_at)
        on conflict (tenant_registration_id) do update set token_hash = excluded.token_hash, expires_at = excluded.expires_at, consumed_at = null;
        return query select 'PENDIENTE_CONTRASENA'::varchar, 'STAFF'::varchar, v_registration_id;
        return;
    end if;
    if v_purpose = 'CLIENT_EMAIL_VERIFICATION' then
        update identity_auth.one_time_token set consumed_at = current_timestamp where token_hash = p_verification_token_hash;
        update identity_auth.principal set status = 'ACTIVA', email_verified_at = current_timestamp, updated_at = current_timestamp
         where id = v_principal_id and account_type = 'CLIENT' and status = 'PENDIENTE_VERIFICACION';
        update identity_auth.account_profile set status = 'ACTIVA', updated_at = current_timestamp
         where principal_id = v_principal_id and tenant_id = v_tenant_id and account_type = 'CLIENT' and status = 'PENDIENTE_VERIFICACION';
        if not found then
            raise exception 'client account is not eligible' using errcode = '22023';
        end if;
        insert into tenant_admin.audit_entry (id, tenant_id, actor_principal_id, action_code, resource_type, resource_id, outcome)
        values (gen_random_uuid(), v_tenant_id, v_principal_id, 'CLIENT_EMAIL_VERIFIED', 'CLIENT_ACCOUNT', v_principal_id, 'SUCCESS');
        return query select 'ACTIVA'::varchar, 'CLIENT'::varchar, v_principal_id;
        return;
    end if;
    raise exception 'verification link is not valid' using errcode = '22023';
end;
$$;

create or replace function identity_auth.request_password_recovery(
    p_account_type varchar,
    p_tenant_slug varchar,
    p_normalized_email varchar,
    p_recovery_token_hash text,
    p_token_expires_at timestamptz
)
returns table (principal_id uuid, tenant_id uuid)
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_principal_id uuid;
    v_tenant_id uuid;
begin
    if p_recovery_token_hash !~ '^[0-9a-f]{64}$' or p_token_expires_at <= current_timestamp
       or p_token_expires_at > current_timestamp + interval '30 minutes' then
        return;
    end if;
    if p_account_type = 'SAAS' and p_tenant_slug is null then
        select p.id, null::uuid into v_principal_id, v_tenant_id
          from identity_auth.principal p join tenant_admin.saas_administrator g on g.principal_id = p.id
         where p.account_type = 'SAAS' and p.status = 'ACTIVA' and g.status = 'ACTIVA' and g.normalized_email = p_normalized_email;
    elsif p_account_type in ('STAFF', 'CLIENT') then
        select p.id, a.tenant_id into v_principal_id, v_tenant_id
          from identity_auth.principal p join identity_auth.account_profile a on a.principal_id = p.id
          join tenancy.tenant t on t.id = a.tenant_id and t.status = 'ACTIVO'
         where p.account_type = p_account_type and p.status = 'ACTIVA' and a.account_type = p_account_type
           and a.status = 'ACTIVA' and t.slug = p_tenant_slug and a.normalized_email = p_normalized_email;
    else
        return;
    end if;
    if v_principal_id is not null then
        update identity_auth.one_time_token set invalidated_at = current_timestamp
         where purpose = 'PASSWORD_RECOVERY' and principal_id = v_principal_id and consumed_at is null and invalidated_at is null;
        insert into identity_auth.one_time_token (id, purpose, token_hash, principal_id, tenant_id, expires_at)
        values (gen_random_uuid(), 'PASSWORD_RECOVERY', p_recovery_token_hash, v_principal_id, v_tenant_id, p_token_expires_at);

        insert into tenant_admin.notification_outbox (id, purpose, recipient_email, related_resource_id, status, delivered_at)
        values (gen_random_uuid(), 'PASSWORD_RECOVERY', p_normalized_email, v_principal_id, 'ACCEPTED_LOCAL', current_timestamp);
        return query select v_principal_id, v_tenant_id;
    end if;
end;
$$;

create or replace function identity_auth.reset_password_with_recovery(p_recovery_token_hash text, p_password_hash varchar)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_principal_id uuid;
    v_tenant_id uuid;
begin
    if p_recovery_token_hash !~ '^[0-9a-f]{64}$' or p_password_hash is null then
        raise exception 'invalid recovery link' using errcode = '22023';
    end if;
    update identity_auth.one_time_token tok set consumed_at = current_timestamp
     where tok.token_hash = p_recovery_token_hash and tok.purpose = 'PASSWORD_RECOVERY'
       and tok.consumed_at is null and tok.invalidated_at is null and tok.expires_at > current_timestamp
     returning tok.principal_id, tok.tenant_id into v_principal_id, v_tenant_id;
    if v_principal_id is null then
        raise exception 'recovery link is not valid' using errcode = '22023';
    end if;
    update identity_auth.principal set password_hash = p_password_hash, status = 'ACTIVA', password_changed_at = current_timestamp,
           email_verified_at = coalesce(email_verified_at, current_timestamp), updated_at = current_timestamp
     where id = v_principal_id;
    update identity_auth.user_session set revoked_at = current_timestamp where principal_id = v_principal_id and revoked_at is null;
    if v_tenant_id is not null then
        insert into tenant_admin.audit_entry (id, tenant_id, actor_principal_id, action_code, resource_type, resource_id, outcome)
        values (gen_random_uuid(), v_tenant_id, v_principal_id, 'PASSWORD_RESET', 'ACCOUNT', v_principal_id, 'SUCCESS');
    end if;
    return true;
end;
$$;

create or replace function identity_auth.invite_staff(
    p_session_token_hash text,
    p_normalized_email varchar,
    p_roles jsonb,
    p_invitation_token_hash text,
    p_token_expires_at timestamptz
)
returns table (principal_id uuid, roles jsonb)
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_actor uuid;
    v_tenant_id uuid;
    v_principal_id uuid;
    v_existing_status varchar;
begin
    select actor_principal_id, tenant_id into v_actor, v_tenant_id from identity_auth.require_tenant_administrator(p_session_token_hash);
    if v_actor is null or p_invitation_token_hash !~ '^[0-9a-f]{64}$' or p_token_expires_at <= current_timestamp
       or p_token_expires_at > current_timestamp + interval '24 hours'
       or jsonb_typeof(p_roles) <> 'array' or jsonb_array_length(p_roles) = 0
       or exists (select 1 from jsonb_array_elements_text(p_roles) role where value not in ('ADMIN_VETERINARIA','RECEPCION','VETERINARIO','CAJERO','GROOMING')) then
        raise exception 'staff invitation is not authorized' using errcode = '42501';
    end if;
    select principal_id, status into v_principal_id, v_existing_status from identity_auth.account_profile
     where tenant_id = v_tenant_id and account_type = 'STAFF' and normalized_email = p_normalized_email for update;
    if v_principal_id is null then
        v_principal_id := gen_random_uuid();
        insert into identity_auth.principal (id, account_type, status) values (v_principal_id, 'STAFF', 'INVITADA');
        insert into identity_auth.account_profile (principal_id, tenant_id, account_type, normalized_email, status)
        values (v_principal_id, v_tenant_id, 'STAFF', p_normalized_email, 'INVITADA');
    elsif v_existing_status <> 'INVITADA' then
        raise exception 'staff account is not available' using errcode = '23505';
    end if;
    update identity_auth.role_assignment set revoked_at = current_timestamp, revoked_by_principal_id = v_actor
     where tenant_id = v_tenant_id and staff_principal_id = v_principal_id and revoked_at is null;
    insert into identity_auth.role_assignment (id, tenant_id, staff_principal_id, role_code, assigned_by_principal_id)
    select gen_random_uuid(), v_tenant_id, v_principal_id, role, v_actor
      from (select distinct value as role from jsonb_array_elements_text(p_roles)) requested;
    update identity_auth.one_time_token set invalidated_at = current_timestamp
     where purpose = 'STAFF_INVITATION' and principal_id = v_principal_id and consumed_at is null and invalidated_at is null;
    insert into identity_auth.one_time_token (id, purpose, token_hash, principal_id, tenant_id, expires_at)
    values (gen_random_uuid(), 'STAFF_INVITATION', p_invitation_token_hash, v_principal_id, v_tenant_id, p_token_expires_at);
    insert into tenant_admin.notification_outbox (id, purpose, recipient_email, related_resource_id, status, delivered_at)
    values (gen_random_uuid(), 'STAFF_INVITATION', p_normalized_email, v_principal_id, 'ACCEPTED_LOCAL', current_timestamp);
    insert into tenant_admin.audit_entry (id, tenant_id, actor_principal_id, action_code, resource_type, resource_id, outcome)
    values (gen_random_uuid(), v_tenant_id, v_actor, 'STAFF_INVITED', 'STAFF_ACCOUNT', v_principal_id, 'SUCCESS');
    return query select v_principal_id, (select coalesce(jsonb_agg(role order by role), '[]'::jsonb)
                                          from (select distinct value as role from jsonb_array_elements_text(p_roles)) requested);
end;
$$;

create or replace function identity_auth.accept_staff_invitation(p_invitation_token_hash text, p_password_hash varchar)
returns uuid
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_principal_id uuid;
    v_tenant_id uuid;
begin
    update identity_auth.one_time_token tok set consumed_at = current_timestamp
     where tok.token_hash = p_invitation_token_hash and tok.purpose = 'STAFF_INVITATION'
       and tok.consumed_at is null and tok.invalidated_at is null and tok.expires_at > current_timestamp
     returning tok.principal_id, tok.tenant_id into v_principal_id, v_tenant_id;
    if v_principal_id is null or p_password_hash is null then
        raise exception 'invitation link is not valid' using errcode = '22023';
    end if;
    update identity_auth.principal set password_hash = p_password_hash, status = 'ACTIVA', email_verified_at = current_timestamp,
           password_changed_at = current_timestamp, updated_at = current_timestamp
     where id = v_principal_id and account_type = 'STAFF' and status = 'INVITADA';
    update identity_auth.account_profile set status = 'ACTIVA', updated_at = current_timestamp
     where principal_id = v_principal_id and tenant_id = v_tenant_id and account_type = 'STAFF' and status = 'INVITADA';
    if not found then
        raise exception 'staff account is not eligible' using errcode = '22023';
    end if;
    insert into tenant_admin.audit_entry (id, tenant_id, actor_principal_id, action_code, resource_type, resource_id, outcome)
    values (gen_random_uuid(), v_tenant_id, v_principal_id, 'STAFF_INVITATION_ACCEPTED', 'STAFF_ACCOUNT', v_principal_id, 'SUCCESS');
    return v_principal_id;
end;
$$;

create or replace function identity_auth.list_staff_for_session(p_session_token_hash text)
returns table (principal_id uuid, normalized_email varchar, account_status varchar, roles jsonb)
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_actor uuid;
    v_tenant_id uuid;
begin
    select actor_principal_id, tenant_id into v_actor, v_tenant_id from identity_auth.require_tenant_administrator(p_session_token_hash);
    if v_actor is null then
        raise exception 'staff administration is not authorized' using errcode = '42501';
    end if;
    return query
    select a.principal_id, a.normalized_email, a.status,
           coalesce(jsonb_agg(r.role_code order by r.role_code) filter (where r.revoked_at is null), '[]'::jsonb)
      from identity_auth.account_profile a
      left join identity_auth.role_assignment r on r.tenant_id = a.tenant_id and r.staff_principal_id = a.principal_id
     where a.tenant_id = v_tenant_id and a.account_type = 'STAFF'
     group by a.principal_id, a.normalized_email, a.status
     order by a.normalized_email;
end;
$$;

create or replace function identity_auth.replace_staff_roles(
    p_session_token_hash text,
    p_target_principal_id uuid,
    p_roles jsonb
)
returns table (normalized_email varchar, account_status varchar, roles jsonb)
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_actor uuid;
    v_tenant_id uuid;
    v_email varchar;
    v_status varchar;
    v_current_roles jsonb;
begin
    select actor_principal_id, tenant_id into v_actor, v_tenant_id from identity_auth.require_tenant_administrator(p_session_token_hash);
    if v_actor is null or jsonb_typeof(p_roles) <> 'array' or jsonb_array_length(p_roles) = 0
       or exists (select 1 from jsonb_array_elements_text(p_roles) role where value not in ('ADMIN_VETERINARIA','RECEPCION','VETERINARIO','CAJERO','GROOMING')) then
        raise exception 'staff role change is not authorized' using errcode = '42501';
    end if;
    select normalized_email, status into v_email, v_status from identity_auth.account_profile
     where principal_id = p_target_principal_id and tenant_id = v_tenant_id and account_type = 'STAFF' for update;
    if v_email is null then
        raise exception 'staff account is not available' using errcode = '22023';
    end if;
    perform 1 from identity_auth.account_profile a join identity_auth.role_assignment r on r.staff_principal_id = a.principal_id
     where a.tenant_id = v_tenant_id and a.account_type = 'STAFF' and a.status = 'ACTIVA'
       and r.role_code = 'ADMIN_VETERINARIA' and r.revoked_at is null for update;
    select coalesce(jsonb_agg(r.role_code), '[]'::jsonb) into v_current_roles from identity_auth.role_assignment r
     where r.tenant_id = v_tenant_id and r.staff_principal_id = p_target_principal_id and r.revoked_at is null;
    if v_status = 'ACTIVA' and v_current_roles ? 'ADMIN_VETERINARIA' and not (p_roles ? 'ADMIN_VETERINARIA')
       and (select count(*) from identity_auth.account_profile a join identity_auth.role_assignment r on r.staff_principal_id = a.principal_id
            where a.tenant_id = v_tenant_id and a.account_type = 'STAFF' and a.status = 'ACTIVA'
              and r.role_code = 'ADMIN_VETERINARIA' and r.revoked_at is null) <= 1 then
        raise exception 'last tenant administrator must remain active' using errcode = '23514';
    end if;
    update identity_auth.role_assignment set revoked_at = current_timestamp, revoked_by_principal_id = v_actor
     where tenant_id = v_tenant_id and staff_principal_id = p_target_principal_id and revoked_at is null;
    insert into identity_auth.role_assignment (id, tenant_id, staff_principal_id, role_code, assigned_by_principal_id)
    select gen_random_uuid(), v_tenant_id, p_target_principal_id, role, v_actor
      from (select distinct value as role from jsonb_array_elements_text(p_roles)) requested;
    update identity_auth.user_session set revoked_at = current_timestamp
     where principal_id = p_target_principal_id and tenant_id = v_tenant_id and revoked_at is null;
    insert into tenant_admin.audit_entry (id, tenant_id, actor_principal_id, action_code, resource_type, resource_id, outcome)
    values (gen_random_uuid(), v_tenant_id, v_actor, 'STAFF_ROLES_REPLACED', 'STAFF_ACCOUNT', p_target_principal_id, 'SUCCESS');
    return query select v_email, v_status, (select coalesce(jsonb_agg(role order by role), '[]'::jsonb)
                                              from (select distinct value as role from jsonb_array_elements_text(p_roles)) requested);
end;
$$;

create or replace function identity_auth.deactivate_staff(p_session_token_hash text, p_target_principal_id uuid)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_actor uuid;
    v_tenant_id uuid;
    v_is_admin boolean;
begin
    select actor_principal_id, tenant_id into v_actor, v_tenant_id from identity_auth.require_tenant_administrator(p_session_token_hash);
    if v_actor is null then
        raise exception 'staff deactivation is not authorized' using errcode = '42501';
    end if;
    select exists (select 1 from identity_auth.role_assignment where tenant_id = v_tenant_id and staff_principal_id = p_target_principal_id
                   and role_code = 'ADMIN_VETERINARIA' and revoked_at is null) into v_is_admin;
    if not exists (select 1 from identity_auth.account_profile where principal_id = p_target_principal_id and tenant_id = v_tenant_id
                   and account_type = 'STAFF' and status = 'ACTIVA' for update) then
        raise exception 'staff account is not available' using errcode = '22023';
    end if;
    perform 1 from identity_auth.account_profile a join identity_auth.role_assignment r on r.staff_principal_id = a.principal_id
     where a.tenant_id = v_tenant_id and a.account_type = 'STAFF' and a.status = 'ACTIVA'
       and r.role_code = 'ADMIN_VETERINARIA' and r.revoked_at is null for update;
    if v_is_admin and (select count(*) from identity_auth.account_profile a join identity_auth.role_assignment r on r.staff_principal_id = a.principal_id
                       where a.tenant_id = v_tenant_id and a.account_type = 'STAFF' and a.status = 'ACTIVA'
                         and r.role_code = 'ADMIN_VETERINARIA' and r.revoked_at is null) <= 1 then
        raise exception 'last tenant administrator must remain active' using errcode = '23514';
    end if;
    update identity_auth.account_profile set status = 'DESACTIVADA', updated_at = current_timestamp
     where principal_id = p_target_principal_id and tenant_id = v_tenant_id;
    update identity_auth.principal set status = 'DESACTIVADA', updated_at = current_timestamp
     where id = p_target_principal_id and account_type = 'STAFF';
    update identity_auth.user_session set revoked_at = current_timestamp
     where principal_id = p_target_principal_id and tenant_id = v_tenant_id and revoked_at is null;
    insert into tenant_admin.audit_entry (id, tenant_id, actor_principal_id, action_code, resource_type, resource_id, outcome)
    values (gen_random_uuid(), v_tenant_id, v_actor, 'STAFF_DEACTIVATED', 'STAFF_ACCOUNT', p_target_principal_id, 'SUCCESS');
    return true;
end;
$$;

-- This is deliberately SECURITY INVOKER.  Only av_schema_owner can call it;
-- neither PUBLIC nor av_app receives EXECUTE.  It is for a local operator,
-- never for application startup or HTTP traffic.
create or replace function tenant_admin.bootstrap_saas_administrator(p_normalized_email varchar, p_password_hash varchar)
returns uuid
language plpgsql
security invoker
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_principal_id uuid := gen_random_uuid();
begin
    if session_user <> 'av_schema_owner' or p_normalized_email is null or p_password_hash is null then
        raise exception 'operator bootstrap is not authorized' using errcode = '42501';
    end if;
    perform pg_advisory_xact_lock(hashtextextended('tenant_admin.saas_bootstrap', 0));
    if exists (select 1 from tenant_admin.saas_bootstrap_state)
       or exists (select 1 from tenant_admin.saas_administrator) then
        raise exception 'saas bootstrap has already been completed' using errcode = '23505';
    end if;
    insert into identity_auth.principal (id, account_type, password_hash, status, email_verified_at, password_changed_at)
    values (v_principal_id, 'SAAS', p_password_hash, 'ACTIVA', current_timestamp, current_timestamp);
    insert into tenant_admin.saas_administrator (principal_id, normalized_email, status)
    values (v_principal_id, p_normalized_email, 'ACTIVA');
    insert into tenant_admin.saas_bootstrap_state (singleton, principal_id, initialized_by_db_role)
    values (true, v_principal_id, session_user);
    insert into tenant_admin.audit_entry (id, actor_principal_id, action_code, resource_type, resource_id, outcome)
    values (gen_random_uuid(), v_principal_id, 'SAAS_BOOTSTRAPPED', 'SAAS_ADMINISTRATOR', v_principal_id, 'SUCCESS');
    return v_principal_id;
end;
$$;

-- Fail closed if Flyway is not using the approved non-superuser schema owner.
alter table tenant_admin.saas_bootstrap_state owner to av_schema_owner;
alter function identity_auth.require_tenant_administrator(text) owner to av_schema_owner;
alter function tenant_admin.require_global_administrator(text) owner to av_schema_owner;
alter function identity_auth.authenticate_and_create_session(varchar, varchar, varchar, text, text, text, timestamptz) owner to av_schema_owner;
alter function identity_auth.revoke_session_by_token(text) owner to av_schema_owner;
alter function tenant_admin.start_tenant_registration(varchar, varchar, varchar, varchar, varchar, varchar, varchar, varchar, timestamptz, text, timestamptz) owner to av_schema_owner;
alter function tenant_admin.resume_tenant_registration(uuid, varchar, text, timestamptz) owner to av_schema_owner;
alter function tenant_admin.verify_tenant_registration(text, text, timestamptz) owner to av_schema_owner;
alter function tenant_admin.complete_tenant_registration(text, varchar, varchar) owner to av_schema_owner;
alter function identity_auth.register_client(varchar, varchar, varchar, text, timestamptz) owner to av_schema_owner;
alter function identity_auth.verify_client_email(text) owner to av_schema_owner;
alter function identity_auth.resend_client_email_verification(varchar, varchar, text, timestamptz) owner to av_schema_owner;
alter function identity_auth.verify_email_link(text, text, timestamptz) owner to av_schema_owner;
alter function identity_auth.request_password_recovery(varchar, varchar, varchar, text, timestamptz) owner to av_schema_owner;
alter function identity_auth.reset_password_with_recovery(text, varchar) owner to av_schema_owner;
alter function identity_auth.invite_staff(text, varchar, jsonb, text, timestamptz) owner to av_schema_owner;
alter function identity_auth.accept_staff_invitation(text, varchar) owner to av_schema_owner;
alter function identity_auth.list_staff_for_session(text) owner to av_schema_owner;
alter function identity_auth.replace_staff_roles(text, uuid, jsonb) owner to av_schema_owner;
alter function identity_auth.deactivate_staff(text, uuid) owner to av_schema_owner;
alter function tenant_admin.bootstrap_saas_administrator(varchar, varchar) owner to av_schema_owner;

revoke all on function identity_auth.require_tenant_administrator(text) from public;
revoke all on function identity_auth.require_tenant_administrator(text) from av_app;
revoke all on function tenant_admin.require_global_administrator(text) from public;
revoke all on function tenant_admin.require_global_administrator(text) from av_app;
revoke all on function identity_auth.authenticate_and_create_session(varchar, varchar, varchar, text, text, text, timestamptz) from public;
revoke all on function identity_auth.revoke_session_by_token(text) from public;
revoke all on function tenant_admin.start_tenant_registration(varchar, varchar, varchar, varchar, varchar, varchar, varchar, varchar, timestamptz, text, timestamptz) from public;
revoke all on function tenant_admin.resume_tenant_registration(uuid, varchar, text, timestamptz) from public;
revoke all on function tenant_admin.verify_tenant_registration(text, text, timestamptz) from public;
revoke all on function tenant_admin.complete_tenant_registration(text, varchar, varchar) from public;
revoke all on function identity_auth.register_client(varchar, varchar, varchar, text, timestamptz) from public;
revoke all on function identity_auth.verify_client_email(text) from public;
revoke all on function identity_auth.resend_client_email_verification(varchar, varchar, text, timestamptz) from public;
revoke all on function identity_auth.verify_email_link(text, text, timestamptz) from public;
revoke all on function identity_auth.request_password_recovery(varchar, varchar, varchar, text, timestamptz) from public;
revoke all on function identity_auth.reset_password_with_recovery(text, varchar) from public;
revoke all on function identity_auth.invite_staff(text, varchar, jsonb, text, timestamptz) from public;
revoke all on function identity_auth.accept_staff_invitation(text, varchar) from public;
revoke all on function identity_auth.list_staff_for_session(text) from public;
revoke all on function identity_auth.replace_staff_roles(text, uuid, jsonb) from public;
revoke all on function identity_auth.deactivate_staff(text, uuid) from public;
revoke all on function tenant_admin.bootstrap_saas_administrator(varchar, varchar) from public;
revoke all on function tenant_admin.bootstrap_saas_administrator(varchar, varchar) from av_app;

grant execute on function identity_auth.authenticate_and_create_session(varchar, varchar, varchar, text, text, text, timestamptz) to av_app;
grant execute on function identity_auth.revoke_session_by_token(text) to av_app;
grant execute on function tenant_admin.start_tenant_registration(varchar, varchar, varchar, varchar, varchar, varchar, varchar, varchar, timestamptz, text, timestamptz) to av_app;
grant execute on function tenant_admin.resume_tenant_registration(uuid, varchar, text, timestamptz) to av_app;
grant execute on function tenant_admin.complete_tenant_registration(text, varchar, varchar) to av_app;
grant execute on function identity_auth.register_client(varchar, varchar, varchar, text, timestamptz) to av_app;
grant execute on function identity_auth.resend_client_email_verification(varchar, varchar, text, timestamptz) to av_app;
grant execute on function identity_auth.verify_email_link(text, text, timestamptz) to av_app;
grant execute on function identity_auth.request_password_recovery(varchar, varchar, varchar, text, timestamptz) to av_app;
grant execute on function identity_auth.reset_password_with_recovery(text, varchar) to av_app;
grant execute on function identity_auth.invite_staff(text, varchar, jsonb, text, timestamptz) to av_app;
grant execute on function identity_auth.accept_staff_invitation(text, varchar) to av_app;
grant execute on function identity_auth.list_staff_for_session(text) to av_app;
grant execute on function identity_auth.replace_staff_roles(text, uuid, jsonb) to av_app;
grant execute on function identity_auth.deactivate_staff(text, uuid) to av_app;
