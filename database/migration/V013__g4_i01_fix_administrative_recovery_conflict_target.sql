-- G4-I01: resolve only the PL/pgSQL name ambiguity in the administrative
-- recovery role assignment. This transactional migration redefines one
-- pre-existing SECURITY DEFINER function and preserves its owner and ACL.

create or replace function tenant_admin.review_administrative_recovery_for_session(
    p_session_token_hash text,
    p_recovery_id uuid,
    p_new_status varchar,
    p_reason varchar,
    p_legitimacy_checked boolean,
    p_invitation_token_hash text,
    p_invitation_expires_at timestamptz
)
returns table (principal_id uuid, tenant_id uuid, applicant_email varchar, executed boolean)
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_actor uuid;
    v_recovery tenant_admin.administrative_recovery%rowtype;
    v_tenant_status varchar;
    v_principal_id uuid;
begin
    v_actor := tenant_admin.require_global_administrator(p_session_token_hash);
    if p_new_status not in ('VERIFICANDO', 'INFO_REQUERIDA', 'RECHAZADA', 'APROBADA')
       or coalesce(length(trim(p_reason)), 0) < 3 then
        raise exception 'invalid recovery review' using errcode = '22023';
    end if;

    select recovery_row.* into v_recovery
      from tenant_admin.administrative_recovery recovery_row
     where recovery_row.id = p_recovery_id
     for update;
    if v_recovery.id is null or v_recovery.status in ('RECHAZADA', 'EJECUTADA') then
        raise exception 'recovery is not available' using errcode = '22023';
    end if;
    if p_new_status <> 'APROBADA' then
        update tenant_admin.administrative_recovery recovery_row
           set status = p_new_status,
               decision_reason = p_reason,
               reviewed_by_principal_id = v_actor,
               reviewed_at = current_timestamp,
               updated_at = current_timestamp
         where recovery_row.id = v_recovery.id;
        return query select null::uuid, v_recovery.tenant_id, v_recovery.applicant_email, false;
        return;
    end if;
    if not p_legitimacy_checked or p_invitation_token_hash !~ '^[0-9a-f]{64}$'
       or p_invitation_expires_at <= current_timestamp
       or p_invitation_expires_at > current_timestamp + interval '24 hours' then
        raise exception 'approved recovery is not authorized' using errcode = '42501';
    end if;

    select tenant_row.status into v_tenant_status
      from tenancy.tenant tenant_row
     where tenant_row.id = v_recovery.tenant_id
     for update;
    if v_tenant_status <> 'ACTIVO' then
        raise exception 'tenant is not eligible for recovery' using errcode = '22023';
    end if;

    update tenant_admin.administrative_recovery recovery_row
       set status = 'APROBADA',
           decision_reason = p_reason,
           reviewed_by_principal_id = v_actor,
           reviewed_at = current_timestamp,
           approval_attested_by_principal_id = v_actor,
           approval_attested_at = current_timestamp,
           updated_at = current_timestamp
     where recovery_row.id = v_recovery.id;
    insert into tenant_admin.audit_entry (id, tenant_id, actor_principal_id, action_code, resource_type, resource_id, outcome, reason)
    values (gen_random_uuid(), v_recovery.tenant_id, v_actor, 'ADMINISTRATIVE_RECOVERY_APPROVED',
            'ADMINISTRATIVE_RECOVERY', v_recovery.id, 'SUCCESS', p_reason);

    select profile.principal_id into v_principal_id
      from identity_auth.account_profile profile
     where profile.tenant_id = v_recovery.tenant_id
       and profile.account_type = 'STAFF'
       and profile.normalized_email = v_recovery.normalized_applicant_email
     for update;
    if v_principal_id is null then
        v_principal_id := gen_random_uuid();
        insert into identity_auth.principal (id, account_type, status)
        values (v_principal_id, 'STAFF', 'INVITADA');
        insert into identity_auth.account_profile (principal_id, tenant_id, account_type, normalized_email, status)
        values (v_principal_id, v_recovery.tenant_id, 'STAFF', v_recovery.normalized_applicant_email, 'INVITADA');
    elsif exists (
        select 1
          from identity_auth.account_profile profile
         where profile.principal_id = v_principal_id
           and profile.status <> 'INVITADA'
    ) then
        raise exception 'recovery account is not eligible' using errcode = '22023';
    end if;

    update identity_auth.role_assignment role_row
       set revoked_at = current_timestamp,
           revoked_by_principal_id = v_actor
     where role_row.tenant_id = v_recovery.tenant_id
       and role_row.staff_principal_id = v_principal_id
       and role_row.revoked_at is null;
    insert into identity_auth.role_assignment (id, tenant_id, staff_principal_id, role_code, assigned_by_principal_id)
    values (gen_random_uuid(), v_recovery.tenant_id, v_principal_id, 'ADMIN_VETERINARIA', v_actor)
    on conflict on constraint role_assignment_tenant_id_staff_principal_id_role_code_key
    do update set revoked_at = null,
                  revoked_by_principal_id = null,
                  assigned_by_principal_id = excluded.assigned_by_principal_id,
                  assigned_at = current_timestamp;
    update identity_auth.one_time_token token_row
       set invalidated_at = current_timestamp
     where token_row.purpose = 'STAFF_INVITATION'
       and token_row.principal_id = v_principal_id
       and token_row.consumed_at is null
       and token_row.invalidated_at is null;
    insert into identity_auth.one_time_token (id, purpose, token_hash, principal_id, tenant_id, expires_at)
    values (gen_random_uuid(), 'STAFF_INVITATION', p_invitation_token_hash, v_principal_id, v_recovery.tenant_id, p_invitation_expires_at);
    update tenant_admin.administrative_recovery recovery_row
       set status = 'EJECUTADA',
           decision_reason = p_reason,
           reviewed_by_principal_id = v_actor,
           reviewed_at = current_timestamp,
           updated_at = current_timestamp
     where recovery_row.id = v_recovery.id;
    insert into tenant_admin.notification_outbox (id, purpose, recipient_email, related_resource_id, status, delivered_at)
    values (gen_random_uuid(), 'STAFF_INVITATION', v_recovery.applicant_email, v_principal_id, 'ACCEPTED_LOCAL', current_timestamp);
    insert into tenant_admin.audit_entry (id, tenant_id, actor_principal_id, action_code, resource_type, resource_id, outcome, reason)
    values (gen_random_uuid(), v_recovery.tenant_id, v_actor, 'ADMINISTRATIVE_RECOVERY_EXECUTED',
            'ADMINISTRATIVE_RECOVERY', v_recovery.id, 'SUCCESS', p_reason);

    return query select v_principal_id, v_recovery.tenant_id, v_recovery.applicant_email, true;
end;
$$;

alter function tenant_admin.review_administrative_recovery_for_session(text, uuid, varchar, varchar, boolean, text, timestamptz)
    owner to av_schema_owner;
revoke all on function tenant_admin.review_administrative_recovery_for_session(text, uuid, varchar, varchar, boolean, text, timestamptz)
    from public;
grant execute on function tenant_admin.review_administrative_recovery_for_session(text, uuid, varchar, varchar, boolean, text, timestamptz)
    to av_app;
