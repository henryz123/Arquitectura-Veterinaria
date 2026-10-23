-- G4-I01: forward-only hardening of V001-V003 and bounded replacements.
-- The migration runs in one Flyway transaction.  It neither rewrites prior
-- migrations nor grants av_app direct access to privileged identity records.

alter function identity_auth.lookup_staff_identity(text, text)
    set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp;
alter function identity_auth.lookup_context_identity(text, text, text)
    set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp;
alter function identity_auth.resolve_session_context(text)
    set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp;
alter function identity_auth.lookup_public_tenant(text)
    set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp;
alter function tenant_admin.is_ruc_available(varchar, uuid)
    set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp;
alter function tenant_admin.assert_global_session(uuid)
    set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp;
alter function tenant_admin.list_organizations(uuid)
    set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp;
alter function tenant_admin.change_tenant_status(uuid, uuid, varchar, varchar)
    set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp;
alter function tenant_admin.resolve_ruc_review(uuid, uuid, varchar, varchar, boolean)
    set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp;

revoke all on table tenant_admin.ruc_review from av_app;
revoke all on table tenant_admin.administrative_recovery from av_app;

-- Retain the approver's durable attestation even after an approved recovery is
-- executed.  The ordinary runtime role cannot update this table directly.
alter table tenant_admin.administrative_recovery
    add column if not exists approval_attested_by_principal_id uuid references identity_auth.principal(id),
    add column if not exists approval_attested_at timestamptz;

revoke all on function identity_auth.lookup_staff_identity(text, text) from public;
revoke all on function identity_auth.lookup_context_identity(text, text, text) from public;
revoke all on function identity_auth.resolve_session_context(text) from public;
revoke all on function identity_auth.lookup_public_tenant(text) from public;
revoke all on function tenant_admin.is_ruc_available(varchar, uuid) from public;
revoke all on function tenant_admin.assert_global_session(uuid) from public;
revoke all on function tenant_admin.list_organizations(uuid) from public;
revoke all on function tenant_admin.change_tenant_status(uuid, uuid, varchar, varchar) from public;
revoke all on function tenant_admin.resolve_ruc_review(uuid, uuid, varchar, varchar, boolean) from public;

revoke all on function identity_auth.lookup_staff_identity(text, text) from av_app;
revoke all on function identity_auth.lookup_context_identity(text, text, text) from av_app;
revoke all on function identity_auth.resolve_session_context(text) from av_app;
revoke all on function tenant_admin.is_ruc_available(varchar, uuid) from av_app;
revoke all on function tenant_admin.assert_global_session(uuid) from av_app;
revoke all on function tenant_admin.list_organizations(uuid) from av_app;
revoke all on function tenant_admin.change_tenant_status(uuid, uuid, varchar, varchar) from av_app;
revoke all on function tenant_admin.resolve_ruc_review(uuid, uuid, varchar, varchar, boolean) from av_app;

-- V002 exposes csrf_hash as part of its session resolver.  Keep that legacy
-- routine owner-only and expose two narrower operations instead: one returns
-- context without a stored hash and one compares two opaque hashes in-database.
create or replace function identity_auth.resolve_active_session_context(p_token_hash text)
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
language sql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
    select s.id, s.principal_id, s.tenant_id, t.slug, t.commercial_name, t.status,
           s.account_type,
           case when s.account_type = 'STAFF' then
                coalesce((select jsonb_agg(r.role_code order by r.role_code)
                            from identity_auth.role_assignment r
                           where r.tenant_id = s.tenant_id
                             and r.staff_principal_id = s.principal_id
                             and r.revoked_at is null), '[]'::jsonb)
                when s.account_type = 'SAAS' then jsonb_build_array('ADMIN_SAAS')
                else jsonb_build_array('CLIENT') end,
           s.expires_at
      from identity_auth.user_session s
      join identity_auth.principal p on p.id = s.principal_id and p.status = 'ACTIVA'
      left join identity_auth.account_profile a on a.principal_id = p.id and a.status = 'ACTIVA'
      left join tenancy.tenant t on t.id = s.tenant_id and t.status = 'ACTIVO'
      left join tenant_admin.saas_administrator g on g.principal_id = p.id and g.status = 'ACTIVA'
     where s.token_hash = p_token_hash
       and s.revoked_at is null
       and s.expires_at > current_timestamp
       and ((s.account_type in ('STAFF', 'CLIENT') and a.tenant_id = s.tenant_id and t.id is not null)
            or (s.account_type = 'SAAS' and s.tenant_id is null and g.principal_id is not null));
