-- G4-I01: resolve the remaining PL/pgSQL output-variable ambiguity in invite_staff.
-- This migration preserves the V007 security and time-boundary contract.

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
    select profile.principal_id, profile.status into v_principal_id, v_existing_status
      from identity_auth.account_profile profile
     where profile.tenant_id = v_tenant_id
       and profile.account_type = 'STAFF'
       and profile.normalized_email = p_normalized_email
     for update;
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
    update identity_auth.one_time_token token set invalidated_at = current_timestamp
     where token.purpose = 'STAFF_INVITATION' and token.principal_id = v_principal_id
       and token.consumed_at is null and token.invalidated_at is null;
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

alter function identity_auth.invite_staff(text, varchar, jsonb, text, timestamptz) owner to av_schema_owner;
revoke all on function identity_auth.invite_staff(text, varchar, jsonb, text, timestamptz) from public;
grant execute on function identity_auth.invite_staff(text, varchar, jsonb, text, timestamptz) to av_app;
