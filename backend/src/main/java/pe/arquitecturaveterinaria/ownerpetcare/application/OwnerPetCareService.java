package pe.arquitecturaveterinaria.ownerpetcare.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.TenantContextJdbc;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.ClinicalDraft;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.ClinicalEpisode;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.OwnerDraft;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.OwnerProfile;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.PetDraft;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.PetProfile;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.OwnershipPeriod;
import pe.arquitecturaveterinaria.shared.api.ApiException;

@Service
public class OwnerPetCareService {

	private final OwnerPetCareJdbcRepository repository;
	private final TenantContextJdbc tenantContext;
	private final B02AccessPolicy accessPolicy = new B02AccessPolicy();

	public OwnerPetCareService(OwnerPetCareJdbcRepository repository, TenantContextJdbc tenantContext) {
		this.repository = repository;
		this.tenantContext = tenantContext;
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public OwnerProfile createOwner(SessionPrincipal actor, OwnerCommand command) {
		accessPolicy.requireRegistryManager(actor);
		setTrustedContext(actor);
		OwnerProfile owner = repository.createOwner(actor.session().tenantId(), actor.session().accountId(),
				new OwnerDraft(required(command.givenNames(), "givenNames"), required(command.familyNames(), "familyNames"),
						optional(command.phone()), normalizedEmail(command.email()), optional(command.address()), "PRESENCIAL"));
		repository.audit(actor.session().tenantId(), actor.session().accountId(), "OWNER_CREATED", "OWNER", owner.id());
		return owner;
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<OwnerProfile> listOwners(SessionPrincipal actor, String query) {
		accessPolicy.requirePetReader(actor);
		setTrustedContext(actor);
		return repository.listOwners(optional(query));
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public OwnerProfile getOwner(SessionPrincipal actor, UUID ownerId) {
		accessPolicy.requirePetReader(actor);
		setTrustedContext(actor);
		return ownerOrNotFound(ownerId);
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public PetProfile createPet(SessionPrincipal actor, UUID ownerId, PetCommand command) {
		accessPolicy.requireRegistryManager(actor);
		setTrustedContext(actor);
		OwnerProfile owner = ownerOrNotFound(ownerId);
		if (!"ACTIVO".equals(owner.status())) {
			throw conflict("OWNER_INACTIVE", "No se puede asignar una mascota a un propietario inactivo.");
		}
		validatePetAge(command.birthDate(), command.estimatedAgeMonths());
		PetProfile pet = repository.createPet(actor.session().tenantId(), actor.session().accountId(), ownerId,
				new PetDraft(required(command.name(), "name"), required(command.species(), "species"), optional(command.breed()),
						normalizedSex(command.sex()), command.birthDate(), command.estimatedAgeMonths(),
						optional(command.distinguishingMarks())));
		repository.createInitialOwnership(actor.session().tenantId(), pet.id(), ownerId, actor.session().accountId());
		repository.audit(actor.session().tenantId(), actor.session().accountId(), "PET_CREATED", "PET", pet.id());
		return pet;
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public PetProfile getPet(SessionPrincipal actor, UUID petId) {
		accessPolicy.requirePetReader(actor);
		setTrustedContext(actor);
		return petOrNotFound(petId);
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<PetProfile> listPetsByOwner(SessionPrincipal actor, UUID ownerId) {
		accessPolicy.requirePetReader(actor);
		setTrustedContext(actor);
		ownerOrNotFound(ownerId);
		return repository.listPetsByOwner(ownerId);
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public PetProfile transferPetOwnership(SessionPrincipal actor, UUID petId, OwnershipTransferCommand command) {
		accessPolicy.requireRegistryManager(actor);
		setTrustedContext(actor);
		PetProfile current = repository.lockPet(petId).orElseThrow(() -> notFound("No se encontrÃ³ el recurso solicitado."));
		if (!current.currentOwnerId().equals(command.expectedOwnerId())) {
			throw conflict("B02_OWNER_TRANSFER_STALE",
					"La responsabilidad cambiÃ³ antes de confirmar la transferencia. Recarga la ficha.");
		}
		if (current.currentOwnerId().equals(command.newOwnerId())) {
			throw validation("newOwnerId", "La mascota ya tiene este responsable principal.");
		}
		if (!command.identityVerified()) {
			throw validation("identityVerified", "Confirma la verificaciÃ³n manual antes de transferir la responsabilidad.");
		}
		OwnerProfile newOwner = ownerOrNotFound(command.newOwnerId());
		if (!"ACTIVO".equals(newOwner.status())) {
			throw conflict("OWNER_INACTIVE", "No se puede transferir una mascota a un propietario inactivo.");
		}
		if (repository.closeCurrentOwnership(actor.session().tenantId(), petId, current.currentOwnerId()) != 1
				|| repository.moveCurrentOwner(actor.session().tenantId(), petId, current.currentOwnerId(), newOwner.id()) != 1) {
			throw conflict("B02_OWNER_TRANSFER_CONCURRENT_CHANGE",
					"No fue posible confirmar una transferencia consistente. Recarga la ficha.");
		}
		repository.createInitialOwnership(actor.session().tenantId(), petId, newOwner.id(), actor.session().accountId(),
				"Transferencia verificada: " + required(command.reason(), "reason"));
		repository.audit(actor.session().tenantId(), actor.session().accountId(), "PET_OWNER_TRANSFERRED", "PET", petId);
		return repository.findPet(petId).orElseThrow(() -> conflict("B02_OWNER_TRANSFER_CONCURRENT_CHANGE",
				"La ficha no pudo recuperarse después de la transferencia."));
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<OwnershipPeriod> listOwnershipPeriods(SessionPrincipal actor, UUID petId) {
		accessPolicy.requireRegistryManager(actor);
		setTrustedContext(actor);
		petOrNotFound(petId);
		return repository.listOwnershipPeriods(petId);
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public ClinicalEpisode createClinicalEpisode(SessionPrincipal actor, UUID petId, ClinicalEpisodeCommand command) {
		accessPolicy.requireVeterinarian(actor);
		setTrustedContext(actor);
		petOrNotFound(petId);
		ClinicalEpisode episode = repository.createClinicalEpisode(actor.session().tenantId(), actor.session().accountId(), petId,
				clinicalDraft(command));
		repository.audit(actor.session().tenantId(), actor.session().accountId(), "CLINICAL_EPISODE_DRAFT_CREATED",
				"CLINICAL_EPISODE", episode.id());
		return episode;
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public ClinicalEpisode finalizeClinicalEpisode(SessionPrincipal actor, UUID episodeId, ClinicalEpisodeCommand command) {
		accessPolicy.requireVeterinarian(actor);
		setTrustedContext(actor);
		ClinicalEpisode existing = episodeOrNotFound(episodeId);
		if (!existing.veterinarianPrincipalId().equals(actor.session().accountId())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "B02_CLINICAL_AUTHOR_REQUIRED",
					"Solo el veterinario autor puede finalizar este borrador.");
		}
		if (!"BORRADOR".equals(existing.status())) {
			throw conflict("CLINICAL_EPISODE_ALREADY_FINALIZED", "El episodio clínico ya fue finalizado.");
		}
		ClinicalDraft draft = clinicalDraft(command);
		if (isBlank(draft.reason()) || isBlank(draft.assessment())) {
			throw validation("reason", "El motivo y la evaluación son obligatorios para finalizar.");
		}
		ClinicalEpisode finalized = repository.finalizeClinicalEpisode(episodeId, actor.session().accountId(), draft)
				.orElseThrow(() -> conflict("CLINICAL_EPISODE_CONCURRENT_CHANGE",
						"El episodio clínico cambió antes de poder finalizarse."));
		repository.audit(actor.session().tenantId(), actor.session().accountId(), "CLINICAL_EPISODE_FINALIZED",
				"CLINICAL_EPISODE", finalized.id());
		return finalized;
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<ClinicalEpisode> listClinicalHistory(SessionPrincipal actor, UUID petId) {
		accessPolicy.requireVeterinarian(actor);
		setTrustedContext(actor);
		petOrNotFound(petId);
		return repository.listClinicalEpisodes(petId);
	}

	private void setTrustedContext(SessionPrincipal actor) {
		tenantContext.setTenantId(actor.session().tenantId());
		tenantContext.setPrincipalId(actor.session().accountId());
	}

	private OwnerProfile ownerOrNotFound(UUID ownerId) {
		return repository.findOwner(ownerId)
				.orElseThrow(() -> notFound("No se encontró el recurso solicitado."));
	}

	private PetProfile petOrNotFound(UUID petId) {
		return repository.findPet(petId)
				.orElseThrow(() -> notFound("No se encontró el recurso solicitado."));
	}

	private ClinicalEpisode episodeOrNotFound(UUID episodeId) {
		return repository.findClinicalEpisode(episodeId)
				.orElseThrow(() -> notFound("No se encontró el recurso solicitado."));
	}

	private ClinicalDraft clinicalDraft(ClinicalEpisodeCommand command) {
		return new ClinicalDraft(command.appointmentId(), optional(command.reason()), optional(command.anamnesis()),
				optional(command.assessment()), optional(command.diagnosis()), optional(command.treatmentPlan()),
				optional(command.observations()));
	}

	private void validatePetAge(LocalDate birthDate, Integer estimatedAgeMonths) {
		if ((birthDate == null) == (estimatedAgeMonths == null)) {
			throw validation("birthDate", "Indica fecha de nacimiento o edad estimada.");
		}
		if (birthDate != null && birthDate.isAfter(LocalDate.now())) {
			throw validation("birthDate", "La fecha de nacimiento no puede estar en el futuro.");
		}
		if (estimatedAgeMonths != null && estimatedAgeMonths < 0) {
			throw validation("estimatedAgeMonths", "La edad estimada no puede ser negativa.");
		}
	}

	private String normalizedSex(String value) {
		String normalized = optional(value);
		if (normalized == null) {
			return "DESCONOCIDO";
		}
		normalized = normalized.toUpperCase(Locale.ROOT);
		if (!List.of("HEMBRA", "MACHO", "DESCONOCIDO").contains(normalized)) {
			throw validation("sex", "El sexo indicado no es válido.");
		}
		return normalized;
	}

	private String normalizedEmail(String value) {
		String normalized = optional(value);
		return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
	}

	private String required(String value, String field) {
		String normalized = optional(value);
		if (normalized == null) {
			throw validation(field, "Este dato es obligatorio.");
		}
		return normalized;
	}

	private String optional(String value) {
		if (value == null) {
			return null;
		}
		String normalized = value.trim();
		return normalized.isEmpty() ? null : normalized;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private ApiException validation(String field, String message) {
		return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", message + " (" + field + ")");
	}

	private ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}

	private ApiException notFound(String message) {
		return new ApiException(HttpStatus.NOT_FOUND, "B02_RESOURCE_NOT_FOUND", message);
	}

	public record OwnerCommand(String givenNames, String familyNames, String phone, String email, String address) { }
	public record PetCommand(String name, String species, String breed, String sex, LocalDate birthDate,
			Integer estimatedAgeMonths, String distinguishingMarks) { }
	public record OwnershipTransferCommand(UUID expectedOwnerId, UUID newOwnerId, String reason, boolean identityVerified) { }
	public record ClinicalEpisodeCommand(UUID appointmentId, String reason, String anamnesis, String assessment,
			String diagnosis, String treatmentPlan, String observations) { }
}
