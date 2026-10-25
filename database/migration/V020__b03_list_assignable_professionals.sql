-- B03: minimal, tenant-bounded query of active clinical and grooming professionals.
-- The caller supplies only an opaque session-token hash issued and resolved by the
-- backend. The function authenticates that session again in PostgreSQL; it does
-- not trust application GUCs or a caller-supplied tenant identifier.
create function identity_auth.list_assignable_professionals_for_session(
    p_session_token_hash text
)
returns table (
    principal_id uuid,
    roles jsonb
)
language sql
stable
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
    with authorized_actor as (
        select session_row.tenant_id
          from identity_auth.user_session session_row
          join identity_auth.principal actor_principal
            on actor_principal.id = session_row.principal_id
           and actor_principal.account_type = 'STAFF'
           and actor_principal.status = 'ACTIVA'
          join identity_auth.account_profile actor_profile
            on actor_profile.principal_id = session_row.principal_id
           and actor_profile.tenant_id = session_row.tenant_id
           and actor_profile.account_type = 'STAFF'
           and actor_profile.status = 'ACTIVA'
          join tenancy.tenant tenant_row
            on tenant_row.id = session_row.tenant_id
           and tenant_row.status = 'ACTIVO'
         where session_row.token_hash = p_session_token_hash
           and session_row.account_type = 'STAFF'
           and session_row.revoked_at is null
           and session_row.expires_at > statement_timestamp()
           and exists (
                select 1
                  from identity_auth.role_assignment actor_role
                 where actor_role.tenant_id = session_row.tenant_id
                   and actor_role.staff_principal_id = session_row.principal_id
                   and actor_role.role_code in ('ADMIN_VETERINARIA', 'RECEPCION')
                   and actor_role.revoked_at is null
           )
    )
    select target_profile.principal_id,
           jsonb_agg(target_role.role_code order by target_role.role_code)
      from authorized_actor actor
      join identity_auth.account_profile target_profile
        on target_profile.tenant_id = actor.tenant_id
       and target_profile.account_type = 'STAFF'
       and target_profile.status = 'ACTIVA'
      join identity_auth.principal target_principal
        on target_principal.id = target_profile.principal_id
       and target_principal.account_type = 'STAFF'
       and target_principal.status = 'ACTIVA'
      join identity_auth.role_assignment target_role
        on target_role.tenant_id = actor.tenant_id
       and target_role.staff_principal_id = target_profile.principal_id
       and target_role.role_code in ('VETERINARIO', 'GROOMING')
       and target_role.revoked_at is null
     group by target_profile.principal_id
     order by target_profile.principal_id;
$$;

alter function identity_auth.list_assignable_professionals_for_session(text)
    owner to av_schema_owner;
revoke all on function identity_auth.list_assignable_professionals_for_session(text) from public;
grant execute on function identity_auth.list_assignable_professionals_for_session(text) to av_app;
