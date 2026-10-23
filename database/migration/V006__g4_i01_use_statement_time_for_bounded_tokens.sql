-- G4-I01: current_timestamp is fixed at transaction start in PostgreSQL.
-- These bounded routines may be called consecutively in one transaction, so
-- expiry ceilings must use the current statement time.  No privilege changes.

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
    v_statement_time timestamptz := statement_timestamp();
begin
    if p_account_type not in ('STAFF', 'CLIENT', 'SAAS')
       or p_normalized_email is null
       or p_password is null
       or p_token_hash !~ '^[0-9a-f]{64}$'
       or p_csrf_hash !~ '^[0-9a-f]{64}$'
       or p_expires_at <= v_statement_time or p_expires_at > v_statement_time + interval '8 hours' then
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
    v_statement_time timestamptz := statement_timestamp();
begin
    select purpose, tenant_registration_id, principal_id, tenant_id
      into v_purpose, v_registration_id, v_principal_id, v_tenant_id
      from identity_auth.one_time_token
     where token_hash = p_verification_token_hash and consumed_at is null and invalidated_at is null and expires_at > v_statement_time
     for update;
    if v_purpose in ('REGISTRATION_EMAIL_VERIFICATION', 'REGISTRATION_COMPLETION') then
        if p_completion_grant_hash !~ '^[0-9a-f]{64}$' or p_completion_expires_at <= v_statement_time
           or p_completion_expires_at > v_statement_time + interval '15 minutes' then
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
    v_statement_time timestamptz := statement_timestamp();
begin
    select actor_principal_id, tenant_id into v_actor, v_tenant_id from identity_auth.require_tenant_administrator(p_session_token_hash);
    if v_actor is null or p_invitation_token_hash !~ '^[0-9a-f]{64}$' or p_token_expires_at <= v_statement_time
       or p_token_expires_at > v_statement_time + interval '24 hours'
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

alter function identity_auth.authenticate_and_create_session(varchar, varchar, varchar, text, text, text, timestamptz) owner to av_schema_owner;
alter function identity_auth.verify_email_link(text, text, timestamptz) owner to av_schema_owner;
alter function identity_auth.invite_staff(text, varchar, jsonb, text, timestamptz) owner to av_schema_owner;

revoke all on function identity_auth.authenticate_and_create_session(varchar, varchar, varchar, text, text, text, timestamptz) from public;
revoke all on function identity_auth.verify_email_link(text, text, timestamptz) from public;
revoke all on function identity_auth.invite_staff(text, varchar, jsonb, text, timestamptz) from public;

grant execute on function identity_auth.authenticate_and_create_session(varchar, varchar, varchar, text, text, text, timestamptz) to av_app;
grant execute on function identity_auth.verify_email_link(text, text, timestamptz) to av_app;
grant execute on function identity_auth.invite_staff(text, varchar, jsonb, text, timestamptz) to av_app;
