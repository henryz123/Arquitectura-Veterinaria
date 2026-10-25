package pe.arquitecturaveterinaria.ownerpetcare.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC adapter for B02. Every caller establishes the trusted tenant context
 * before invoking this adapter; PostgreSQL RLS then enforces the same scope.
 */
@Repository
public class OwnerPetCareJdbcRepository {

	private final JdbcTemplate jdbcTemplate;

	public OwnerPetCareJdbcRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public OwnerProfile createOwner(UUID tenantId, UUID actorPrincipalId, OwnerDraft draft) {
		return jdbcTemplate.queryForObject("""
				insert into owner_pet_care.owner_profile (
				    tenant_id, given_names, family_names, phone, email, address, origin, created_by_principal_id
				) values (?, ?, ?, ?, ?, ?, ?, ?)
				returning id, tenant_id, given_names, family_names, phone, email, address, status, origin, created_at, updated_at
				""", this::mapOwner, tenantId, draft.givenNames(), draft.familyNames(), draft.phone(), draft.email(),
				draft.address(), draft.origin(), actorPrincipalId);
	}

	public List<OwnerProfile> listOwners(String query) {
		String pattern = query == null ? "" : "%" + query.toLowerCase() + "%";
		return jdbcTemplate.query("""
				select id, tenant_id, given_names, family_names, phone, email, address, status, origin, created_at, updated_at
				from owner_pet_care.owner_profile
				where (? = '' or lower(given_names) like ? or lower(family_names) like ? or lower(coalesce(phone, '')) like ?)
				order by family_names, given_names, created_at
				limit 100
				""", this::mapOwner, pattern, pattern, pattern, pattern);
	}

	public Optional<OwnerProfile> findOwner(UUID ownerId) {
		return jdbcTemplate.query("""
				select id, tenant_id, given_names, family_names, phone, email, address, status, origin, created_at, updated_at
				from owner_pet_care.owner_profile
				where id = ?
				""", this::mapOwner, ownerId).stream().findFirst();
	}

	/** Locks one tenant-visible owner while an operation changes a responsibility or status. */
	public Optional<OwnerProfile> lockOwner(UUID ownerId) {
		return jdbcTemplate.query("""
				select id, tenant_id, given_names, family_names, phone, email, address, status, origin, created_at, updated_at
				from owner_pet_care.owner_profile
				where id = ?
				for update
				""", this::mapOwner, ownerId).stream().findFirst();
	}

	public Optional<OwnerProfile> findOwnerForClient(UUID clientPrincipalId) {
		return jdbcTemplate.query("""
				select owner.id, owner.tenant_id, owner.given_names, owner.family_names, owner.phone, owner.email,
				       owner.address, owner.status, owner.origin, owner.created_at, owner.updated_at
				from owner_pet_care.owner_profile owner
				join owner_pet_care.client_owner_link link
				  on link.tenant_id = owner.tenant_id and link.owner_id = owner.id
				where link.client_principal_id = ?
				""", this::mapOwner, clientPrincipalId).stream().findFirst();
	}

	/** Updates only the editable owner profile fields in the trusted tenant context. */
	public Optional<OwnerProfile> updateOwner(UUID tenantId, UUID ownerId, OwnerDraft draft) {
		return jdbcTemplate.query("""
				update owner_pet_care.owner_profile
				set given_names = ?, family_names = ?, phone = ?, email = ?, address = ?, updated_at = statement_timestamp()
				where tenant_id = ? and id = ?
				returning id, tenant_id, given_names, family_names, phone, email, address, status, origin, created_at, updated_at
				""", this::mapOwner, draft.givenNames(), draft.familyNames(), draft.phone(), draft.email(), draft.address(),
				tenantId, ownerId).stream().findFirst();
	}

	public Optional<OwnerProfile> updateOwnerForClient(UUID tenantId, UUID clientPrincipalId, UUID ownerId, OwnerDraft draft) {
		return jdbcTemplate.query("""
				update owner_pet_care.owner_profile owner
				set given_names = ?, family_names = ?, phone = ?, email = ?, address = ?, updated_at = statement_timestamp()
				from owner_pet_care.client_owner_link link
				where owner.tenant_id = ? and owner.id = ?
				  and link.tenant_id = owner.tenant_id
				  and link.owner_id = owner.id
				  and link.client_principal_id = ?
				returning owner.id, owner.tenant_id, owner.given_names, owner.family_names, owner.phone, owner.email,
				          owner.address, owner.status, owner.origin, owner.created_at, owner.updated_at
				""", this::mapOwner, draft.givenNames(), draft.familyNames(), draft.phone(), draft.email(), draft.address(),
				tenantId, ownerId, clientPrincipalId).stream().findFirst();
	}

	public PetProfile createPet(UUID tenantId, UUID actorPrincipalId, UUID ownerId, PetDraft draft) {
		return jdbcTemplate.queryForObject("""
				insert into owner_pet_care.pet (
				    tenant_id, current_owner_id, name, species, breed, sex, birth_date, estimated_age_months,
				    distinguishing_marks, created_by_principal_id
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				returning id, tenant_id, current_owner_id, name, species, breed, sex, birth_date, estimated_age_months,
				          distinguishing_marks, status, created_at, updated_at
				""", this::mapPet, tenantId, ownerId, draft.name(), draft.species(), draft.breed(), draft.sex(),
				draft.birthDate(), draft.estimatedAgeMonths(), draft.distinguishingMarks(), actorPrincipalId);
	}

	public ClientOwnerLinkRequest createClientOwnerLinkRequest(UUID tenantId, UUID clientPrincipalId) {
		return jdbcTemplate.queryForObject("""
				insert into owner_pet_care.client_owner_link_request (tenant_id, client_principal_id)
				values (?, ?)
				returning id, tenant_id, request_reference, client_principal_id, status, proposed_owner_id,
				          verification_method, rejection_reason, requested_at, resolved_at, resolved_by_principal_id
				""", this::mapClientOwnerLinkRequest, tenantId, clientPrincipalId);
	}

