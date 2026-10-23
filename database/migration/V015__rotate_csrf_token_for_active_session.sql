-- B01/B06: reissue one CSRF token only for the current, still-valid opaque session.
-- The Flyway migration is transactional: definition, ownership and ACL are atomic.

create or replace function identity_auth.rotate_csrf_token(
    p_session_token_hash text,
    p_new_csrf_token_hash text
)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
begin
    if p_session_token_hash is null
       or p_new_csrf_token_hash is null
       or p_session_token_hash !~ '^[0-9a-f]{64}$'
       or p_new_csrf_token_hash !~ '^[0-9a-f]{64}$' then
        raise exception 'invalid opaque token hash' using errcode = '22023';
    end if;

    update identity_auth.user_session session_row
       set csrf_hash = p_new_csrf_token_hash
     where session_row.token_hash = p_session_token_hash
       and session_row.revoked_at is null
       and session_row.expires_at > statement_timestamp()
       and exists (
           select 1
             from identity_auth.principal principal_row
             left join identity_auth.account_profile account_row
               on account_row.principal_id = principal_row.id
              and account_row.status = 'ACTIVA'
             left join tenancy.tenant tenant_row
               on tenant_row.id = session_row.tenant_id
              and tenant_row.status = 'ACTIVO'
             left join tenant_admin.saas_administrator saas_row
               on saas_row.principal_id = principal_row.id
              and saas_row.status = 'ACTIVA'
            where principal_row.id = session_row.principal_id
              and principal_row.status = 'ACTIVA'
              and ((session_row.account_type in ('STAFF', 'CLIENT')
                    and account_row.tenant_id = session_row.tenant_id
                    and tenant_row.id is not null)
                   or (session_row.account_type = 'SAAS'
                       and session_row.tenant_id is null
                       and saas_row.principal_id is not null))
       );

    return found;
end;
$$;

alter function identity_auth.rotate_csrf_token(text, text) owner to av_schema_owner;
revoke all on function identity_auth.rotate_csrf_token(text, text) from public;
grant execute on function identity_auth.rotate_csrf_token(text, text) to av_app;
