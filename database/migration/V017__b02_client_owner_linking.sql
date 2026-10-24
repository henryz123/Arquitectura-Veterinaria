-- B02 digital linking: a CLIENT requests review without learning whether a
-- matching in-person record exists. Registry staff resolve the request after
-- manual verification; the approved link is tenant-local and has one client
-- account and one local owner profile on each side.
--
-- This migration is additive, transactional, and introduces no privileged
-- routine, public permission, dynamic SQL, or access to credential hashes.

create table owner_pet_care.client_owner_link_request (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    request_reference varchar(24) not null default
        ('VINC-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 12))),
    client_principal_id uuid not null,
    status varchar(16) not null default 'PENDIENTE'
        check (status in ('PENDIENTE', 'APROBADA', 'RECHAZADA')),
    proposed_owner_id uuid,
    verification_method varchar(120),
    rejection_reason varchar(500),
    requested_at timestamptz not null default statement_timestamp(),
    resolved_at timestamptz,
    resolved_by_principal_id uuid,
    constraint uq_client_owner_link_request_reference unique (request_reference),
    constraint uq_client_owner_link_request_tenant_id unique (tenant_id, id),
    constraint fk_client_owner_link_request_client_same_tenant
        foreign key (tenant_id, client_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id),
    constraint fk_client_owner_link_request_owner_same_tenant
        foreign key (tenant_id, proposed_owner_id)
        references owner_pet_care.owner_profile (tenant_id, id),
    constraint fk_client_owner_link_request_resolver_same_tenant
        foreign key (tenant_id, resolved_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id),
    constraint ck_client_owner_link_request_resolution check (
        (status = 'PENDIENTE'
            and proposed_owner_id is null
            and verification_method is null
            and rejection_reason is null
            and resolved_at is null
            and resolved_by_principal_id is null)
        or (status = 'APROBADA'
            and proposed_owner_id is not null
            and char_length(btrim(coalesce(verification_method, ''))) between 3 and 120
            and rejection_reason is null
            and resolved_at is not null
            and resolved_by_principal_id is not null)
        or (status = 'RECHAZADA'
            and proposed_owner_id is null
            and char_length(btrim(coalesce(verification_method, ''))) between 3 and 120
            and char_length(btrim(coalesce(rejection_reason, ''))) between 3 and 500
            and resolved_at is not null
            and resolved_by_principal_id is not null)
    )
);

create unique index uq_client_owner_link_request_pending_client
    on owner_pet_care.client_owner_link_request (tenant_id, client_principal_id)
    where status = 'PENDIENTE';
create index ix_client_owner_link_request_tenant_status_requested
    on owner_pet_care.client_owner_link_request (tenant_id, status, requested_at, id);

create table owner_pet_care.client_owner_link (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    client_principal_id uuid not null,
    owner_id uuid not null,
    source_request_id uuid not null,
    approved_by_principal_id uuid not null,
    approved_at timestamptz not null default statement_timestamp(),
    constraint uq_client_owner_link_tenant_id unique (tenant_id, id),
    constraint uq_client_owner_link_client unique (tenant_id, client_principal_id),
    constraint uq_client_owner_link_owner unique (tenant_id, owner_id),
    constraint uq_client_owner_link_request unique (tenant_id, source_request_id),
    constraint fk_client_owner_link_client_same_tenant
        foreign key (tenant_id, client_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id),
    constraint fk_client_owner_link_owner_same_tenant
        foreign key (tenant_id, owner_id)
        references owner_pet_care.owner_profile (tenant_id, id),
    constraint fk_client_owner_link_request_same_tenant
        foreign key (tenant_id, source_request_id)
        references owner_pet_care.client_owner_link_request (tenant_id, id),
    constraint fk_client_owner_link_approver_same_tenant
        foreign key (tenant_id, approved_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create index ix_client_owner_link_tenant_owner
    on owner_pet_care.client_owner_link (tenant_id, owner_id);

alter table owner_pet_care.client_owner_link_request enable row level security;
alter table owner_pet_care.client_owner_link enable row level security;

create policy client_owner_link_request_tenant_isolation on owner_pet_care.client_owner_link_request
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy client_owner_link_tenant_isolation on owner_pet_care.client_owner_link
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));

revoke all on owner_pet_care.client_owner_link_request from public;
revoke all on owner_pet_care.client_owner_link from public;
grant select, insert, update on owner_pet_care.client_owner_link_request to av_app;
grant select, insert on owner_pet_care.client_owner_link to av_app;