	public Optional<ClientOwnerLinkRequest> findLatestClientOwnerLinkRequest(UUID clientPrincipalId) {
		return jdbcTemplate.query("""
				select id, tenant_id, request_reference, client_principal_id, status, proposed_owner_id,
				       verification_method, rejection_reason, requested_at, resolved_at, resolved_by_principal_id
				from owner_pet_care.client_owner_link_request
				where client_principal_id = ?
				order by requested_at desc, id desc
				limit 1
				""", this::mapClientOwnerLinkRequest, clientPrincipalId).stream().findFirst();
	}

	public Optional<ClientOwnerLinkRequest> findPendingClientOwnerLinkRequest(UUID clientPrincipalId) {
		return jdbcTemplate.query("""
				select id, tenant_id, request_reference, client_principal_id, status, proposed_owner_id,
				       verification_method, rejection_reason, requested_at, resolved_at, resolved_by_principal_id
				from owner_pet_care.client_owner_link_request
				where client_principal_id = ? and status = 'PENDIENTE'
				""", this::mapClientOwnerLinkRequest, clientPrincipalId).stream().findFirst();
	}

	public List<ClientOwnerLinkRequest> listClientOwnerLinkRequests(String status) {
		return jdbcTemplate.query("""
				select id, tenant_id, request_reference, client_principal_id, status, proposed_owner_id,
				       verification_method, rejection_reason, requested_at, resolved_at, resolved_by_principal_id
				from owner_pet_care.client_owner_link_request
				where (? is null or status = ?)
				order by requested_at, id
				limit 100
				""", this::mapClientOwnerLinkRequest, status, status);
	}

	public Optional<ClientOwnerLinkRequest> lockClientOwnerLinkRequest(UUID requestId) {
		return jdbcTemplate.query("""
				select id, tenant_id, request_reference, client_principal_id, status, proposed_owner_id,
				       verification_method, rejection_reason, requested_at, resolved_at, resolved_by_principal_id
				from owner_pet_care.client_owner_link_request
				where id = ?
				for update
				""", this::mapClientOwnerLinkRequest, requestId).stream().findFirst();
	}

	public Optional<ClientOwnerLink> findClientOwnerLink(UUID clientPrincipalId) {
		return jdbcTemplate.query("""
				select id, tenant_id, client_principal_id, owner_id, source_request_id, approved_by_principal_id, approved_at
				from owner_pet_care.client_owner_link
				where client_principal_id = ?
				""", this::mapClientOwnerLink, clientPrincipalId).stream().findFirst();
	}

	public ClientOwnerLink createClientOwnerLink(UUID tenantId, UUID clientPrincipalId, UUID ownerId, UUID sourceRequestId,
			UUID approvedByPrincipalId) {
		return jdbcTemplate.queryForObject("""
				insert into owner_pet_care.client_owner_link (
				    tenant_id, client_principal_id, owner_id, source_request_id, approved_by_principal_id
				) values (?, ?, ?, ?, ?)
				returning id, tenant_id, client_principal_id, owner_id, source_request_id, approved_by_principal_id, approved_at
				""", this::mapClientOwnerLink, tenantId, clientPrincipalId, ownerId, sourceRequestId, approvedByPrincipalId);
	}

	public Optional<ClientOwnerLinkRequest> resolveClientOwnerLinkRequest(UUID requestId, String status, UUID ownerId,
			String verificationMethod, String rejectionReason, UUID resolverPrincipalId) {
		return jdbcTemplate.query("""
				update owner_pet_care.client_owner_link_request
				set status = ?, proposed_owner_id = ?, verification_method = ?, rejection_reason = ?,
				    resolved_at = statement_timestamp(), resolved_by_principal_id = ?
				where id = ? and status = 'PENDIENTE'
				returning id, tenant_id, request_reference, client_principal_id, status, proposed_owner_id,
				          verification_method, rejection_reason, requested_at, resolved_at, resolved_by_principal_id
				""", this::mapClientOwnerLinkRequest, status, ownerId, verificationMethod, rejectionReason,
				resolverPrincipalId, requestId).stream().findFirst();
	}

	public void createInitialOwnership(UUID tenantId, UUID petId, UUID ownerId, UUID actorPrincipalId) {
		createInitialOwnership(tenantId, petId, ownerId, actorPrincipalId, "Alta de mascota");
	}

	public void createInitialOwnership(UUID tenantId, UUID petId, UUID ownerId, UUID actorPrincipalId, String reason) {
		jdbcTemplate.update("""
				insert into owner_pet_care.pet_ownership_period (
				    tenant_id, pet_id, owner_id, verified_by_principal_id, reason
				) values (?, ?, ?, ?, ?)
				""", tenantId, petId, ownerId, actorPrincipalId, reason);
	}

	public Optional<PetProfile> findPet(UUID petId) {
		return jdbcTemplate.query("""
				select id, tenant_id, current_owner_id, name, species, breed, sex, birth_date, estimated_age_months,
				       distinguishing_marks, status, created_at, updated_at
				from owner_pet_care.pet
				where id = ?
				""", this::mapPet, petId).stream().findFirst();
	}

	public Optional<PetProfile> lockPet(UUID petId) {
		return jdbcTemplate.query("""
				select id, tenant_id, current_owner_id, name, species, breed, sex, birth_date, estimated_age_months,
				       distinguishing_marks, status, created_at, updated_at
				from owner_pet_care.pet
				where id = ?
				for update
				""", this::mapPet, petId).stream().findFirst();
	}