$$;

create or replace function identity_auth.is_csrf_token_valid(
    p_session_token_hash text,
    p_csrf_token_hash text
)
returns boolean
language sql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
    select exists (
        select 1
          from identity_auth.user_session s
          join identity_auth.principal p on p.id = s.principal_id and p.status = 'ACTIVA'
          left join identity_auth.account_profile a on a.principal_id = p.id and a.status = 'ACTIVA'
          left join tenancy.tenant t on t.id = s.tenant_id and t.status = 'ACTIVO'
          left join tenant_admin.saas_administrator g on g.principal_id = p.id and g.status = 'ACTIVA'
         where s.token_hash = p_session_token_hash
           and s.csrf_hash = p_csrf_token_hash
           and s.revoked_at is null
           and s.expires_at > current_timestamp
           and ((s.account_type in ('STAFF', 'CLIENT') and a.tenant_id = s.tenant_id and t.id is not null)
                or (s.account_type = 'SAAS' and s.tenant_id is null and g.principal_id is not null))
    );
$$;

create or replace function tenant_admin.list_organizations_for_session(p_session_token_hash text)
returns table (
    tenant_id uuid,
    commercial_name varchar,
    ruc varchar,
    commercial_address varchar,
    commercial_phone varchar,
    commercial_email varchar,
    registered_at timestamptz,
    status varchar
)
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
begin
    perform tenant_admin.require_global_administrator(p_session_token_hash);
    return query
    select t.id, t.commercial_name, t.ruc, t.commercial_address, t.commercial_phone,
           t.commercial_email, t.created_at, t.status
      from tenancy.tenant t
     order by t.created_at desc;
end;
$$;

create or replace function tenant_admin.change_tenant_status_for_session(
    p_session_token_hash text,
    p_tenant_id uuid,
    p_new_status varchar,
    p_reason varchar
)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_actor uuid;
    v_current_status varchar;
begin
    v_actor := tenant_admin.require_global_administrator(p_session_token_hash);
    if p_new_status not in ('ACTIVO', 'SUSPENDIDO') or coalesce(length(trim(p_reason)), 0) < 3 then
        raise exception 'invalid tenant status request' using errcode = '22023';
    end if;
    select status into v_current_status from tenancy.tenant where id = p_tenant_id for update;
    if v_current_status is null then
        return false;
    end if;
    if v_current_status = p_new_status then
        return true;
    end if;
    update tenancy.tenant set status = p_new_status where id = p_tenant_id;
    if p_new_status = 'SUSPENDIDO' then
        update identity_auth.user_session set revoked_at = current_timestamp
         where tenant_id = p_tenant_id and revoked_at is null;
    end if;
    insert into tenant_admin.audit_entry (id, tenant_id, actor_principal_id, action_code, resource_type, resource_id, outcome, reason)
    values (gen_random_uuid(), p_tenant_id, v_actor,
            case when p_new_status = 'SUSPENDIDO' then 'TENANT_SUSPENDED' else 'TENANT_REACTIVATED' end,
            'TENANT', p_tenant_id, 'SUCCESS', p_reason);
    return true;
end;
$$;

create or replace function tenant_admin.submit_ruc_review(
    p_ruc varchar,
    p_requester_email varchar,
    p_normalized_requester_email varchar,
    p_contact_channel varchar,
    p_reason varchar
)
returns uuid
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_review_id uuid := gen_random_uuid();
begin
    if p_ruc !~ '^[0-9]{11}$' or coalesce(length(trim(p_requester_email)), 0) = 0
       or coalesce(length(trim(p_normalized_requester_email)), 0) = 0
       or coalesce(length(trim(p_contact_channel)), 0) < 3
       or coalesce(length(trim(p_reason)), 0) < 3 then
        raise exception 'invalid ruc review request' using errcode = '22023';
    end if;
    insert into tenant_admin.ruc_review
        (id, ruc, requester_email, normalized_requester_email, contact_channel, request_reason, status)
    values (v_review_id, p_ruc, p_requester_email, p_normalized_requester_email, p_contact_channel, p_reason, 'PENDIENTE');
    return v_review_id;
