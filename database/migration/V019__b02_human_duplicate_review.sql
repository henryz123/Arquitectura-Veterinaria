-- B02 human duplicate review. The review case, its veterinary evaluation and
-- its resolution are append-only facts. No migration merges records, changes
-- identifiers, deletes evidence, or grants UPDATE/DELETE to av_app.

alter table owner_pet_care.pet
    drop constraint pet_status_check,
    add constraint pet_status_check
        check (status in ('ACTIVA', 'FALLECIDA', 'INACTIVA'));

create table owner_pet_care.owner_duplicate_review (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    duplicate_owner_id uuid not null,
    reference_owner_id uuid not null,
    reported_by_principal_id uuid not null,
    reported_at timestamptz not null default statement_timestamp(),
    constraint ck_owner_duplicate_review_distinct_records
        check (duplicate_owner_id <> reference_owner_id),
    constraint uq_owner_duplicate_review_tenant_id unique (tenant_id, id),
    constraint fk_owner_duplicate_review_duplicate_same_tenant
        foreign key (tenant_id, duplicate_owner_id)
        references owner_pet_care.owner_profile (tenant_id, id),
    constraint fk_owner_duplicate_review_reference_same_tenant
        foreign key (tenant_id, reference_owner_id)
        references owner_pet_care.owner_profile (tenant_id, id),
    constraint fk_owner_duplicate_review_reporter_same_tenant
        foreign key (tenant_id, reported_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create unique index uq_owner_duplicate_review_pair
    on owner_pet_care.owner_duplicate_review (
        tenant_id, least(duplicate_owner_id, reference_owner_id), greatest(duplicate_owner_id, reference_owner_id)
    );
create index ix_owner_duplicate_review_tenant_reported
    on owner_pet_care.owner_duplicate_review (tenant_id, reported_at, id);

create table owner_pet_care.owner_duplicate_resolution (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null,
    review_id uuid not null,
    outcome varchar(32) not null
        check (outcome in ('DESCARTADA', 'CONFIRMADA_INACTIVA')),
    resolved_by_principal_id uuid not null,
    resolved_at timestamptz not null default statement_timestamp(),
    resolution_reason varchar(500) not null,
    constraint ck_owner_duplicate_resolution_reason
        check (char_length(btrim(resolution_reason)) between 3 and 500),
    constraint uq_owner_duplicate_resolution_review unique (tenant_id, review_id),
    constraint fk_owner_duplicate_resolution_review_same_tenant
        foreign key (tenant_id, review_id)
        references owner_pet_care.owner_duplicate_review (tenant_id, id),
    constraint fk_owner_duplicate_resolution_reviewer_same_tenant
        foreign key (tenant_id, resolved_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create table owner_pet_care.pet_duplicate_review (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    duplicate_pet_id uuid not null,
    reference_pet_id uuid not null,
    reported_by_principal_id uuid not null,
    reported_at timestamptz not null default statement_timestamp(),
    constraint ck_pet_duplicate_review_distinct_records
        check (duplicate_pet_id <> reference_pet_id),
    constraint uq_pet_duplicate_review_tenant_id unique (tenant_id, id),
    constraint fk_pet_duplicate_review_duplicate_same_tenant
        foreign key (tenant_id, duplicate_pet_id)
        references owner_pet_care.pet (tenant_id, id),
    constraint fk_pet_duplicate_review_reference_same_tenant
        foreign key (tenant_id, reference_pet_id)
        references owner_pet_care.pet (tenant_id, id),
    constraint fk_pet_duplicate_review_reporter_same_tenant
        foreign key (tenant_id, reported_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create unique index uq_pet_duplicate_review_pair
    on owner_pet_care.pet_duplicate_review (
        tenant_id, least(duplicate_pet_id, reference_pet_id), greatest(duplicate_pet_id, reference_pet_id)
    );
create index ix_pet_duplicate_review_tenant_reported
    on owner_pet_care.pet_duplicate_review (tenant_id, reported_at, id);

create table owner_pet_care.pet_duplicate_clinical_evaluation (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null,
    review_id uuid not null,
    evaluated_by_principal_id uuid not null,
    evaluated_at timestamptz not null default statement_timestamp(),
    constraint uq_pet_duplicate_clinical_evaluation_review unique (tenant_id, review_id),
    constraint fk_pet_duplicate_clinical_evaluation_review_same_tenant
        foreign key (tenant_id, review_id)
        references owner_pet_care.pet_duplicate_review (tenant_id, id),
    constraint fk_pet_duplicate_clinical_evaluation_actor_same_tenant
        foreign key (tenant_id, evaluated_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create table owner_pet_care.pet_duplicate_resolution (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null,
    review_id uuid not null,
    outcome varchar(32) not null
        check (outcome in ('DESCARTADA', 'CONFIRMADA_INACTIVA')),
    resolved_by_principal_id uuid not null,
    resolved_at timestamptz not null default statement_timestamp(),
    resolution_reason varchar(500) not null,
    constraint ck_pet_duplicate_resolution_reason
        check (char_length(btrim(resolution_reason)) between 3 and 500),
    constraint uq_pet_duplicate_resolution_review unique (tenant_id, review_id),
    constraint fk_pet_duplicate_resolution_review_same_tenant
        foreign key (tenant_id, review_id)
        references owner_pet_care.pet_duplicate_review (tenant_id, id),
    constraint fk_pet_duplicate_resolution_reviewer_same_tenant
        foreign key (tenant_id, resolved_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

alter table owner_pet_care.owner_duplicate_review enable row level security;
alter table owner_pet_care.owner_duplicate_resolution enable row level security;
alter table owner_pet_care.pet_duplicate_review enable row level security;
alter table owner_pet_care.pet_duplicate_clinical_evaluation enable row level security;
alter table owner_pet_care.pet_duplicate_resolution enable row level security;

create policy owner_duplicate_review_tenant_isolation on owner_pet_care.owner_duplicate_review
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy owner_duplicate_resolution_tenant_isolation on owner_pet_care.owner_duplicate_resolution
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy pet_duplicate_review_tenant_isolation on owner_pet_care.pet_duplicate_review
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy pet_duplicate_clinical_evaluation_tenant_isolation on owner_pet_care.pet_duplicate_clinical_evaluation
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy pet_duplicate_resolution_tenant_isolation on owner_pet_care.pet_duplicate_resolution
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));

revoke all on owner_pet_care.owner_duplicate_review from public;
revoke all on owner_pet_care.owner_duplicate_resolution from public;
revoke all on owner_pet_care.pet_duplicate_review from public;
revoke all on owner_pet_care.pet_duplicate_clinical_evaluation from public;
revoke all on owner_pet_care.pet_duplicate_resolution from public;
grant select, insert on owner_pet_care.owner_duplicate_review to av_app;
grant select, insert on owner_pet_care.owner_duplicate_resolution to av_app;
grant select, insert on owner_pet_care.pet_duplicate_review to av_app;
grant select, insert on owner_pet_care.pet_duplicate_clinical_evaluation to av_app;
grant select, insert on owner_pet_care.pet_duplicate_resolution to av_app;
