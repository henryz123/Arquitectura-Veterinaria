-- B03 schedule foundation. This migration is additive and transactional: it
-- introduces the tenant-local data needed for services, availability, requests,
-- proposals, effective reservations, grooming execution and audit history.
-- It intentionally creates no SECURITY DEFINER function, dynamic SQL, public
-- permission, elevated runtime role, extension or destructive alteration.

create schema if not exists appointment_management;

revoke all on schema appointment_management from public;
revoke create on schema appointment_management from av_app;
grant usage on schema appointment_management to av_app;

create table appointment_management.offered_service (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    service_type varchar(16) not null
        check (service_type in ('CLINICA', 'GROOMING')),
    name varchar(160) not null,
    public_description varchar(1000),
    duration_minutes integer not null,
    reference_price numeric(12, 2) not null default 0,
    published boolean not null default false,
    status varchar(16) not null default 'ACTIVO'
        check (status in ('ACTIVO', 'INACTIVO')),
    created_by_principal_id uuid not null,
    created_at timestamptz not null default statement_timestamp(),
    updated_at timestamptz not null default statement_timestamp(),
    constraint ck_offered_service_name
        check (char_length(btrim(name)) between 1 and 160),
    constraint ck_offered_service_description
        check (public_description is null or char_length(btrim(public_description)) between 1 and 1000),
    constraint ck_offered_service_duration
        check (duration_minutes between 15 and 480 and mod(duration_minutes, 15) = 0),
    constraint ck_offered_service_reference_price
        check (reference_price >= 0),
    constraint uq_offered_service_tenant_id unique (tenant_id, id),
    constraint fk_offered_service_actor_same_tenant
        foreign key (tenant_id, created_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create unique index uq_offered_service_tenant_type_name
    on appointment_management.offered_service (tenant_id, service_type, lower(btrim(name)));
create index ix_offered_service_tenant_visible
    on appointment_management.offered_service (tenant_id, service_type, status, published, name);

create table appointment_management.operating_hours (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    weekday smallint not null check (weekday between 1 and 7),
    starts_at_local time not null,
    ends_at_local time not null,
    created_by_principal_id uuid not null,
    created_at timestamptz not null default statement_timestamp(),
    updated_at timestamptz not null default statement_timestamp(),
    constraint ck_operating_hours_interval check (ends_at_local > starts_at_local),
    constraint uq_operating_hours_tenant_id unique (tenant_id, id),
    constraint fk_operating_hours_actor_same_tenant
        foreign key (tenant_id, created_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create index ix_operating_hours_tenant_weekday
    on appointment_management.operating_hours (tenant_id, weekday, starts_at_local, ends_at_local);

create table appointment_management.professional_availability (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    professional_principal_id uuid not null,
    weekday smallint not null check (weekday between 1 and 7),
    starts_at_local time not null,
    ends_at_local time not null,
    created_by_principal_id uuid not null,
    created_at timestamptz not null default statement_timestamp(),
    updated_at timestamptz not null default statement_timestamp(),
    constraint ck_professional_availability_interval check (ends_at_local > starts_at_local),
    constraint uq_professional_availability_tenant_id unique (tenant_id, id),
    constraint fk_professional_availability_professional_same_tenant
        foreign key (tenant_id, professional_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id),
    constraint fk_professional_availability_actor_same_tenant
        foreign key (tenant_id, created_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create index ix_professional_availability_tenant_professional_weekday
    on appointment_management.professional_availability
        (tenant_id, professional_principal_id, weekday, starts_at_local, ends_at_local);

create table appointment_management.schedule_block (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    block_scope varchar(16) not null
        check (block_scope in ('ORGANIZACION', 'PROFESIONAL')),
    professional_principal_id uuid,
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    reason varchar(500) not null,
    created_by_principal_id uuid not null,
    created_at timestamptz not null default statement_timestamp(),
    constraint ck_schedule_block_interval check (ends_at > starts_at),
    constraint ck_schedule_block_scope check (
        (block_scope = 'ORGANIZACION' and professional_principal_id is null)
        or (block_scope = 'PROFESIONAL' and professional_principal_id is not null)
    ),
    constraint ck_schedule_block_reason check (char_length(btrim(reason)) between 3 and 500),
    constraint uq_schedule_block_tenant_id unique (tenant_id, id),
    constraint fk_schedule_block_professional_same_tenant
        foreign key (tenant_id, professional_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id),
    constraint fk_schedule_block_actor_same_tenant
        foreign key (tenant_id, created_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create index ix_schedule_block_tenant_interval
    on appointment_management.schedule_block (tenant_id, starts_at, ends_at);
create index ix_schedule_block_tenant_professional_interval
    on appointment_management.schedule_block (tenant_id, professional_principal_id, starts_at, ends_at)
    where professional_principal_id is not null;

create table appointment_management.schedule_request (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    requester_principal_id uuid not null,
    owner_id uuid not null,
    pet_id uuid not null,
    service_id uuid not null,
    requested_starts_at timestamptz not null,
    requested_duration_minutes integer not null,
    status varchar(32) not null default 'PENDIENTE'
        check (status in ('PENDIENTE', 'PROPUESTA', 'CONFIRMADA', 'REPROGRAMACION_PROPUESTA',
                          'PENDIENTE_REPROGRAMACION', 'CANCELACION_SOLICITADA', 'EN_ATENCION',
                          'COMPLETADA', 'CANCELADA', 'INASISTENCIA', 'CERRADA')),
    observation varchar(1000),
    idempotency_key uuid not null,
    created_at timestamptz not null default statement_timestamp(),
    updated_at timestamptz not null default statement_timestamp(),
    constraint ck_schedule_request_duration
        check (requested_duration_minutes between 15 and 480 and mod(requested_duration_minutes, 15) = 0),
    constraint ck_schedule_request_observation
        check (observation is null or char_length(btrim(observation)) between 1 and 1000),
    constraint uq_schedule_request_tenant_id unique (tenant_id, id),
    constraint uq_schedule_request_idempotency unique (tenant_id, requester_principal_id, idempotency_key),
    constraint fk_schedule_request_requester_same_tenant
        foreign key (tenant_id, requester_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id),
    constraint fk_schedule_request_owner_same_tenant
        foreign key (tenant_id, owner_id)
        references owner_pet_care.owner_profile (tenant_id, id),
    constraint fk_schedule_request_pet_same_tenant
        foreign key (tenant_id, pet_id)
        references owner_pet_care.pet (tenant_id, id),
    constraint fk_schedule_request_service_same_tenant
        foreign key (tenant_id, service_id)
        references appointment_management.offered_service (tenant_id, id)
);

create index ix_schedule_request_tenant_status_starts
    on appointment_management.schedule_request (tenant_id, status, requested_starts_at, id);
create index ix_schedule_request_tenant_pet_created
    on appointment_management.schedule_request (tenant_id, pet_id, created_at desc, id desc);

create table appointment_management.schedule_proposal (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    request_id uuid not null,
    version integer not null,
    proposed_starts_at timestamptz not null,
    proposed_duration_minutes integer not null,
    proposed_professional_principal_id uuid,
    status varchar(16) not null default 'ENVIADA'
        check (status in ('ENVIADA', 'ACEPTADA', 'RECHAZADA', 'CERRADA')),
    created_by_principal_id uuid not null,
    created_at timestamptz not null default statement_timestamp(),
    decided_at timestamptz,
    constraint ck_schedule_proposal_version check (version > 0),
    constraint ck_schedule_proposal_duration
        check (proposed_duration_minutes between 15 and 480 and mod(proposed_duration_minutes, 15) = 0),
    constraint ck_schedule_proposal_decision check (
        (status = 'ENVIADA' and decided_at is null)
        or (status in ('ACEPTADA', 'RECHAZADA', 'CERRADA') and decided_at is not null)
    ),
    constraint uq_schedule_proposal_tenant_id unique (tenant_id, id),
    constraint uq_schedule_proposal_request_version unique (tenant_id, request_id, version),
    constraint fk_schedule_proposal_request_same_tenant
        foreign key (tenant_id, request_id)
        references appointment_management.schedule_request (tenant_id, id),
    constraint fk_schedule_proposal_professional_same_tenant
        foreign key (tenant_id, proposed_professional_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id),
    constraint fk_schedule_proposal_actor_same_tenant
        foreign key (tenant_id, created_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create index ix_schedule_proposal_tenant_request_status
    on appointment_management.schedule_proposal (tenant_id, request_id, status, version desc);

create table appointment_management.schedule_reservation (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    request_id uuid not null,
    professional_principal_id uuid not null,
    starts_at timestamptz not null,
    ends_at timestamptz not null,
    status varchar(16) not null default 'CONFIRMADA'
        check (status in ('CONFIRMADA', 'EN_ATENCION', 'COMPLETADA', 'INASISTENCIA', 'CANCELADA')),
    is_current boolean not null default true,
    version integer not null default 1,
    confirmed_by_principal_id uuid not null,
    created_at timestamptz not null default statement_timestamp(),
    updated_at timestamptz not null default statement_timestamp(),
    constraint ck_schedule_reservation_interval check (ends_at > starts_at),
    constraint ck_schedule_reservation_version check (version > 0),
    constraint uq_schedule_reservation_tenant_id unique (tenant_id, id),
    constraint fk_schedule_reservation_request_same_tenant
        foreign key (tenant_id, request_id)
        references appointment_management.schedule_request (tenant_id, id),
    constraint fk_schedule_reservation_professional_same_tenant
        foreign key (tenant_id, professional_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id),
    constraint fk_schedule_reservation_actor_same_tenant
        foreign key (tenant_id, confirmed_by_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create unique index uq_schedule_reservation_effective_request
    on appointment_management.schedule_reservation (tenant_id, request_id)
    where is_current;
create index ix_schedule_reservation_tenant_professional_interval
    on appointment_management.schedule_reservation (tenant_id, professional_principal_id, starts_at, ends_at)
    where is_current and status in ('CONFIRMADA', 'EN_ATENCION');
create index ix_schedule_reservation_tenant_status_starts
    on appointment_management.schedule_reservation (tenant_id, status, starts_at, id)
    where is_current;

create table appointment_management.schedule_change_event (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    request_id uuid not null,
    actor_principal_id uuid not null,
    action_code varchar(64) not null,
    previous_starts_at timestamptz,
    new_starts_at timestamptz,
    previous_professional_principal_id uuid,
    new_professional_principal_id uuid,
    reason varchar(500),
    notification_emitted_at timestamptz,
    created_at timestamptz not null default statement_timestamp(),
    constraint ck_schedule_change_event_action check (char_length(btrim(action_code)) between 3 and 64),
    constraint ck_schedule_change_event_reason
        check (reason is null or char_length(btrim(reason)) between 3 and 500),
    constraint uq_schedule_change_event_tenant_id unique (tenant_id, id),
    constraint fk_schedule_change_event_request_same_tenant
        foreign key (tenant_id, request_id)
        references appointment_management.schedule_request (tenant_id, id),
    constraint fk_schedule_change_event_actor_same_tenant
        foreign key (tenant_id, actor_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id),
    constraint fk_schedule_change_event_previous_professional_same_tenant
        foreign key (tenant_id, previous_professional_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id),
    constraint fk_schedule_change_event_new_professional_same_tenant
        foreign key (tenant_id, new_professional_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

create index ix_schedule_change_event_tenant_request_created
    on appointment_management.schedule_change_event (tenant_id, request_id, created_at desc, id desc);

create table appointment_management.grooming_attention (
    id uuid primary key default gen_random_uuid(),
    tenant_id uuid not null references tenancy.tenant(id),
    reservation_id uuid not null,
    groomer_principal_id uuid not null,
    started_at timestamptz,
    completed_at timestamptz,
    result varchar(1000),
    operational_notes varchar(2000),
    created_at timestamptz not null default statement_timestamp(),
    updated_at timestamptz not null default statement_timestamp(),
    constraint ck_grooming_attention_completed_after_start
        check (completed_at is null or (started_at is not null and completed_at >= started_at)),
    constraint ck_grooming_attention_result
        check (result is null or char_length(btrim(result)) between 1 and 1000),
    constraint ck_grooming_attention_notes
        check (operational_notes is null or char_length(btrim(operational_notes)) between 1 and 2000),
    constraint uq_grooming_attention_tenant_id unique (tenant_id, id),
    constraint uq_grooming_attention_reservation unique (tenant_id, reservation_id),
    constraint fk_grooming_attention_reservation_same_tenant
        foreign key (tenant_id, reservation_id)
        references appointment_management.schedule_reservation (tenant_id, id),
    constraint fk_grooming_attention_groomer_same_tenant
        foreign key (tenant_id, groomer_principal_id)
        references identity_auth.account_profile (tenant_id, principal_id)
);

alter table appointment_management.offered_service enable row level security;
alter table appointment_management.operating_hours enable row level security;
alter table appointment_management.professional_availability enable row level security;
alter table appointment_management.schedule_block enable row level security;
alter table appointment_management.schedule_request enable row level security;
alter table appointment_management.schedule_proposal enable row level security;
alter table appointment_management.schedule_reservation enable row level security;
alter table appointment_management.schedule_change_event enable row level security;
alter table appointment_management.grooming_attention enable row level security;

create policy offered_service_tenant_isolation on appointment_management.offered_service
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy operating_hours_tenant_isolation on appointment_management.operating_hours
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy professional_availability_tenant_isolation on appointment_management.professional_availability
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy schedule_block_tenant_isolation on appointment_management.schedule_block
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy schedule_request_tenant_isolation on appointment_management.schedule_request
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy schedule_proposal_tenant_isolation on appointment_management.schedule_proposal
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy schedule_reservation_tenant_isolation on appointment_management.schedule_reservation
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy schedule_change_event_tenant_isolation on appointment_management.schedule_change_event
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));
create policy grooming_attention_tenant_isolation on appointment_management.grooming_attention
    using (tenant_id::text = current_setting('app.tenant_id', true))
    with check (tenant_id::text = current_setting('app.tenant_id', true));

revoke all on all tables in schema appointment_management from public;
grant select, insert, update on appointment_management.offered_service to av_app;
grant select, insert, update on appointment_management.operating_hours to av_app;
grant select, insert, update on appointment_management.professional_availability to av_app;
grant select, insert, update on appointment_management.schedule_block to av_app;
grant select, insert, update on appointment_management.schedule_request to av_app;
grant select, insert, update on appointment_management.schedule_proposal to av_app;
grant select, insert, update on appointment_management.schedule_reservation to av_app;
grant select, insert on appointment_management.schedule_change_event to av_app;
grant select, insert, update on appointment_management.grooming_attention to av_app;