end;
$$;

create or replace function tenant_admin.list_ruc_reviews_for_session(p_session_token_hash text)
returns table (
    review_id uuid,
    ruc varchar,
    requester_email varchar,
    normalized_requester_email varchar,
    contact_channel varchar,
    request_reason varchar,
    status varchar,
    resolution_reason varchar,
    resolved_by_principal_id uuid,
    resolved_at timestamptz,
    affected_tenant_id uuid
)
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
begin
    perform tenant_admin.require_global_administrator(p_session_token_hash);
    return query
    select r.id, r.ruc, r.requester_email, r.normalized_requester_email, r.contact_channel, r.request_reason,
           r.status, r.resolution_reason, r.resolved_by_principal_id, r.resolved_at, r.affected_tenant_id
      from tenant_admin.ruc_review r
     order by r.created_at desc;
end;
$$;

create or replace function tenant_admin.resolve_ruc_review_for_session(
    p_session_token_hash text,
    p_review_id uuid,
    p_new_status varchar,
    p_reason varchar,
    p_release_ruc boolean
)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_session_id uuid;
begin
    perform tenant_admin.require_global_administrator(p_session_token_hash);
    select id into v_session_id
      from identity_auth.user_session
     where token_hash = p_session_token_hash and account_type = 'SAAS'
       and revoked_at is null and expires_at > current_timestamp;
    if v_session_id is null then
        raise exception 'global session is not authorized' using errcode = '42501';
    end if;
    return tenant_admin.resolve_ruc_review(v_session_id, p_review_id, p_new_status, p_reason, p_release_ruc);
end;
$$;

create or replace function tenant_admin.request_administrative_recovery(
    p_tenant_slug varchar,
    p_applicant_email varchar,
    p_normalized_applicant_email varchar,
    p_evidence_reference varchar
)
returns uuid
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
declare
    v_tenant_id uuid;
    v_recovery_id uuid := gen_random_uuid();
begin
    if coalesce(length(trim(p_normalized_applicant_email)), 0) = 0
       or coalesce(length(trim(p_evidence_reference)), 0) < 3 then
        raise exception 'invalid recovery request' using errcode = '22023';
    end if;
    select id into v_tenant_id from tenancy.tenant where slug = p_tenant_slug and status = 'ACTIVO';
    if v_tenant_id is null then
        return null;
    end if;
    insert into tenant_admin.administrative_recovery
        (id, tenant_id, applicant_email, normalized_applicant_email, evidence_reference, status)
    values (v_recovery_id, v_tenant_id, p_applicant_email, p_normalized_applicant_email, p_evidence_reference, 'SOLICITADA');
    return v_recovery_id;
end;
$$;

create or replace function tenant_admin.list_administrative_recoveries_for_session(p_session_token_hash text)
returns table (
    recovery_id uuid,
    tenant_id uuid,
    applicant_email varchar,
    normalized_applicant_email varchar,
    evidence_reference varchar,
    status varchar,
    decision_reason varchar,
    reviewed_by_principal_id uuid,
    reviewed_at timestamptz
)
language plpgsql
security definer
set search_path = pg_catalog, tenancy, identity_auth, tenant_admin, pg_temp
as $$
begin
    perform tenant_admin.require_global_administrator(p_session_token_hash);
    return query
    select r.id, r.tenant_id, r.applicant_email, r.normalized_applicant_email, r.evidence_reference,
           r.status, r.decision_reason, r.reviewed_by_principal_id, r.reviewed_at
      from tenant_admin.administrative_recovery r
     order by r.created_at desc;
