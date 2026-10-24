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

	public record OwnerDraft(String givenNames, String familyNames, String phone, String email, String address, String origin) { }
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
	public record OwnershipPeriod(UUID id, UUID tenantId, UUID petId, UUID ownerId, String ownerGivenNames,
			String ownerFamilyNames, Instant effectiveFrom, Instant effectiveUntil, UUID verifiedByPrincipalId,
			String reason) { }
}
