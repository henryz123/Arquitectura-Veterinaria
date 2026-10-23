-- G4-I01: serialize password-recovery issuance and enforce one active token.
-- The migration is intentionally transactional: it refuses pre-existing
-- duplicates instead of deleting data, then installs the integrity guard and
-- redefines only the authorized recovery-issuance and consumption functions.

do $$
begin
    if exists (
        select 1
          from identity_auth.one_time_token token
         where token.purpose = 'PASSWORD_RECOVERY'
           and token.consumed_at is null
           and token.invalidated_at is null
         group by token.principal_id
        having count(*) > 1
    ) then
        raise exception 'cannot enforce one active password-recovery token while duplicate active tokens exist'
            using errcode = '23505';
    end if;
end;
$$;

create unique index uq_one_time_token_password_recovery_active_principal
    on identity_auth.one_time_token (principal_id)
 where purpose = 'PASSWORD_RECOVERY'
   and consumed_at is null
   and invalidated_at is null;

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
    v_statement_time timestamptz := statement_timestamp();
begin
    if p_recovery_token_hash !~ '^[0-9a-f]{64}$' or p_token_expires_at <= v_statement_time
       or p_token_expires_at > v_statement_time + interval '30 minutes' then
        return;
    end if;

    if p_account_type = 'SAAS' and p_tenant_slug is null then
        select principal.id, null::uuid into v_principal_id, v_tenant_id
          from identity_auth.principal principal
          join tenant_admin.saas_administrator administrator on administrator.principal_id = principal.id
         where principal.account_type = 'SAAS'
           and principal.status = 'ACTIVA'
           and administrator.status = 'ACTIVA'
           and administrator.normalized_email = p_normalized_email
         for update of principal;
    elsif p_account_type in ('STAFF', 'CLIENT') then
        select principal.id, profile.tenant_id into v_principal_id, v_tenant_id
          from identity_auth.principal principal
          join identity_auth.account_profile profile on profile.principal_id = principal.id
          join tenancy.tenant tenant on tenant.id = profile.tenant_id and tenant.status = 'ACTIVO'
         where principal.account_type = p_account_type
           and principal.status = 'ACTIVA'
           and profile.account_type = p_account_type
           and profile.status = 'ACTIVA'
           and tenant.slug = p_tenant_slug
           and profile.normalized_email = p_normalized_email
         for update of principal;
    else
        return;
    end if;

    if v_principal_id is not null then
        update identity_auth.one_time_token token
           set invalidated_at = current_timestamp
         where token.purpose = 'PASSWORD_RECOVERY'
           and token.principal_id = v_principal_id
           and token.consumed_at is null
           and token.invalidated_at is null;

        insert into identity_auth.one_time_token (id, purpose, token_hash, principal_id, tenant_id, expires_at)
        values (gen_random_uuid(), 'PASSWORD_RECOVERY', p_recovery_token_hash, v_principal_id, v_tenant_id, p_token_expires_at);

        insert into tenant_admin.notification_outbox (id, purpose, recipient_email, related_resource_id, status, delivered_at)
        values (gen_random_uuid(), 'PASSWORD_RECOVERY', p_normalized_email, v_principal_id, 'ACCEPTED_LOCAL', current_timestamp);

        return query select v_principal_id, v_tenant_id;
    end if;
end;
$$;

-- Read the candidate token without locking it, then serialize with issuers on
-- the stable principal row before consuming it. The token predicate is checked
-- again by the UPDATE after the principal lock is held.
create or replace function identity_auth.reset_password_with_recovery(
    p_recovery_token_hash text,
    p_password_hash varchar
)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_principal_id uuid;
    v_tenant_id uuid;
    v_statement_time timestamptz := statement_timestamp();
begin
    if p_recovery_token_hash !~ '^[0-9a-f]{64}$' or p_password_hash is null then
        raise exception 'invalid recovery link' using errcode = '22023';
    end if;

    select token.principal_id, token.tenant_id into v_principal_id, v_tenant_id
      from identity_auth.one_time_token token
     where token.token_hash = p_recovery_token_hash
       and token.purpose = 'PASSWORD_RECOVERY'
       and token.consumed_at is null
       and token.invalidated_at is null
       and token.expires_at > v_statement_time;

    if v_principal_id is null then
        raise exception 'recovery link is not valid' using errcode = '22023';
    end if;

    perform 1
      from identity_auth.principal principal
     where principal.id = v_principal_id
     for update;

    if not found then
        raise exception 'recovery link is not valid' using errcode = '22023';
    end if;

    update identity_auth.one_time_token token
       set consumed_at = current_timestamp
     where token.token_hash = p_recovery_token_hash
       and token.purpose = 'PASSWORD_RECOVERY'
       and token.consumed_at is null
       and token.invalidated_at is null
       and token.expires_at > v_statement_time
     returning token.principal_id, token.tenant_id into v_principal_id, v_tenant_id;

    if v_principal_id is null then
        raise exception 'recovery link is not valid' using errcode = '22023';
    end if;

    update identity_auth.principal
       set password_hash = p_password_hash,
           status = 'ACTIVA',
           password_changed_at = current_timestamp,
           email_verified_at = coalesce(email_verified_at, current_timestamp),
           updated_at = current_timestamp
     where id = v_principal_id;

    update identity_auth.user_session
       set revoked_at = current_timestamp
     where principal_id = v_principal_id
       and revoked_at is null;

    if v_tenant_id is not null then
        insert into tenant_admin.audit_entry (id, tenant_id, actor_principal_id, action_code, resource_type, resource_id, outcome)
        values (gen_random_uuid(), v_tenant_id, v_principal_id, 'PASSWORD_RESET', 'ACCOUNT', v_principal_id, 'SUCCESS');
    end if;

    return true;
end;
$$;

alter function identity_auth.request_password_recovery(varchar, varchar, varchar, text, timestamptz) owner to av_schema_owner;
revoke all on function identity_auth.request_password_recovery(varchar, varchar, varchar, text, timestamptz) from public;
grant execute on function identity_auth.request_password_recovery(varchar, varchar, varchar, text, timestamptz) to av_app;

alter function identity_auth.reset_password_with_recovery(text, varchar) owner to av_schema_owner;
revoke all on function identity_auth.reset_password_with_recovery(text, varchar) from public;
grant execute on function identity_auth.reset_password_with_recovery(text, varchar) to av_app;
