-- Corrects the existing role-replacement workflow without changing its access
-- surface: a previously revoked role must be reactivated through the unique key
-- instead of inserted again as a duplicate.
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
      ) requested_role
    on conflict (tenant_id, staff_principal_id, role_code)
    do update set revoked_at = null,
                  revoked_by_principal_id = null,
                  assigned_by_principal_id = excluded.assigned_by_principal_id,
                  assigned_at = current_timestamp;
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
