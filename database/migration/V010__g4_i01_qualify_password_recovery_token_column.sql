-- G4-I01: resolve the PL/pgSQL output-variable ambiguity in password recovery.
-- Preserves the V009 bounded-expiry and privileged-function contract.

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
        update identity_auth.one_time_token token set invalidated_at = current_timestamp
         where token.purpose = 'PASSWORD_RECOVERY' and token.principal_id = v_principal_id
           and token.consumed_at is null and token.invalidated_at is null;
        insert into identity_auth.one_time_token (id, purpose, token_hash, principal_id, tenant_id, expires_at)
        values (gen_random_uuid(), 'PASSWORD_RECOVERY', p_recovery_token_hash, v_principal_id, v_tenant_id, p_token_expires_at);

        insert into tenant_admin.notification_outbox (id, purpose, recipient_email, related_resource_id, status, delivered_at)
        values (gen_random_uuid(), 'PASSWORD_RECOVERY', p_normalized_email, v_principal_id, 'ACCEPTED_LOCAL', current_timestamp);
        return query select v_principal_id, v_tenant_id;
    end if;
end;
$$;

alter function identity_auth.request_password_recovery(varchar, varchar, varchar, text, timestamptz) owner to av_schema_owner;
revoke all on function identity_auth.request_password_recovery(varchar, varchar, varchar, text, timestamptz) from public;
grant execute on function identity_auth.request_password_recovery(varchar, varchar, varchar, text, timestamptz) to av_app;
