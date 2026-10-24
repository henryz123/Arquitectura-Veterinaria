-- B02 first vertical: tenant-scoped owners, pets, current responsibility and
-- clinical episodes. This migration is intentionally transactional and
-- introduces no privileged routine or elevated runtime role.

create schema if not exists owner_pet_care;

revoke all on schema owner_pet_care from public;
revoke create on schema owner_pet_care from av_app;
grant usage on schema owner_pet_care to av_app;

-- Lets B02 foreign keys prove that the acting principal belongs to the same
-- tenant without granting av_app direct access to identity tables.
alter table identity_auth.account_profile
    add constraint uq_account_profile_tenant_principal unique (tenant_id, principal_id);

create table owner_pet_care.owner_profile (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    given_names varchar(120) not null,
    family_names varchar(120) not null,
    phone varchar(40),
    email varchar(254),
    address varchar(240),
    status varchar(16) not null default 'ACTIVO'
        check (status in ('ACTIVO', 'INACTIVO')),
    origin varchar(16) not null default 'PRESENCIAL'
        check (origin in ('PRESENCIAL', 'DIGITAL')),
    created_by_principal_id uuid not null,
    created_at timestamptz not null default statement_timestamp(),
    updated_at timestamptz not null default statement_timestamp(),
    constraint ck_owner_profile_given_names check (char_length(btrim(given_names)) between 1 and 120),
    constraint ck_owner_profile_family_names check (char_length(btrim(family_names)) between 1 and 120),
    constraint uq_owner_profile_tenant_id unique (tenant_id, id),
    constraint fk_owner_profile_actor_same_tenant
        foreign key (tenant_id, created_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create index ix_owner_profile_tenant_name
    on owner_pet_care.owner_profile (tenant_id, family_names, given_names);

create table owner_pet_care.pet (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    current_owner_id uuid not null,
    name varchar(120) not null,
    species varchar(80) not null,
    breed varchar(120),
    sex varchar(16) not null default 'DESCONOCIDO'
        check (sex in ('HEMBRA', 'MACHO', 'DESCONOCIDO')),
    birth_date date,
    estimated_age_months integer,
    distinguishing_marks varchar(500),
    status varchar(16) not null default 'ACTIVA'
        check (status in ('ACTIVA', 'FALLECIDA')),
    created_by_principal_id uuid not null,
    created_at timestamptz not null default statement_timestamp(),
    updated_at timestamptz not null default statement_timestamp(),
    constraint ck_pet_name check (char_length(btrim(name)) between 1 and 120),
    constraint ck_pet_species check (char_length(btrim(species)) between 1 and 80),
    constraint ck_pet_birth_or_estimated_age
        check ((birth_date is not null) <> (estimated_age_months is not null)),
    constraint ck_pet_estimated_age check (estimated_age_months is null or estimated_age_months >= 0),
    constraint uq_pet_tenant_id unique (tenant_id, id),
    constraint fk_pet_current_owner_same_tenant
        foreign key (tenant_id, current_owner_id)
        references owner_pet_care.owner_profile (tenant_id, id),
    constraint fk_pet_actor_same_tenant
        foreign key (tenant_id, created_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create index ix_pet_tenant_current_owner
    on owner_pet_care.pet (tenant_id, current_owner_id);
create index ix_pet_tenant_name
    on owner_pet_care.pet (tenant_id, name);

create table owner_pet_care.pet_ownership_period (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    pet_id uuid not null,
    owner_id uuid not null,
    effective_from timestamptz not null default statement_timestamp(),
    effective_until timestamptz,
    verified_by_principal_id uuid not null,
    reason varchar(500),
    created_at timestamptz not null default statement_timestamp(),
    constraint ck_pet_ownership_period_range
        check (effective_until is null or effective_until > effective_from),
    constraint uq_pet_ownership_period_tenant_id unique (tenant_id, id),
    constraint fk_pet_ownership_period_pet_same_tenant
        foreign key (tenant_id, pet_id)
        references owner_pet_care.pet (tenant_id, id),
    constraint fk_pet_ownership_period_owner_same_tenant
        foreign key (tenant_id, owner_id)
        references owner_pet_care.owner_profile (tenant_id, id),
    constraint fk_pet_ownership_period_actor_same_tenant
        foreign key (tenant_id, verified_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create unique index uq_pet_ownership_period_current
    on owner_pet_care.pet_ownership_period (tenant_id, pet_id)
    where effective_until is null;
create index ix_pet_ownership_period_tenant_owner
    on owner_pet_care.pet_ownership_period (tenant_id, owner_id, effective_until);

create table owner_pet_care.clinical_episode (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    pet_id uuid not null,
    veterinarian_principal_id uuid not null,
    appointment_id uuid,
    status varchar(16) not null default 'BORRADOR'
        check (status in ('BORRADOR', 'FINALIZADO')),
    reason text,
    anamnesis text,
    assessment text,
    diagnosis text,
    treatment_plan text,
    observations text,
    opened_at timestamptz not null default statement_timestamp(),
    finalized_at timestamptz,
    created_at timestamptz not null default statement_timestamp(),
    updated_at timestamptz not null default statement_timestamp(),
    constraint ck_clinical_episode_finalization
        check (
            (status = 'BORRADOR' and finalized_at is null)
            or (
                status = 'FINALIZADO'
                and finalized_at is not null
                and char_length(btrim(coalesce(reason, ''))) > 0
                and char_length(btrim(coalesce(assessment, ''))) > 0
            )
        ),
    constraint uq_clinical_episode_tenant_id unique (tenant_id, id),
    constraint fk_clinical_episode_pet_same_tenant
        foreign key (tenant_id, pet_id)
        references owner_pet_care.pet (tenant_id, id),
    constraint fk_clinical_episode_veterinarian_same_tenant
        foreign key (tenant_id, veterinarian_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create index ix_clinical_episode_tenant_pet_opened
    on owner_pet_care.clinical_episode (tenant_id, pet_id, opened_at desc);

create table owner_pet_care.care_audit_event (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    actor_principal_id uuid not null,
    action_code varchar(64) not null,
    resource_type varchar(64) not null,
    resource_id uuid not null,
    occurred_at timestamptz not null default statement_timestamp(),
    metadata jsonb not null default '{}'::jsonb,
    constraint ck_care_audit_metadata_object check (jsonb_typeof(metadata) = 'object'),
    constraint fk_care_audit_actor_same_tenant
        foreign key (tenant_id, actor_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create index ix_care_audit_tenant_resource
    on owner_pet_care.care_audit_event (tenant_id, resource_type, resource_id, occurred_at desc);

alter table owner_pet_care.owner_profile enable row level security;
alter table owner_pet_care.pet enable row level security;
alter table owner_pet_care.pet_ownership_period enable row level security;
alter table owner_pet_care.clinical_episode enable row level security;
alter table owner_pet_care.care_audit_event enable row level security;

create policy owner_profile_tenant_isolation on owner_pet_care.owner_profile
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy pet_tenant_isolation on owner_pet_care.pet
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy pet_ownership_period_tenant_isolation on owner_pet_care.pet_ownership_period
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy clinical_episode_tenant_isolation on owner_pet_care.clinical_episode
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy care_audit_event_tenant_isolation on owner_pet_care.care_audit_event
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));

revoke all on all tables in schema owner_pet_care from public;
grant select, insert, update on owner_pet_care.owner_profile to av_app;
grant select, insert, update on owner_pet_care.pet to av_app;
grant select, insert, update on owner_pet_care.pet_ownership_period to av_app;
grant select, insert, update on owner_pet_care.clinical_episode to av_app;
grant select, insert on owner_pet_care.care_audit_event to av_app;