	public List<DuplicateCandidate> findPotentialOwnerDuplicates(UUID ownerId) {
		return jdbcTemplate.query("""
				with source as (
				    select id, given_names, family_names, phone, email
				    from owner_pet_care.owner_profile
				    where id = ? and status = 'ACTIVO'
				)
				select candidate.id,
				       candidate.family_names || ', ' || candidate.given_names as label,
				       case
				           when source.phone is not null and candidate.phone = source.phone then 'Teléfono coincidente'
				           when source.email is not null and lower(candidate.email) = lower(source.email) then 'Correo coincidente'
				           else 'Nombres y apellidos coincidentes'
				       end as match_reason
				from source
				join owner_pet_care.owner_profile candidate on candidate.id <> source.id
				where candidate.status = 'ACTIVO'
				  and (
				      (lower(candidate.given_names) = lower(source.given_names)
				       and lower(candidate.family_names) = lower(source.family_names))
				      or (source.phone is not null and candidate.phone = source.phone)
				      or (source.email is not null and lower(candidate.email) = lower(source.email))
				  )
				order by candidate.family_names, candidate.given_names, candidate.id
				limit 20
				""", this::mapDuplicateCandidate, ownerId);
	}

	public List<DuplicateCandidate> findPotentialPetDuplicates(UUID petId) {
		return jdbcTemplate.query("""
				with source as (
				    select id, name, species, birth_date, estimated_age_months
				    from owner_pet_care.pet
				    where id = ? and status = 'ACTIVA'
				)
				select candidate.id,
				       candidate.name || ' · ' || candidate.species as label,
				       case
				           when source.birth_date is not null and candidate.birth_date = source.birth_date
				               then 'Nombre, especie y fecha de nacimiento coincidentes'
				           else 'Nombre, especie y edad estimada coincidentes'
				       end as match_reason
				from source
				join owner_pet_care.pet candidate on candidate.id <> source.id
				where candidate.status = 'ACTIVA'
				  and lower(candidate.name) = lower(source.name)
				  and lower(candidate.species) = lower(source.species)
				  and (
				      (source.birth_date is not null and candidate.birth_date = source.birth_date)
				      or (source.estimated_age_months is not null
				          and candidate.estimated_age_months = source.estimated_age_months)
				  )
				order by candidate.name, candidate.id
				limit 20
				""", this::mapDuplicateCandidate, petId);
	}

	public Optional<OwnerDuplicateReview> createOwnerDuplicateReview(UUID tenantId, UUID actorPrincipalId, UUID duplicateOwnerId,
			UUID referenceOwnerId) {
		return jdbcTemplate.query("""
				insert into owner_pet_care.owner_duplicate_review (
				    tenant_id, duplicate_owner_id, reference_owner_id, reported_by_principal_id
				) values (?, ?, ?, ?)
				on conflict do nothing
				returning id, duplicate_owner_id, reference_owner_id, reported_at
				""", this::mapCreatedOwnerDuplicateReview, tenantId, duplicateOwnerId, referenceOwnerId, actorPrincipalId)
				.stream().findFirst();
	}

	/**
	 * Serializes the two append-only review decisions inside the current JDBC
	 * transaction. It avoids granting UPDATE merely to obtain a row lock.
	 */
	public void lockDuplicateReviewOperation(UUID reviewId) {
		jdbcTemplate.query("select pg_advisory_xact_lock(hashtextextended(?::text, 29019))", resultSet -> { },
				reviewId.toString());
	}

	public Optional<OwnerDuplicateReview> lockOwnerDuplicateReview(UUID reviewId) {
		return jdbcTemplate.query("""
				select review.id, review.duplicate_owner_id, review.reference_owner_id, review.reported_at,
				       null::text as duplicate_label, null::text as reference_label,
				       resolution.outcome, resolution.resolved_at
				from owner_pet_care.owner_duplicate_review review
				left join owner_pet_care.owner_duplicate_resolution resolution
				  on resolution.tenant_id = review.tenant_id and resolution.review_id = review.id
				where review.id = ?
				""", this::mapOwnerDuplicateReview, reviewId).stream().findFirst();
	}

	public List<OwnerDuplicateReview> listOwnerDuplicateReviews() {
		return jdbcTemplate.query("""
				select review.id, review.duplicate_owner_id, review.reference_owner_id, review.reported_at,
				       duplicate_owner.family_names || ', ' || duplicate_owner.given_names as duplicate_label,
				       reference_owner.family_names || ', ' || reference_owner.given_names as reference_label,
				       resolution.outcome, resolution.resolved_at
				from owner_pet_care.owner_duplicate_review review
				join owner_pet_care.owner_profile duplicate_owner
				  on duplicate_owner.tenant_id = review.tenant_id and duplicate_owner.id = review.duplicate_owner_id
				join owner_pet_care.owner_profile reference_owner
				  on reference_owner.tenant_id = review.tenant_id and reference_owner.id = review.reference_owner_id
				left join owner_pet_care.owner_duplicate_resolution resolution
				  on resolution.tenant_id = review.tenant_id and resolution.review_id = review.id
				order by review.reported_at desc, review.id desc
				""", this::mapOwnerDuplicateReview);
	}

