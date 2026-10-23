-- PROPUESTA NO EJECUTABLE. No está en database/migration y Flyway no la consume.
-- Si se autoriza V012, copiar estos bytes sin cambios a:
-- database/migration/V012__g4_i01_qualify_remaining_identity_output_columns.sql
-- La migración propuesta es transaccional y redefine exclusivamente las dos
-- funciones afectadas por referencias PL/pgSQL ambiguas.

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
    select actor_principal_id, tenant_id into v_actor, v_tenant_id
      from identity_auth.require_tenant_administrator(p_session_token_hash);
    if v_actor is null or jsonb_typeof(p_roles) <> 'array' or jsonb_array_length(p_roles) = 0
       or exists (
            select 1
              from jsonb_array_elements_text(p_roles) requested_role
             where value not in ('ADMIN_VETERINARIA', 'RECEPCION', 'VETERINARIO', 'CAJERO', 'GROOMING')
       ) then
        raise exception 'staff role change is not authorized' using errcode = '42501';
    end if;

    select profile.normalized_email, profile.status into v_email, v_status
      from identity_auth.account_profile profile
     where profile.principal_id = p_target_principal_id
       and profile.tenant_id = v_tenant_id
       and profile.account_type = 'STAFF'
     for update;
    if v_email is null then
        raise exception 'staff account is not available' using errcode = '22023';
    end if;

    perform 1
      from identity_auth.account_profile profile
      join identity_auth.role_assignment role_row on role_row.staff_principal_id = profile.principal_id
     where profile.tenant_id = v_tenant_id
       and profile.account_type = 'STAFF'
       and profile.status = 'ACTIVA'
       and role_row.role_code = 'ADMIN_VETERINARIA'
       and role_row.revoked_at is null
     for update;

    select coalesce(jsonb_agg(role_row.role_code), '[]'::jsonb) into v_current_roles
      from identity_auth.role_assignment role_row
     where role_row.tenant_id = v_tenant_id
       and role_row.staff_principal_id = p_target_principal_id
       and role_row.revoked_at is null;
    if v_status = 'ACTIVA' and v_current_roles ? 'ADMIN_VETERINARIA' and not (p_roles ? 'ADMIN_VETERINARIA')
       and (
            select count(*)
              from identity_auth.account_profile profile
              join identity_auth.role_assignment role_row on role_row.staff_principal_id = profile.principal_id
             where profile.tenant_id = v_tenant_id
               and profile.account_type = 'STAFF'
               and profile.status = 'ACTIVA'
               and role_row.role_code = 'ADMIN_VETERINARIA'
               and role_row.revoked_at is null
       ) <= 1 then
        raise exception 'last tenant administrator must remain active' using errcode = '23514';
    end if;

    update identity_auth.role_assignment role_row
       set revoked_at = current_timestamp,
           revoked_by_principal_id = v_actor
     where role_row.tenant_id = v_tenant_id
       and role_row.staff_principal_id = p_target_principal_id
       and role_row.revoked_at is null;
    insert into identity_auth.role_assignment (id, tenant_id, staff_principal_id, role_code, assigned_by_principal_id)
    select gen_random_uuid(), v_tenant_id, p_target_principal_id, requested_role.value, v_actor
      from (
          select distinct value
            from jsonb_array_elements_text(p_roles)
      ) requested_role;
    update identity_auth.user_session session_row
       set revoked_at = current_timestamp
     where session_row.principal_id = p_target_principal_id
       and session_row.tenant_id = v_tenant_id
       and session_row.revoked_at is null;
    insert into tenant_admin.audit_entry (id, tenant_id, actor_principal_id, action_code, resource_type, resource_id, outcome)
    values (gen_random_uuid(), v_tenant_id, v_actor, 'STAFF_ROLES_REPLACED', 'STAFF_ACCOUNT', p_target_principal_id, 'SUCCESS');

    return query
    select v_email,
           v_status,
           (
               select coalesce(jsonb_agg(requested_role.value order by requested_role.value), '[]'::jsonb)
                 from (
                     select distinct value
                       from jsonb_array_elements_text(p_roles)
                 ) requested_role
           );
end;
$$;

alter function identity_auth.replace_staff_roles(text, uuid, jsonb) owner to av_schema_owner;
revoke all on function identity_auth.replace_staff_roles(text, uuid, jsonb) from public;
grant execute on function identity_auth.replace_staff_roles(text, uuid, jsonb) to av_app;

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
    on conflict (tenant_id, staff_principal_id, role_code)
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