end;
$$;

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
    select * into v_recovery from tenant_admin.administrative_recovery where id = p_recovery_id for update;
    if v_recovery.id is null or v_recovery.status in ('RECHAZADA', 'EJECUTADA') then
        raise exception 'recovery is not available' using errcode = '22023';
    end if;
    if p_new_status <> 'APROBADA' then
        update tenant_admin.administrative_recovery
           set status = p_new_status, decision_reason = p_reason, reviewed_by_principal_id = v_actor,
               reviewed_at = current_timestamp, updated_at = current_timestamp
         where id = v_recovery.id;
        return query select null::uuid, v_recovery.tenant_id, v_recovery.applicant_email, false;
        return;
    end if;
    if not p_legitimacy_checked or p_invitation_token_hash !~ '^[0-9a-f]{64}$'
       or p_invitation_expires_at <= current_timestamp
       or p_invitation_expires_at > current_timestamp + interval '24 hours' then
        raise exception 'approved recovery is not authorized' using errcode = '42501';
    end if;
    select status into v_tenant_status from tenancy.tenant where id = v_recovery.tenant_id for update;
    if v_tenant_status <> 'ACTIVO' then
        raise exception 'tenant is not eligible for recovery' using errcode = '22023';
    end if;
    -- The application confirmation is not a privilege by itself: the global
    -- session was derived above.  Persist the responsible administrator and
    -- reason before creating the limited invitation, so the approval remains
    -- auditable after this recovery reaches EJECUTADA.
    update tenant_admin.administrative_recovery
       set status = 'APROBADA', decision_reason = p_reason, reviewed_by_principal_id = v_actor,
           reviewed_at = current_timestamp, approval_attested_by_principal_id = v_actor,
           approval_attested_at = current_timestamp, updated_at = current_timestamp
     where id = v_recovery.id;
    insert into tenant_admin.audit_entry (id, tenant_id, actor_principal_id, action_code, resource_type, resource_id, outcome, reason)
    values (gen_random_uuid(), v_recovery.tenant_id, v_actor, 'ADMINISTRATIVE_RECOVERY_APPROVED',
            'ADMINISTRATIVE_RECOVERY', v_recovery.id, 'SUCCESS', p_reason);
    select principal_id into v_principal_id
      from identity_auth.account_profile
     where tenant_id = v_recovery.tenant_id and account_type = 'STAFF'
       and normalized_email = v_recovery.normalized_applicant_email
     for update;
    if v_principal_id is null then
        v_principal_id := gen_random_uuid();
        insert into identity_auth.principal (id, account_type, status)
        values (v_principal_id, 'STAFF', 'INVITADA');
        insert into identity_auth.account_profile (principal_id, tenant_id, account_type, normalized_email, status)
        values (v_principal_id, v_recovery.tenant_id, 'STAFF', v_recovery.normalized_applicant_email, 'INVITADA');
    elsif exists (select 1 from identity_auth.account_profile where principal_id = v_principal_id and status <> 'INVITADA') then
        raise exception 'recovery account is not eligible' using errcode = '22023';
    end if;
    update identity_auth.role_assignment
       set revoked_at = current_timestamp, revoked_by_principal_id = v_actor
     where tenant_id = v_recovery.tenant_id and staff_principal_id = v_principal_id and revoked_at is null;
    insert into identity_auth.role_assignment (id, tenant_id, staff_principal_id, role_code, assigned_by_principal_id)
    values (gen_random_uuid(), v_recovery.tenant_id, v_principal_id, 'ADMIN_VETERINARIA', v_actor)
    on conflict (tenant_id, staff_principal_id, role_code)
    do update set revoked_at = null, revoked_by_principal_id = null, assigned_by_principal_id = excluded.assigned_by_principal_id,
                  assigned_at = current_timestamp;
    update identity_auth.one_time_token set invalidated_at = current_timestamp
     where purpose = 'STAFF_INVITATION' and principal_id = v_principal_id and consumed_at is null and invalidated_at is null;
    insert into identity_auth.one_time_token (id, purpose, token_hash, principal_id, tenant_id, expires_at)
    values (gen_random_uuid(), 'STAFF_INVITATION', p_invitation_token_hash, v_principal_id, v_recovery.tenant_id, p_invitation_expires_at);
    update tenant_admin.administrative_recovery
       set status = 'EJECUTADA', decision_reason = p_reason, reviewed_by_principal_id = v_actor,
           reviewed_at = current_timestamp, updated_at = current_timestamp
     where id = v_recovery.id;
    insert into tenant_admin.notification_outbox (id, purpose, recipient_email, related_resource_id, status, delivered_at)
    values (gen_random_uuid(), 'STAFF_INVITATION', v_recovery.applicant_email, v_principal_id, 'ACCEPTED_LOCAL', current_timestamp);
    insert into tenant_admin.audit_entry (id, tenant_id, actor_principal_id, action_code, resource_type, resource_id, outcome, reason)
    values (gen_random_uuid(), v_recovery.tenant_id, v_actor, 'ADMINISTRATIVE_RECOVERY_EXECUTED',
            'ADMINISTRATIVE_RECOVERY', v_recovery.id, 'SUCCESS', p_reason);
    return query select v_principal_id, v_recovery.tenant_id, v_recovery.applicant_email, true;