	public boolean ownerHasActiveResponsibilities(UUID ownerId) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
				select exists (
				    select 1 from owner_pet_care.pet where current_owner_id = ? and status = 'ACTIVA'
				)
				""", Boolean.class, ownerId));
	}

	public boolean ownerHasApprovedClientLink(UUID ownerId) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
				select exists (select 1 from owner_pet_care.client_owner_link where owner_id = ?)
				""", Boolean.class, ownerId));
	}

	public int deactivateOwnerIfActive(UUID tenantId, UUID ownerId) {
		return jdbcTemplate.update("""
				update owner_pet_care.owner_profile
				set status = 'INACTIVO', updated_at = statement_timestamp()
				where tenant_id = ? and id = ? and status = 'ACTIVO'
				""", tenantId, ownerId);
	}

	public boolean createOwnerDuplicateResolution(UUID tenantId, UUID reviewId, String outcome, UUID actorPrincipalId,
			String reason) {
		return jdbcTemplate.update("""
				insert into owner_pet_care.owner_duplicate_resolution (
				    tenant_id, review_id, outcome, resolved_by_principal_id, resolution_reason
				) values (?, ?, ?, ?, ?)
				on conflict do nothing
				""", tenantId, reviewId, outcome, actorPrincipalId, reason) == 1;
	}

	public Optional<PetDuplicateReview> createPetDuplicateReview(UUID tenantId, UUID actorPrincipalId, UUID duplicatePetId,
			UUID referencePetId) {
		return jdbcTemplate.query("""
				insert into owner_pet_care.pet_duplicate_review (
				    tenant_id, duplicate_pet_id, reference_pet_id, reported_by_principal_id
				) values (?, ?, ?, ?)
				on conflict do nothing
				returning id, duplicate_pet_id, reference_pet_id, reported_at
				""", this::mapCreatedPetDuplicateReview, tenantId, duplicatePetId, referencePetId, actorPrincipalId)
				.stream().findFirst();
	}

	public Optional<PetDuplicateReview> lockPetDuplicateReview(UUID reviewId) {
		return jdbcTemplate.query("""
				select review.id, review.duplicate_pet_id, review.reference_pet_id, review.reported_at,
				       null::text as duplicate_label, null::text as reference_label,
				       resolution.outcome, resolution.resolved_at, evaluation.evaluated_at,
				       false as requires_clinical_evaluation
				from owner_pet_care.pet_duplicate_review review
				left join owner_pet_care.pet_duplicate_resolution resolution
				  on resolution.tenant_id = review.tenant_id and resolution.review_id = review.id
				left join owner_pet_care.pet_duplicate_clinical_evaluation evaluation
				  on evaluation.tenant_id = review.tenant_id and evaluation.review_id = review.id
				where review.id = ?
				""", this::mapPetDuplicateReview, reviewId).stream().findFirst();
	}

	public List<PetDuplicateReview> listPetDuplicateReviews() {
		return jdbcTemplate.query("""
				select review.id, review.duplicate_pet_id, review.reference_pet_id, review.reported_at,
				       duplicate_pet.name || ' · ' || duplicate_pet.species as duplicate_label,
				       reference_pet.name || ' · ' || reference_pet.species as reference_label,
				       resolution.outcome, resolution.resolved_at, evaluation.evaluated_at,
				       exists (select 1 from owner_pet_care.clinical_episode episode
				               where episode.tenant_id = review.tenant_id and episode.pet_id = review.duplicate_pet_id) as requires_clinical_evaluation
				from owner_pet_care.pet_duplicate_review review
				join owner_pet_care.pet duplicate_pet
				  on duplicate_pet.tenant_id = review.tenant_id and duplicate_pet.id = review.duplicate_pet_id
				join owner_pet_care.pet reference_pet
				  on reference_pet.tenant_id = review.tenant_id and reference_pet.id = review.reference_pet_id
				left join owner_pet_care.pet_duplicate_resolution resolution
				  on resolution.tenant_id = review.tenant_id and resolution.review_id = review.id
				left join owner_pet_care.pet_duplicate_clinical_evaluation evaluation
				  on evaluation.tenant_id = review.tenant_id and evaluation.review_id = review.id
				order by review.reported_at desc, review.id desc
				""", this::mapPetDuplicateReview);
	}

	public boolean petHasClinicalData(UUID petId) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
				select exists (select 1 from owner_pet_care.clinical_episode where pet_id = ?)
				""", Boolean.class, petId));
	}

	public boolean hasPetDuplicateClinicalEvaluation(UUID reviewId) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
				select exists (select 1 from owner_pet_care.pet_duplicate_clinical_evaluation where review_id = ?)
				""", Boolean.class, reviewId));
	}

	public boolean createPetDuplicateClinicalEvaluation(UUID tenantId, UUID reviewId, UUID veterinarianPrincipalId) {
		return jdbcTemplate.update("""
				insert into owner_pet_care.pet_duplicate_clinical_evaluation (
				    tenant_id, review_id, evaluated_by_principal_id
				) values (?, ?, ?)
				on conflict do nothing
				""", tenantId, reviewId, veterinarianPrincipalId) == 1;
	}

	public int deactivatePetIfActive(UUID tenantId, UUID petId) {
		return jdbcTemplate.update("""
				update owner_pet_care.pet
				set status = 'INACTIVA', updated_at = statement_timestamp()
				where tenant_id = ? and id = ? and status = 'ACTIVA'
				""", tenantId, petId);
	}

	public boolean createPetDuplicateResolution(UUID tenantId, UUID reviewId, String outcome, UUID actorPrincipalId,
			String reason) {
		return jdbcTemplate.update("""
				insert into owner_pet_care.pet_duplicate_resolution (
				    tenant_id, review_id, outcome, resolved_by_principal_id, resolution_reason
				) values (?, ?, ?, ?, ?)
				on conflict do nothing
				""", tenantId, reviewId, outcome, actorPrincipalId, reason) == 1;
	}

	public List<PetProfile> listPetsByOwner(UUID ownerId) {
		return jdbcTemplate.query("""
				select id, tenant_id, current_owner_id, name, species, breed, sex, birth_date, estimated_age_months,
				       distinguishing_marks, status, created_at, updated_at
				from owner_pet_care.pet
				where current_owner_id = ?
				order by name, created_at
				""", this::mapPet, ownerId);
	}

	public int closeCurrentOwnership(UUID tenantId, UUID petId, UUID ownerId) {
		return jdbcTemplate.update("""
				update owner_pet_care.pet_ownership_period
				set effective_until = statement_timestamp()
				where tenant_id = ? and pet_id = ? and owner_id = ? and effective_until is null
				""", tenantId, petId, ownerId);
	}

	public List<ClientPetProfile> listCurrentPetsForClient(UUID clientPrincipalId) {
		return jdbcTemplate.query("""
				select pet.id, pet.name, pet.species, pet.breed, pet.sex, pet.birth_date, pet.estimated_age_months,
				       pet.distinguishing_marks, pet.status
				from owner_pet_care.pet pet
				join owner_pet_care.client_owner_link link
				  on link.tenant_id = pet.tenant_id and link.owner_id = pet.current_owner_id
				where link.client_principal_id = ?
				order by pet.name, pet.created_at
				""", this::mapClientPet, clientPrincipalId);
	}

	public Optional<ClientPetProfile> findCurrentPetForClient(UUID clientPrincipalId, UUID petId) {
		return jdbcTemplate.query("""
				select pet.id, pet.name, pet.species, pet.breed, pet.sex, pet.birth_date, pet.estimated_age_months,
				       pet.distinguishing_marks, pet.status
				from owner_pet_care.pet pet
				join owner_pet_care.client_owner_link link
				  on link.tenant_id = pet.tenant_id and link.owner_id = pet.current_owner_id
				where link.client_principal_id = ? and pet.id = ?
				""", this::mapClientPet, clientPrincipalId, petId).stream().findFirst();
	}

	public Optional<ClientPetProfile> updateCurrentPetForClient(UUID tenantId, UUID clientPrincipalId, UUID petId,
			PetDraft draft) {
		return jdbcTemplate.query("""
				update owner_pet_care.pet pet
				set name = ?, species = ?, breed = ?, sex = ?, birth_date = ?, estimated_age_months = ?,
				    distinguishing_marks = ?, updated_at = statement_timestamp()
				from owner_pet_care.client_owner_link link
				where pet.tenant_id = ? and pet.id = ?
				  and pet.status = 'ACTIVA'
				  and link.tenant_id = pet.tenant_id
				  and link.owner_id = pet.current_owner_id
				  and link.client_principal_id = ?
				returning pet.id, pet.name, pet.species, pet.breed, pet.sex, pet.birth_date, pet.estimated_age_months,
				          pet.distinguishing_marks, pet.status
				""", this::mapClientPet, draft.name(), draft.species(), draft.breed(), draft.sex(), draft.birthDate(),
				draft.estimatedAgeMonths(), draft.distinguishingMarks(), tenantId, petId, clientPrincipalId).stream().findFirst();
	}

	public int moveCurrentOwner(UUID tenantId, UUID petId, UUID expectedOwnerId, UUID newOwnerId) {
		return jdbcTemplate.update("""
				update owner_pet_care.pet
				set current_owner_id = ?, updated_at = statement_timestamp()
				where tenant_id = ? and id = ? and current_owner_id = ?
				""", newOwnerId, tenantId, petId, expectedOwnerId);
	}

	public List<OwnershipPeriod> listOwnershipPeriods(UUID petId) {
		return jdbcTemplate.query("""
				select period.id, period.tenant_id, period.pet_id, period.owner_id, owner.given_names, owner.family_names,
				       period.effective_from, period.effective_until, period.verified_by_principal_id, period.reason
				from owner_pet_care.pet_ownership_period period
				join owner_pet_care.owner_profile owner on owner.id = period.owner_id and owner.tenant_id = period.tenant_id
				where period.pet_id = ?
				order by period.effective_from desc, period.id desc
				""", this::mapOwnershipPeriod, petId);
	}

	public ClinicalEpisode createClinicalEpisode(UUID tenantId, UUID veterinarianPrincipalId, UUID petId, ClinicalDraft draft) {
		return jdbcTemplate.queryForObject("""
				insert into owner_pet_care.clinical_episode (
				    tenant_id, pet_id, veterinarian_principal_id, appointment_id, reason, anamnesis, assessment,
				    diagnosis, treatment_plan, observations
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				returning id, tenant_id, pet_id, veterinarian_principal_id, appointment_id, status, reason, anamnesis,
				          assessment, diagnosis, treatment_plan, observations, opened_at, finalized_at, created_at, updated_at
				""", this::mapClinicalEpisode, tenantId, petId, veterinarianPrincipalId, draft.appointmentId(), draft.reason(),
				draft.anamnesis(), draft.assessment(), draft.diagnosis(), draft.treatmentPlan(), draft.observations());
	}

	public Optional<ClinicalEpisode> findClinicalEpisode(UUID episodeId) {
		return jdbcTemplate.query("""
				select id, tenant_id, pet_id, veterinarian_principal_id, appointment_id, status, reason, anamnesis,
				       assessment, diagnosis, treatment_plan, observations, opened_at, finalized_at, created_at, updated_at
				from owner_pet_care.clinical_episode
				where id = ?
				""", this::mapClinicalEpisode, episodeId).stream().findFirst();
	}

	public Optional<ClinicalEpisode> finalizeClinicalEpisode(UUID episodeId, UUID veterinarianPrincipalId, ClinicalDraft draft) {
		return jdbcTemplate.query("""
				update owner_pet_care.clinical_episode
				set reason = ?, anamnesis = ?, assessment = ?, diagnosis = ?, treatment_plan = ?, observations = ?,
				    status = 'FINALIZADO', finalized_at = statement_timestamp(), updated_at = statement_timestamp()
				where id = ? and veterinarian_principal_id = ? and status = 'BORRADOR'
				returning id, tenant_id, pet_id, veterinarian_principal_id, appointment_id, status, reason, anamnesis,
				          assessment, diagnosis, treatment_plan, observations, opened_at, finalized_at, created_at, updated_at
				""", this::mapClinicalEpisode, draft.reason(), draft.anamnesis(), draft.assessment(), draft.diagnosis(),
				draft.treatmentPlan(), draft.observations(), episodeId, veterinarianPrincipalId).stream().findFirst();
	}

	public List<ClinicalEpisode> listClinicalEpisodes(UUID petId) {
		return jdbcTemplate.query("""
				select id, tenant_id, pet_id, veterinarian_principal_id, appointment_id, status, reason, anamnesis,
				       assessment, diagnosis, treatment_plan, observations, opened_at, finalized_at, created_at, updated_at
				from owner_pet_care.clinical_episode
				where pet_id = ?
				order by opened_at desc, id desc
		""", this::mapClinicalEpisode, petId);
	}

	public Optional<VaccinationRecord> createVaccinationIfAbsent(UUID tenantId, UUID veterinarianPrincipalId, UUID petId,
			VaccinationDraft draft) {
		return jdbcTemplate.query("""
				insert into owner_pet_care.vaccination_record (
				    tenant_id, pet_id, episode_id, veterinarian_principal_id, operation_id, vaccine_name,
				    administered_on, dose, batch_code, next_due_on
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				on conflict (tenant_id, operation_id) do nothing
				returning id, tenant_id, pet_id, episode_id, veterinarian_principal_id, operation_id, vaccine_name,
				          administered_on, dose, batch_code, next_due_on, recorded_at, created_at
				""", this::mapVaccinationRecord, tenantId, petId, draft.episodeId(), veterinarianPrincipalId,
				draft.operationId(), draft.vaccineName(), draft.administeredOn(), draft.dose(), draft.batchCode(),
				draft.nextDueOn()).stream().findFirst();
	}

	public Optional<VaccinationRecord> findVaccinationByOperationId(UUID tenantId, UUID operationId) {
		return jdbcTemplate.query("""
				select id, tenant_id, pet_id, episode_id, veterinarian_principal_id, operation_id, vaccine_name,
				       administered_on, dose, batch_code, next_due_on, recorded_at, created_at
				from owner_pet_care.vaccination_record
				where tenant_id = ? and operation_id = ?
				""", this::mapVaccinationRecord, tenantId, operationId).stream().findFirst();
	}

	public List<VaccinationRecord> listVaccinations(UUID petId) {
		return jdbcTemplate.query("""
				select id, tenant_id, pet_id, episode_id, veterinarian_principal_id, operation_id, vaccine_name,
				       administered_on, dose, batch_code, next_due_on, recorded_at, created_at
				from owner_pet_care.vaccination_record
				where pet_id = ?
				order by administered_on desc, recorded_at desc, id desc
				""", this::mapVaccinationRecord, petId);
	}

	public ClinicalPrescription createClinicalPrescription(UUID tenantId, UUID veterinarianPrincipalId, UUID petId,
			UUID episodeId, PrescriptionDraft draft) {
		return jdbcTemplate.queryForObject("""
				insert into owner_pet_care.clinical_prescription (
				    tenant_id, pet_id, episode_id, veterinarian_principal_id, medication, presentation, dose, route,
				    frequency, duration, instructions, prescribed_on
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				returning id, tenant_id, pet_id, episode_id, veterinarian_principal_id, medication, presentation,
				          dose, route, frequency, duration, instructions, prescribed_on, created_at
				""", this::mapClinicalPrescription, tenantId, petId, episodeId, veterinarianPrincipalId,
				draft.medication(), draft.presentation(), draft.dose(), draft.route(), draft.frequency(), draft.duration(),
				draft.instructions(), draft.prescribedOn());
	}

	public List<ClinicalPrescription> listClinicalPrescriptions(UUID episodeId) {
		return jdbcTemplate.query("""
				select id, tenant_id, pet_id, episode_id, veterinarian_principal_id, medication, presentation,
				       dose, route, frequency, duration, instructions, prescribed_on, created_at
				from owner_pet_care.clinical_prescription
				where episode_id = ?
				order by prescribed_on desc, created_at desc, id desc
				""", this::mapClinicalPrescription, episodeId);
	}

	public Optional<ClinicalEpisodeAmendment> createClinicalEpisodeAmendment(UUID tenantId, UUID veterinarianPrincipalId,
			UUID episodeId, AmendmentDraft draft) {
		return jdbcTemplate.query("""
				insert into owner_pet_care.clinical_episode_amendment (
				    tenant_id, pet_id, episode_id, veterinarian_principal_id, reason, previous_content, addendum
				)
				select ?, episode.pet_id, episode.id, ?, ?, jsonb_build_object(
				    'reason', episode.reason,
				    'anamnesis', episode.anamnesis,
				    'assessment', episode.assessment,
				    'diagnosis', episode.diagnosis,
				    'treatmentPlan', episode.treatment_plan,
				    'observations', episode.observations,
				    'finalizedAt', episode.finalized_at
				), ?
				from owner_pet_care.clinical_episode episode
				where episode.tenant_id = ? and episode.id = ? and episode.status = 'FINALIZADO'
				returning id, tenant_id, pet_id, episode_id, veterinarian_principal_id, reason, previous_content,
				          addendum, created_at
				""", this::mapClinicalEpisodeAmendment, tenantId, veterinarianPrincipalId, draft.reason(), draft.addendum(),
				tenantId, episodeId).stream().findFirst();
	}

	public List<ClinicalEpisodeAmendment> listClinicalEpisodeAmendments(UUID episodeId) {
		return jdbcTemplate.query("""
				select id, tenant_id, pet_id, episode_id, veterinarian_principal_id, reason, previous_content,
				       addendum, created_at
				from owner_pet_care.clinical_episode_amendment
				where episode_id = ?
				order by created_at desc, id desc
				""", this::mapClinicalEpisodeAmendment, episodeId);
	}

	public void audit(UUID tenantId, UUID actorPrincipalId, String actionCode, String resourceType, UUID resourceId) {
		jdbcTemplate.update("""
				insert into owner_pet_care.care_audit_event (
				    tenant_id, actor_principal_id, action_code, resource_type, resource_id
				) values (?, ?, ?, ?, ?)
				""", tenantId, actorPrincipalId, actionCode, resourceType, resourceId);
	}

	private OwnerProfile mapOwner(ResultSet resultSet, int rowNum) throws SQLException {
		return new OwnerProfile(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
				resultSet.getString("given_names"), resultSet.getString("family_names"), resultSet.getString("phone"),
				resultSet.getString("email"), resultSet.getString("address"), resultSet.getString("status"),
				resultSet.getString("origin"), instant(resultSet, "created_at"), instant(resultSet, "updated_at"));
	}

	private DuplicateCandidate mapDuplicateCandidate(ResultSet resultSet, int rowNum) throws SQLException {
		return new DuplicateCandidate(resultSet.getObject("id", UUID.class), resultSet.getString("label"),
				resultSet.getString("match_reason"));
	}

	private OwnerDuplicateReview mapCreatedOwnerDuplicateReview(ResultSet resultSet, int rowNum) throws SQLException {
		return new OwnerDuplicateReview(resultSet.getObject("id", UUID.class), resultSet.getObject("duplicate_owner_id", UUID.class),
				resultSet.getObject("reference_owner_id", UUID.class), null, null, instant(resultSet, "reported_at"), null, null);
	}

	private OwnerDuplicateReview mapOwnerDuplicateReview(ResultSet resultSet, int rowNum) throws SQLException {
		return new OwnerDuplicateReview(resultSet.getObject("id", UUID.class), resultSet.getObject("duplicate_owner_id", UUID.class),
				resultSet.getObject("reference_owner_id", UUID.class), resultSet.getString("duplicate_label"),
				resultSet.getString("reference_label"), instant(resultSet, "reported_at"), resultSet.getString("outcome"),
				instant(resultSet, "resolved_at"));
	}

	private PetDuplicateReview mapCreatedPetDuplicateReview(ResultSet resultSet, int rowNum) throws SQLException {
		return new PetDuplicateReview(resultSet.getObject("id", UUID.class), resultSet.getObject("duplicate_pet_id", UUID.class),
				resultSet.getObject("reference_pet_id", UUID.class), null, null, instant(resultSet, "reported_at"), null, null, null, false);
	}

	private PetDuplicateReview mapPetDuplicateReview(ResultSet resultSet, int rowNum) throws SQLException {
		return new PetDuplicateReview(resultSet.getObject("id", UUID.class), resultSet.getObject("duplicate_pet_id", UUID.class),
				resultSet.getObject("reference_pet_id", UUID.class), resultSet.getString("duplicate_label"),
				resultSet.getString("reference_label"), instant(resultSet, "reported_at"), resultSet.getString("outcome"),
				instant(resultSet, "resolved_at"), instant(resultSet, "evaluated_at"),
				resultSet.getBoolean("requires_clinical_evaluation"));
	}

	private PetProfile mapPet(ResultSet resultSet, int rowNum) throws SQLException {
		return new PetProfile(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
				resultSet.getObject("current_owner_id", UUID.class), resultSet.getString("name"), resultSet.getString("species"),
				resultSet.getString("breed"), resultSet.getString("sex"), resultSet.getObject("birth_date", LocalDate.class),
				resultSet.getObject("estimated_age_months", Integer.class), resultSet.getString("distinguishing_marks"),
				resultSet.getString("status"), instant(resultSet, "created_at"), instant(resultSet, "updated_at"));
	}

	private ClinicalEpisode mapClinicalEpisode(ResultSet resultSet, int rowNum) throws SQLException {
		return new ClinicalEpisode(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
				resultSet.getObject("pet_id", UUID.class), resultSet.getObject("veterinarian_principal_id", UUID.class),
				resultSet.getObject("appointment_id", UUID.class), resultSet.getString("status"), resultSet.getString("reason"),
				resultSet.getString("anamnesis"), resultSet.getString("assessment"), resultSet.getString("diagnosis"),
				resultSet.getString("treatment_plan"), resultSet.getString("observations"), instant(resultSet, "opened_at"),
				instant(resultSet, "finalized_at"), instant(resultSet, "created_at"), instant(resultSet, "updated_at"));
	}

	private VaccinationRecord mapVaccinationRecord(ResultSet resultSet, int rowNum) throws SQLException {
		return new VaccinationRecord(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
				resultSet.getObject("pet_id", UUID.class), resultSet.getObject("episode_id", UUID.class),
				resultSet.getObject("veterinarian_principal_id", UUID.class), resultSet.getObject("operation_id", UUID.class),
				resultSet.getString("vaccine_name"), resultSet.getObject("administered_on", LocalDate.class),
				resultSet.getString("dose"), resultSet.getString("batch_code"), resultSet.getObject("next_due_on", LocalDate.class),
				instant(resultSet, "recorded_at"), instant(resultSet, "created_at"));
	}

	private ClinicalPrescription mapClinicalPrescription(ResultSet resultSet, int rowNum) throws SQLException {
		return new ClinicalPrescription(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
				resultSet.getObject("pet_id", UUID.class), resultSet.getObject("episode_id", UUID.class),
				resultSet.getObject("veterinarian_principal_id", UUID.class), resultSet.getString("medication"),
				resultSet.getString("presentation"), resultSet.getString("dose"), resultSet.getString("route"),
				resultSet.getString("frequency"), resultSet.getString("duration"), resultSet.getString("instructions"),
				resultSet.getObject("prescribed_on", LocalDate.class), instant(resultSet, "created_at"));
	}

	private ClinicalEpisodeAmendment mapClinicalEpisodeAmendment(ResultSet resultSet, int rowNum) throws SQLException {
		return new ClinicalEpisodeAmendment(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
				resultSet.getObject("pet_id", UUID.class), resultSet.getObject("episode_id", UUID.class),
				resultSet.getObject("veterinarian_principal_id", UUID.class), resultSet.getString("reason"),
				resultSet.getString("previous_content"), resultSet.getString("addendum"), instant(resultSet, "created_at"));
	}

	private OwnershipPeriod mapOwnershipPeriod(ResultSet resultSet, int rowNum) throws SQLException {
		return new OwnershipPeriod(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
				resultSet.getObject("pet_id", UUID.class), resultSet.getObject("owner_id", UUID.class),
				resultSet.getString("given_names"), resultSet.getString("family_names"), instant(resultSet, "effective_from"),
				instant(resultSet, "effective_until"), resultSet.getObject("verified_by_principal_id", UUID.class),
				resultSet.getString("reason"));
	}

	private Instant instant(ResultSet resultSet, String column) throws SQLException {
		Timestamp value = resultSet.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	private ClientOwnerLinkRequest mapClientOwnerLinkRequest(ResultSet resultSet, int rowNum) throws SQLException {
		return new ClientOwnerLinkRequest(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
				resultSet.getString("request_reference"), resultSet.getObject("client_principal_id", UUID.class),
				resultSet.getString("status"), resultSet.getObject("proposed_owner_id", UUID.class),
				resultSet.getString("verification_method"), resultSet.getString("rejection_reason"),
				instant(resultSet, "requested_at"), instant(resultSet, "resolved_at"),
				resultSet.getObject("resolved_by_principal_id", UUID.class));
	}

	private ClientOwnerLink mapClientOwnerLink(ResultSet resultSet, int rowNum) throws SQLException {
		return new ClientOwnerLink(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
				resultSet.getObject("client_principal_id", UUID.class), resultSet.getObject("owner_id", UUID.class),
				resultSet.getObject("source_request_id", UUID.class), resultSet.getObject("approved_by_principal_id", UUID.class),
				instant(resultSet, "approved_at"));
	}

	private ClientPetProfile mapClientPet(ResultSet resultSet, int rowNum) throws SQLException {
		return new ClientPetProfile(resultSet.getObject("id", UUID.class), resultSet.getString("name"),
				resultSet.getString("species"), resultSet.getString("breed"), resultSet.getString("sex"),
				resultSet.getObject("birth_date", LocalDate.class), resultSet.getObject("estimated_age_months", Integer.class),
				resultSet.getString("distinguishing_marks"), resultSet.getString("status"));
	}

	public record OwnerDraft(String givenNames, String familyNames, String phone, String email, String address, String origin) { }
	public record DuplicateCandidate(UUID id, String label, String matchReason) { }
	public record OwnerDuplicateReview(UUID id, UUID duplicateOwnerId, UUID referenceOwnerId, String duplicateLabel,
			String referenceLabel, Instant reportedAt, String outcome, Instant resolvedAt) { }
	public record PetDuplicateReview(UUID id, UUID duplicatePetId, UUID referencePetId, String duplicateLabel,
			String referenceLabel, Instant reportedAt, String outcome, Instant resolvedAt, Instant clinicalEvaluatedAt,
			boolean requiresClinicalEvaluation) { }
	public record OwnerProfile(UUID id, UUID tenantId, String givenNames, String familyNames, String phone, String email,
			String address, String status, String origin, Instant createdAt, Instant updatedAt) { }
	public record PetDraft(String name, String species, String breed, String sex, LocalDate birthDate,
			Integer estimatedAgeMonths, String distinguishingMarks) { }
	public record PetProfile(UUID id, UUID tenantId, UUID currentOwnerId, String name, String species, String breed,
			String sex, LocalDate birthDate, Integer estimatedAgeMonths, String distinguishingMarks, String status,
			Instant createdAt, Instant updatedAt) { }
	public record ClinicalDraft(UUID appointmentId, String reason, String anamnesis, String assessment, String diagnosis,
			String treatmentPlan, String observations) { }
	public record ClinicalEpisode(UUID id, UUID tenantId, UUID petId, UUID veterinarianPrincipalId, UUID appointmentId,
			String status, String reason, String anamnesis, String assessment, String diagnosis, String treatmentPlan,
			String observations, Instant openedAt, Instant finalizedAt, Instant createdAt, Instant updatedAt) { }
	public record VaccinationDraft(UUID episodeId, UUID operationId, String vaccineName, LocalDate administeredOn, String dose,
			String batchCode, LocalDate nextDueOn) { }
	public record VaccinationRecord(UUID id, UUID tenantId, UUID petId, UUID episodeId, UUID veterinarianPrincipalId,
			UUID operationId, String vaccineName, LocalDate administeredOn, String dose, String batchCode, LocalDate nextDueOn,
			Instant recordedAt, Instant createdAt) { }
	public record PrescriptionDraft(String medication, String presentation, String dose, String route, String frequency,
			String duration, String instructions, LocalDate prescribedOn) { }
	public record ClinicalPrescription(UUID id, UUID tenantId, UUID petId, UUID episodeId, UUID veterinarianPrincipalId,
			String medication, String presentation, String dose, String route, String frequency, String duration,
			String instructions, LocalDate prescribedOn, Instant createdAt) { }
	public record AmendmentDraft(String reason, String addendum) { }
	public record ClinicalEpisodeAmendment(UUID id, UUID tenantId, UUID petId, UUID episodeId,
			UUID veterinarianPrincipalId, String reason, String previousContent, String addendum, Instant createdAt) { }
	public record OwnershipPeriod(UUID id, UUID tenantId, UUID petId, UUID ownerId, String ownerGivenNames,
			String ownerFamilyNames, Instant effectiveFrom, Instant effectiveUntil, UUID verifiedByPrincipalId,
			String reason) { }
	public record ClientOwnerLinkRequest(UUID id, UUID tenantId, String requestReference, UUID clientPrincipalId,
			String status, UUID proposedOwnerId, String verificationMethod, String rejectionReason, Instant requestedAt,
			Instant resolvedAt, UUID resolvedByPrincipalId) { }
	public record ClientOwnerLink(UUID id, UUID tenantId, UUID clientPrincipalId, UUID ownerId, UUID sourceRequestId,
			UUID approvedByPrincipalId, Instant approvedAt) { }
	public record ClientPetProfile(UUID id, String name, String species, String breed, String sex, LocalDate birthDate,
			Integer estimatedAgeMonths, String distinguishingMarks, String status) { }
}
