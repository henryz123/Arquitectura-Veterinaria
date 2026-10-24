-- B02 clinical follow-up: vaccination events, basic prescriptions and
-- attributable amendments. This migration is additive and transactional. It
-- creates no SECURITY DEFINER routine, dynamic SQL, public permission, or
-- elevated runtime role.

-- The composite key lets dependent records prove that a referenced clinical
-- episode belongs to the same pet and tenant without a trigger.
alter table owner_pet_care.clinical_episode
    add constraint uq_clinical_episode_tenant_id_pet_id unique (tenant_id, id, pet_id);

create table owner_pet_care.vaccination_record (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    pet_id uuid not null,
    episode_id uuid,
    veterinarian_principal_id uuid not null,
    operation_id uuid not null,
    vaccine_name varchar(240) not null,
    administered_on date not null,
    dose varchar(240),
    batch_code varchar(240),
    next_due_on date,
    recorded_at timestamptz not null default statement_timestamp(),
    created_at timestamptz not null default statement_timestamp(),
    constraint uq_vaccination_record_tenant_id unique (tenant_id, id),
    constraint uq_vaccination_record_operation unique (tenant_id, operation_id),
    constraint ck_vaccination_record_name
        check (char_length(btrim(vaccine_name)) between 1 and 240),
    constraint ck_vaccination_record_next_due_on
        check (next_due_on is null or next_due_on >= administered_on),
    constraint fk_vaccination_record_pet_same_tenant
        foreign key (tenant_id, pet_id)
        references owner_pet_care.pet (tenant_id, id),
    constraint fk_vaccination_record_episode_pet_same_tenant
        foreign key (tenant_id, episode_id, pet_id)
        references owner_pet_care.clinical_episode (tenant_id, id, pet_id),
    constraint fk_vaccination_record_veterinarian_same_tenant
        foreign key (tenant_id, veterinarian_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create index ix_vaccination_record_tenant_pet_administered
    on owner_pet_care.vaccination_record (tenant_id, pet_id, administered_on desc, id desc);

create table owner_pet_care.clinical_prescription (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    pet_id uuid not null,
    episode_id uuid not null,
    veterinarian_principal_id uuid not null,
    medication varchar(500) not null,
    presentation varchar(500) not null,
    dose varchar(500) not null,
    route varchar(240) not null,
    frequency varchar(240) not null,
    duration varchar(240) not null,
    instructions text not null,
    prescribed_on date not null,
    created_at timestamptz not null default statement_timestamp(),
    constraint uq_clinical_prescription_tenant_id unique (tenant_id, id),
    constraint ck_clinical_prescription_medication check (char_length(btrim(medication)) between 1 and 500),
    constraint ck_clinical_prescription_presentation check (char_length(btrim(presentation)) between 1 and 500),
    constraint ck_clinical_prescription_dose check (char_length(btrim(dose)) between 1 and 500),
    constraint ck_clinical_prescription_route check (char_length(btrim(route)) between 1 and 240),
    constraint ck_clinical_prescription_frequency check (char_length(btrim(frequency)) between 1 and 240),
    constraint ck_clinical_prescription_duration check (char_length(btrim(duration)) between 1 and 240),
    constraint ck_clinical_prescription_instructions check (char_length(btrim(instructions)) between 1 and 12000),
    constraint fk_clinical_prescription_episode_pet_same_tenant
        foreign key (tenant_id, episode_id, pet_id)
        references owner_pet_care.clinical_episode (tenant_id, id, pet_id),
    constraint fk_clinical_prescription_veterinarian_same_tenant
        foreign key (tenant_id, veterinarian_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create index ix_clinical_prescription_tenant_episode_created
    on owner_pet_care.clinical_prescription (tenant_id, episode_id, created_at desc, id desc);

create table owner_pet_care.clinical_episode_amendment (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    pet_id uuid not null,
    episode_id uuid not null,
    veterinarian_principal_id uuid not null,
    reason varchar(500) not null,
    previous_content jsonb not null,
    addendum text not null,
    created_at timestamptz not null default statement_timestamp(),
    constraint uq_clinical_episode_amendment_tenant_id unique (tenant_id, id),
    constraint ck_clinical_episode_amendment_reason check (char_length(btrim(reason)) between 3 and 500),
    constraint ck_clinical_episode_amendment_previous_content_object check (jsonb_typeof(previous_content) = 'object'),
    constraint ck_clinical_episode_amendment_addendum check (char_length(btrim(addendum)) between 1 and 12000),
    constraint fk_clinical_episode_amendment_episode_pet_same_tenant
        foreign key (tenant_id, episode_id, pet_id)
        references owner_pet_care.clinical_episode (tenant_id, id, pet_id),
    constraint fk_clinical_episode_amendment_veterinarian_same_tenant
        foreign key (tenant_id, veterinarian_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create index ix_clinical_episode_amendment_tenant_episode_created
    on owner_pet_care.clinical_episode_amendment (tenant_id, episode_id, created_at desc, id desc);

alter table owner_pet_care.vaccination_record enable row level security;
alter table owner_pet_care.clinical_prescription enable row level security;
alter table owner_pet_care.clinical_episode_amendment enable row level security;

create policy vaccination_record_tenant_isolation on owner_pet_care.vaccination_record
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy clinical_prescription_tenant_isolation on owner_pet_care.clinical_prescription
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy clinical_episode_amendment_tenant_isolation on owner_pet_care.clinical_episode_amendment
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));

revoke all on owner_pet_care.vaccination_record from public;
revoke all on owner_pet_care.clinical_prescription from public;
revoke all on owner_pet_care.clinical_episode_amendment from public;
grant select, insert on owner_pet_care.vaccination_record to av_app;
grant select, insert on owner_pet_care.clinical_prescription to av_app;
grant select, insert on owner_pet_care.clinical_episode_amendment to av_app;