end;
$$;

alter function tenant_admin.list_organizations_for_session(text) owner to av_schema_owner;
alter function tenant_admin.change_tenant_status_for_session(text, uuid, varchar, varchar) owner to av_schema_owner;
alter function tenant_admin.submit_ruc_review(varchar, varchar, varchar, varchar, varchar) owner to av_schema_owner;
alter function tenant_admin.list_ruc_reviews_for_session(text) owner to av_schema_owner;
alter function tenant_admin.resolve_ruc_review_for_session(text, uuid, varchar, varchar, boolean) owner to av_schema_owner;
alter function identity_auth.resolve_active_session_context(text) owner to av_schema_owner;
alter function identity_auth.is_csrf_token_valid(text, text) owner to av_schema_owner;
alter function tenant_admin.request_administrative_recovery(varchar, varchar, varchar, varchar) owner to av_schema_owner;
alter function tenant_admin.list_administrative_recoveries_for_session(text) owner to av_schema_owner;
alter function tenant_admin.review_administrative_recovery_for_session(text, uuid, varchar, varchar, boolean, text, timestamptz) owner to av_schema_owner;

revoke all on function tenant_admin.list_organizations_for_session(text) from public;
revoke all on function tenant_admin.change_tenant_status_for_session(text, uuid, varchar, varchar) from public;
revoke all on function tenant_admin.submit_ruc_review(varchar, varchar, varchar, varchar, varchar) from public;
revoke all on function tenant_admin.list_ruc_reviews_for_session(text) from public;
revoke all on function tenant_admin.resolve_ruc_review_for_session(text, uuid, varchar, varchar, boolean) from public;
revoke all on function identity_auth.resolve_active_session_context(text) from public;
revoke all on function identity_auth.is_csrf_token_valid(text, text) from public;
revoke all on function tenant_admin.request_administrative_recovery(varchar, varchar, varchar, varchar) from public;
revoke all on function tenant_admin.list_administrative_recoveries_for_session(text) from public;
revoke all on function tenant_admin.review_administrative_recovery_for_session(text, uuid, varchar, varchar, boolean, text, timestamptz) from public;

grant execute on function identity_auth.lookup_public_tenant(text) to av_app;
grant execute on function identity_auth.resolve_active_session_context(text) to av_app;
grant execute on function identity_auth.is_csrf_token_valid(text, text) to av_app;
grant execute on function tenant_admin.list_organizations_for_session(text) to av_app;
grant execute on function tenant_admin.change_tenant_status_for_session(text, uuid, varchar, varchar) to av_app;
grant execute on function tenant_admin.submit_ruc_review(varchar, varchar, varchar, varchar, varchar) to av_app;
grant execute on function tenant_admin.list_ruc_reviews_for_session(text) to av_app;
grant execute on function tenant_admin.resolve_ruc_review_for_session(text, uuid, varchar, varchar, boolean) to av_app;
grant execute on function tenant_admin.request_administrative_recovery(varchar, varchar, varchar, varchar) to av_app;
grant execute on function tenant_admin.list_administrative_recoveries_for_session(text) to av_app;
grant execute on function tenant_admin.review_administrative_recovery_for_session(text, uuid, varchar, varchar, boolean, text, timestamptz) to av_app;
