package pe.arquitecturaveterinaria.ownerpetcare.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.TenantContextJdbc;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.ClinicalDraft;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.ClinicalEpisode;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.ClinicalEpisodeAmendment;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.ClinicalPrescription;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.ClientOwnerLinkRequest;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.ClientPetProfile;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.DuplicateCandidate;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.OwnerDuplicateReview;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.PetDuplicateReview;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.AmendmentDraft;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.OwnerDraft;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.OwnerProfile;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.PetDraft;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.PetProfile;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.OwnershipPeriod;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.PrescriptionDraft;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.VaccinationDraft;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.VaccinationRecord;
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

	@Transactional(transactionManager = "jdbcTransactionManager")
	public OwnerProfile updateOwner(SessionPrincipal actor, UUID ownerId, OwnerCommand command) {
		accessPolicy.requireRegistryManager(actor);
		setTrustedContext(actor);
		OwnerProfile current = lockOwnerOrNotFound(ownerId);
		OwnerProfile updated = repository.updateOwner(actor.session().tenantId(), ownerId,
				new OwnerDraft(required(command.givenNames(), "givenNames"), required(command.familyNames(), "familyNames"),
						optional(command.phone()), normalizedEmail(command.email()), optional(command.address()), current.origin()))
				.orElseThrow(() -> notFound("No se encontró el recurso solicitado."));
		repository.audit(actor.session().tenantId(), actor.session().accountId(), "OWNER_UPDATED", "OWNER", updated.id());
		return updated;
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
		OwnerProfile owner = lockOwnerOrNotFound(ownerId);
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

	@Transactional(transactionManager = "jdbcTransactionManager")
	public PetProfile updatePet(SessionPrincipal actor, UUID petId, PetCommand command) {
		accessPolicy.requireRegistryManager(actor);
		setTrustedContext(actor);
		PetProfile current = lockPetOrNotFound(petId);
		validatePetAge(command.birthDate(), command.estimatedAgeMonths());
		PetProfile updated = repository.updatePet(actor.session().tenantId(), petId,
				new PetDraft(required(command.name(), "name"), required(command.species(), "species"), optional(command.breed()),
						normalizedSex(command.sex()), command.birthDate(), command.estimatedAgeMonths(),
						optional(command.distinguishingMarks())))
				.orElseThrow(() -> notFound("No se encontró el recurso solicitado."));
		if (!current.currentOwnerId().equals(updated.currentOwnerId()) || !current.status().equals(updated.status())) {
			throw conflict("B02_PET_UPDATE_INVARIANT", "La edición básica no puede cambiar responsable ni condición de la mascota.");
		}
		repository.audit(actor.session().tenantId(), actor.session().accountId(), "PET_UPDATED", "PET", updated.id());
		return updated;
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
		OwnerProfile newOwner = lockOwnerOrNotFound(command.newOwnerId());
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
		petForClinicalWrite(petId);
		ClinicalEpisode episode = repository.createClinicalEpisode(actor.session().tenantId(), actor.session().accountId(), petId,
				clinicalDraft(command));
		repository.audit(actor.session().tenantId(), actor.session().accountId(), "CLINICAL_EPISODE_DRAFT_CREATED",
				"CLINICAL_EPISODE", episode.id());
		return episode;
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public ClientOwnerLinkStatus requestClientOwnerLink(SessionPrincipal actor) {
		accessPolicy.requireClient(actor);
		setTrustedContext(actor);
		if (repository.findClientOwnerLink(actor.session().accountId()).isPresent()) {
			throw conflict("B02_CLIENT_ALREADY_LINKED", "La cuenta ya tiene una vinculacion digital aprobada.");
		}
		if (repository.findPendingClientOwnerLinkRequest(actor.session().accountId()).isPresent()) {
			throw conflict("B02_CLIENT_LINK_REQUEST_PENDING", "Ya existe una solicitud de vinculacion pendiente.");
		}
		ClientOwnerLinkRequest request = repository.createClientOwnerLinkRequest(actor.session().tenantId(),
				actor.session().accountId());
		repository.audit(actor.session().tenantId(), actor.session().accountId(), "CLIENT_OWNER_LINK_REQUESTED",
				"CLIENT_OWNER_LINK_REQUEST", request.id());
		return statusOf(request);
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public ClientOwnerLinkStatus getClientOwnerLinkStatus(SessionPrincipal actor) {
		accessPolicy.requireClient(actor);
		setTrustedContext(actor);
		return repository.findLatestClientOwnerLinkRequest(actor.session().accountId()).map(this::statusOf)
				.orElseGet(() -> new ClientOwnerLinkStatus("SIN_SOLICITUD", null, null, null, null));
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<ClientPetProfile> listCurrentPetsForClient(SessionPrincipal actor) {
		accessPolicy.requireClient(actor);
		setTrustedContext(actor);
		return repository.listCurrentPetsForClient(actor.session().accountId());
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public OwnerProfile getClientOwnerProfile(SessionPrincipal actor) {
		accessPolicy.requireClient(actor);
		setTrustedContext(actor);
		return clientOwnerOrRequired(actor);
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public OwnerProfile updateClientOwnerProfile(SessionPrincipal actor, OwnerCommand command) {
		accessPolicy.requireClient(actor);
		setTrustedContext(actor);
		OwnerProfile owner = clientOwnerOrRequired(actor);
		OwnerProfile updated = repository.updateOwnerForClient(actor.session().tenantId(), actor.session().accountId(), owner.id(),
				new OwnerDraft(required(command.givenNames(), "givenNames"), required(command.familyNames(), "familyNames"),
						optional(command.phone()), normalizedEmail(command.email()), optional(command.address()), owner.origin()))
				.orElseThrow(() -> notFound("No se encontro la ficha propia autorizada."));
		repository.audit(actor.session().tenantId(), actor.session().accountId(), "CLIENT_OWNER_PROFILE_UPDATED", "OWNER", updated.id());
		return updated;
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public ClientPetProfile createClientPet(SessionPrincipal actor, PetCommand command) {
		accessPolicy.requireClient(actor);
		setTrustedContext(actor);
		OwnerProfile owner = lockOwnerOrNotFound(clientOwnerOrRequired(actor).id());
		if (!"ACTIVO".equals(owner.status())) {
			throw conflict("OWNER_INACTIVE", "No se puede registrar una mascota para una ficha inactiva.");
		}
		validatePetAge(command.birthDate(), command.estimatedAgeMonths());
		PetProfile pet = repository.createPet(actor.session().tenantId(), actor.session().accountId(), owner.id(),
				new PetDraft(required(command.name(), "name"), required(command.species(), "species"), optional(command.breed()),
						normalizedSex(command.sex()), command.birthDate(), command.estimatedAgeMonths(),
						optional(command.distinguishingMarks())));
		repository.createInitialOwnership(actor.session().tenantId(), pet.id(), owner.id(), actor.session().accountId(),
				"Alta digital por propietario vinculado");
		repository.audit(actor.session().tenantId(), actor.session().accountId(), "CLIENT_PET_CREATED", "PET", pet.id());
		return clientPetOrNotFound(actor, pet.id());
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public ClientPetProfile getClientPet(SessionPrincipal actor, UUID petId) {
		accessPolicy.requireClient(actor);
		setTrustedContext(actor);
		return clientPetOrNotFound(actor, petId);
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public ClientPetProfile updateClientPet(SessionPrincipal actor, UUID petId, PetCommand command) {
		accessPolicy.requireClient(actor);
		setTrustedContext(actor);
		validatePetAge(command.birthDate(), command.estimatedAgeMonths());
		ClientPetProfile updated = repository.updateCurrentPetForClient(actor.session().tenantId(), actor.session().accountId(), petId,
				new PetDraft(required(command.name(), "name"), required(command.species(), "species"), optional(command.breed()),
						normalizedSex(command.sex()), command.birthDate(), command.estimatedAgeMonths(),
						optional(command.distinguishingMarks())))
				.orElseThrow(() -> notFound("No se encontro la ficha propia autorizada."));
		repository.audit(actor.session().tenantId(), actor.session().accountId(), "CLIENT_PET_UPDATED", "PET", updated.id());
		return updated;
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<DuplicateCandidate> findPotentialOwnerDuplicates(SessionPrincipal actor, UUID ownerId) {
		accessPolicy.requireRegistryManager(actor);
		setTrustedContext(actor);
		ownerOrNotFound(ownerId);
		return repository.findPotentialOwnerDuplicates(ownerId);
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<DuplicateCandidate> findPotentialPetDuplicates(SessionPrincipal actor, UUID petId) {
		accessPolicy.requireRegistryManager(actor);
		setTrustedContext(actor);
		petOrNotFound(petId);
		return repository.findPotentialPetDuplicates(petId);
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public OwnerDuplicateReview reportOwnerDuplicate(SessionPrincipal actor, DuplicateReviewCommand command) {
		accessPolicy.requireRegistryManager(actor);
		setTrustedContext(actor);
		if (command.duplicateId() == null || command.referenceId() == null || command.duplicateId().equals(command.referenceId())) {
			throw validation("referenceId", "Selecciona dos fichas distintas para una revisión humana.");
		}
		OwnerPair pair = lockOwnerPair(command.duplicateId(), command.referenceId());
		if (!"ACTIVO".equals(pair.duplicate().status()) || !"ACTIVO".equals(pair.reference().status())) {
			throw conflict("B02_DUPLICATE_REVIEW_INACTIVE_RECORD", "Las dos fichas deben estar activas antes de abrir la revisión.");
		}
		OwnerDuplicateReview review = repository.createOwnerDuplicateReview(actor.session().tenantId(), actor.session().accountId(),
				pair.duplicate().id(), pair.reference().id())
				.orElseThrow(() -> conflict("B02_DUPLICATE_REVIEW_ALREADY_EXISTS",
						"Ya existe un expediente inmutable para este par; revisa su resultado antes de crear otro."));
		repository.audit(actor.session().tenantId(), actor.session().accountId(), "OWNER_DUPLICATE_REVIEW_REPORTED",
				"OWNER_DUPLICATE_REVIEW", review.id());
		return review;
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<OwnerDuplicateReview> listOwnerDuplicateReviews(SessionPrincipal actor) {
		accessPolicy.requireRegistryManager(actor);
		setTrustedContext(actor);
		return repository.listOwnerDuplicateReviews();
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public void resolveOwnerDuplicate(SessionPrincipal actor, UUID reviewId, DuplicateResolutionCommand command) {
		accessPolicy.requireRegistryManager(actor);
		setTrustedContext(actor);
		repository.lockDuplicateReviewOperation(reviewId);
		OwnerDuplicateReview review = repository.lockOwnerDuplicateReview(reviewId)
				.orElseThrow(() -> notFound("No se encontró la revisión de duplicado."));
		if (review.outcome() != null) {
			throw conflict("B02_DUPLICATE_REVIEW_ALREADY_RESOLVED", "La revisión ya tiene una resolución inmutable.");
		}
		String outcome = duplicateOutcome(command.outcome());
		String reason = duplicateResolutionReason(command.reason());
		if ("CONFIRMADA_INACTIVA".equals(outcome)) {
			OwnerPair pair = lockOwnerPair(review.duplicateOwnerId(), review.referenceOwnerId());
			if (!"ACTIVO".equals(pair.duplicate().status()) || !"ACTIVO".equals(pair.reference().status())) {
				throw conflict("B02_DUPLICATE_REVIEW_STALE", "Las fichas cambiaron antes de confirmar la inactivación.");
			}
			if (repository.ownerHasActiveResponsibilities(pair.duplicate().id()) || repository.ownerHasApprovedClientLink(pair.duplicate().id())) {
				throw conflict("B02_DUPLICATE_OWNER_HAS_ACTIVE_RELATIONS",
						"No se puede inactivar la ficha mientras conserve mascotas activas o un vínculo digital vigente.");
			}
			if (repository.deactivateOwnerIfActive(actor.session().tenantId(), pair.duplicate().id()) != 1) {
				throw conflict("B02_DUPLICATE_REVIEW_STALE", "La ficha cambió antes de confirmar la inactivación.");
			}
		}
		if (!repository.createOwnerDuplicateResolution(actor.session().tenantId(), review.id(), outcome,
				actor.session().accountId(), reason)) {
			throw conflict("B02_DUPLICATE_REVIEW_ALREADY_RESOLVED", "La revisión fue resuelta por otra operación.");
		}
		repository.audit(actor.session().tenantId(), actor.session().accountId(), "OWNER_DUPLICATE_REVIEW_RESOLVED",
				"OWNER_DUPLICATE_REVIEW", review.id());
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public PetDuplicateReview reportPetDuplicate(SessionPrincipal actor, DuplicateReviewCommand command) {
		accessPolicy.requireRegistryManager(actor);
		setTrustedContext(actor);
		if (command.duplicateId() == null || command.referenceId() == null || command.duplicateId().equals(command.referenceId())) {
			throw validation("referenceId", "Selecciona dos fichas distintas para una revisión humana.");
		}
		PetPair pair = lockPetPair(command.duplicateId(), command.referenceId());
		if (!"ACTIVA".equals(pair.duplicate().status()) || !"ACTIVA".equals(pair.reference().status())) {
			throw conflict("B02_DUPLICATE_REVIEW_INACTIVE_RECORD", "Las dos fichas deben estar activas antes de abrir la revisión.");
		}
		PetDuplicateReview review = repository.createPetDuplicateReview(actor.session().tenantId(), actor.session().accountId(),
				pair.duplicate().id(), pair.reference().id())
				.orElseThrow(() -> conflict("B02_DUPLICATE_REVIEW_ALREADY_EXISTS",
						"Ya existe un expediente inmutable para este par; revisa su resultado antes de crear otro."));
		repository.audit(actor.session().tenantId(), actor.session().accountId(), "PET_DUPLICATE_REVIEW_REPORTED",
				"PET_DUPLICATE_REVIEW", review.id());
		return review;
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<PetDuplicateReview> listPetDuplicateReviews(SessionPrincipal actor) {
		accessPolicy.requirePetReader(actor);
		setTrustedContext(actor);
		return repository.listPetDuplicateReviews();
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public void evaluatePetDuplicateClinically(SessionPrincipal actor, UUID reviewId) {
		accessPolicy.requireVeterinarian(actor);
		setTrustedContext(actor);
		repository.lockDuplicateReviewOperation(reviewId);
		PetDuplicateReview review = repository.lockPetDuplicateReview(reviewId)
				.orElseThrow(() -> notFound("No se encontró la revisión de duplicado."));
		if (review.outcome() != null) {
			throw conflict("B02_DUPLICATE_REVIEW_ALREADY_RESOLVED", "No se evalúan expedientes ya resueltos.");
		}
		PetProfile duplicate = lockPetOrNotFound(review.duplicatePetId());
		if (!repository.petHasClinicalData(duplicate.id())) {
			throw conflict("B02_DUPLICATE_CLINICAL_EVALUATION_NOT_REQUIRED",
					"Esta ficha no tiene datos clínicos que requieran una evaluación veterinaria.");
		}
		if (!repository.createPetDuplicateClinicalEvaluation(actor.session().tenantId(), review.id(), actor.session().accountId())) {
			throw conflict("B02_DUPLICATE_CLINICAL_EVALUATION_ALREADY_RECORDED",
					"La evaluación clínica ya fue registrada para esta revisión.");
		}
		repository.audit(actor.session().tenantId(), actor.session().accountId(), "PET_DUPLICATE_CLINICALLY_EVALUATED",
				"PET_DUPLICATE_REVIEW", review.id());
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public void resolvePetDuplicate(SessionPrincipal actor, UUID reviewId, DuplicateResolutionCommand command) {
		accessPolicy.requireRegistryManager(actor);
		setTrustedContext(actor);
		repository.lockDuplicateReviewOperation(reviewId);
		PetDuplicateReview review = repository.lockPetDuplicateReview(reviewId)
				.orElseThrow(() -> notFound("No se encontró la revisión de duplicado."));
		if (review.outcome() != null) {
			throw conflict("B02_DUPLICATE_REVIEW_ALREADY_RESOLVED", "La revisión ya tiene una resolución inmutable.");
		}
		String outcome = duplicateOutcome(command.outcome());
		String reason = duplicateResolutionReason(command.reason());
		if ("CONFIRMADA_INACTIVA".equals(outcome)) {
			PetPair pair = lockPetPair(review.duplicatePetId(), review.referencePetId());
			if (!"ACTIVA".equals(pair.duplicate().status()) || !"ACTIVA".equals(pair.reference().status())) {
				throw conflict("B02_DUPLICATE_REVIEW_STALE", "Las fichas cambiaron antes de confirmar la inactivación.");
			}
			if (repository.petHasClinicalData(pair.duplicate().id())
					&& !repository.hasPetDuplicateClinicalEvaluation(review.id())) {
				throw conflict("B02_DUPLICATE_CLINICAL_EVALUATION_REQUIRED",
						"La ficha tiene datos clínicos; un veterinario debe evaluar la revisión antes de inactivarla.");
			}
			if (repository.deactivatePetIfActive(actor.session().tenantId(), pair.duplicate().id()) != 1) {
				throw conflict("B02_DUPLICATE_REVIEW_STALE", "La ficha cambió antes de confirmar la inactivación.");
			}
		}
		if (!repository.createPetDuplicateResolution(actor.session().tenantId(), review.id(), outcome,
				actor.session().accountId(), reason)) {
			throw conflict("B02_DUPLICATE_REVIEW_ALREADY_RESOLVED", "La revisión fue resuelta por otra operación.");
		}
		repository.audit(actor.session().tenantId(), actor.session().accountId(), "PET_DUPLICATE_REVIEW_RESOLVED",
				"PET_DUPLICATE_REVIEW", review.id());
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<ClientOwnerLinkRequest> listClientOwnerLinkRequests(SessionPrincipal actor, String status) {
		accessPolicy.requireRegistryManager(actor);
		setTrustedContext(actor);
		return repository.listClientOwnerLinkRequests(status == null ? "PENDIENTE" : status);
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public ClientOwnerLinkRequest resolveClientOwnerLinkRequest(SessionPrincipal actor, UUID requestId,
			ClientOwnerLinkResolutionCommand command) {
		accessPolicy.requireRegistryManager(actor);
		setTrustedContext(actor);
		ClientOwnerLinkRequest request = repository.lockClientOwnerLinkRequest(requestId)
				.orElseThrow(() -> notFound("No se encontro la solicitud de vinculacion."));
		if (!"PENDIENTE".equals(request.status())) {
			throw conflict("B02_CLIENT_LINK_REQUEST_ALREADY_RESOLVED",
					"La solicitud ya fue resuelta y no puede procesarse otra vez.");
		}
		if (!command.identityVerified()) {
			throw validation("identityVerified", "Confirma la verificacion manual antes de resolver la vinculacion.");
		}
		String status = required(command.status(), "status").toUpperCase(Locale.ROOT);
		String verificationMethod = required(command.verificationMethod(), "verificationMethod");
		if ("APROBADA".equals(status)) {
			OwnerProfile owner = ownerOrNotFound(command.ownerId());
			if (!"ACTIVO".equals(owner.status())) {
				throw conflict("OWNER_INACTIVE", "No se puede vincular una cuenta a un propietario inactivo.");
			}
			if (repository.findClientOwnerLink(request.clientPrincipalId()).isPresent()) {
				throw conflict("B02_CLIENT_ALREADY_LINKED", "La cuenta ya tiene una vinculacion digital aprobada.");
			}
			try {
				repository.createClientOwnerLink(actor.session().tenantId(), request.clientPrincipalId(), owner.id(), request.id(),
						actor.session().accountId());
			} catch (DataIntegrityViolationException exception) {
				throw conflict("B02_CLIENT_LINK_CONFLICT", "El propietario o la cuenta ya tienen una vinculacion incompatible.");
			}
			ClientOwnerLinkRequest resolved = repository.resolveClientOwnerLinkRequest(request.id(), status, owner.id(),
					verificationMethod, null, actor.session().accountId())
					.orElseThrow(() -> conflict("B02_CLIENT_LINK_CONCURRENT_CHANGE",
							"La solicitud cambio antes de poder aprobarse."));
			repository.audit(actor.session().tenantId(), actor.session().accountId(), "CLIENT_OWNER_LINK_APPROVED",
					"CLIENT_OWNER_LINK_REQUEST", resolved.id());
			return resolved;
		}
		if ("RECHAZADA".equals(status)) {
			String rejectionReason = required(command.rejectionReason(), "rejectionReason");
			ClientOwnerLinkRequest resolved = repository.resolveClientOwnerLinkRequest(request.id(), status, null,
					verificationMethod, rejectionReason, actor.session().accountId())
					.orElseThrow(() -> conflict("B02_CLIENT_LINK_CONCURRENT_CHANGE",
							"La solicitud cambio antes de poder rechazarse."));
			repository.audit(actor.session().tenantId(), actor.session().accountId(), "CLIENT_OWNER_LINK_REJECTED",
					"CLIENT_OWNER_LINK_REQUEST", resolved.id());
			return resolved;
		}
		throw validation("status", "Indica una decision de aprobacion o rechazo.");
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public ClinicalEpisode finalizeClinicalEpisode(SessionPrincipal actor, UUID episodeId, ClinicalEpisodeCommand command) {
		accessPolicy.requireVeterinarian(actor);
		setTrustedContext(actor);
		ClinicalEpisode existing = episodeOrNotFound(episodeId);
		petForClinicalWrite(existing.petId());
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

	@Transactional(transactionManager = "jdbcTransactionManager")
	public VaccinationRecord recordVaccination(SessionPrincipal actor, UUID petId, VaccinationCommand command) {
		accessPolicy.requireVeterinarian(actor);
		setTrustedContext(actor);
		petForClinicalWrite(petId);
		if (command.episodeId() != null) {
			ClinicalEpisode episode = episodeOrNotFound(command.episodeId());
			if (!episode.petId().equals(petId)) {
				throw validation("episodeId", "El episodio indicado no corresponde a esta mascota.");
			}
		}
		VaccinationDraft draft = vaccinationDraft(command);
		return repository.createVaccinationIfAbsent(actor.session().tenantId(), actor.session().accountId(), petId, draft)
				.map(record -> {
					repository.audit(actor.session().tenantId(), actor.session().accountId(), "VACCINATION_RECORDED",
							"VACCINATION", record.id());
					return record;
				})
				.orElseGet(() -> vaccinationForRetry(actor.session().tenantId(), petId, draft));
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<VaccinationRecord> listVaccinations(SessionPrincipal actor, UUID petId) {
		accessPolicy.requireVeterinarian(actor);
		setTrustedContext(actor);
		petOrNotFound(petId);
		return repository.listVaccinations(petId);
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public ClinicalPrescription recordClinicalPrescription(SessionPrincipal actor, UUID episodeId,
			PrescriptionCommand command) {
		accessPolicy.requireVeterinarian(actor);
		setTrustedContext(actor);
		ClinicalEpisode episode = episodeOrNotFound(episodeId);
		petForClinicalWrite(episode.petId());
		ClinicalPrescription prescription = repository.createClinicalPrescription(actor.session().tenantId(),
				actor.session().accountId(), episode.petId(), episode.id(), prescriptionDraft(command));
		repository.audit(actor.session().tenantId(), actor.session().accountId(), "CLINICAL_PRESCRIPTION_RECORDED",
				"CLINICAL_PRESCRIPTION", prescription.id());
		return prescription;
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<ClinicalPrescription> listClinicalPrescriptions(SessionPrincipal actor, UUID episodeId) {
		accessPolicy.requireVeterinarian(actor);
		setTrustedContext(actor);
		episodeOrNotFound(episodeId);
		return repository.listClinicalPrescriptions(episodeId);
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public ClinicalEpisodeAmendment amendClinicalEpisode(SessionPrincipal actor, UUID episodeId, AmendmentCommand command) {
		accessPolicy.requireVeterinarian(actor);
		setTrustedContext(actor);
		ClinicalEpisode episode = episodeOrNotFound(episodeId);
		petForClinicalWrite(episode.petId());
		if (!"FINALIZADO".equals(episode.status())) {
			throw conflict("B02_CLINICAL_FINALIZED_REQUIRED",
					"Solo un episodio finalizado puede recibir una rectificacion atribuible.");
		}
		ClinicalEpisodeAmendment amendment = repository.createClinicalEpisodeAmendment(actor.session().tenantId(),
				actor.session().accountId(), episode.id(), new AmendmentDraft(required(command.reason(), "reason"),
						required(command.addendum(), "addendum")))
				.orElseThrow(() -> conflict("B02_CLINICAL_AMENDMENT_CONCURRENT_CHANGE",
						"El episodio cambio antes de poder registrar la rectificacion."));
		repository.audit(actor.session().tenantId(), actor.session().accountId(), "CLINICAL_EPISODE_AMENDED",
				"CLINICAL_EPISODE_AMENDMENT", amendment.id());
		return amendment;
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<ClinicalEpisodeAmendment> listClinicalEpisodeAmendments(SessionPrincipal actor, UUID episodeId) {
		accessPolicy.requireVeterinarian(actor);
		setTrustedContext(actor);
		episodeOrNotFound(episodeId);
		return repository.listClinicalEpisodeAmendments(episodeId);
	}

	private void setTrustedContext(SessionPrincipal actor) {
		tenantContext.setTenantId(actor.session().tenantId());
		tenantContext.setPrincipalId(actor.session().accountId());
	}

	private OwnerProfile ownerOrNotFound(UUID ownerId) {
		return repository.findOwner(ownerId)
				.orElseThrow(() -> notFound("No se encontró el recurso solicitado."));
	}

	private OwnerProfile lockOwnerOrNotFound(UUID ownerId) {
		return repository.lockOwner(ownerId)
				.orElseThrow(() -> notFound("No se encontró el recurso solicitado."));
	}

	private PetProfile petOrNotFound(UUID petId) {
		return repository.findPet(petId)
				.orElseThrow(() -> notFound("No se encontró el recurso solicitado."));
	}

	private PetProfile lockPetOrNotFound(UUID petId) {
		return repository.lockPet(petId)
				.orElseThrow(() -> notFound("No se encontró el recurso solicitado."));
	}

	private PetProfile petForClinicalWrite(UUID petId) {
		PetProfile pet = lockPetOrNotFound(petId);
		if ("INACTIVA".equals(pet.status())) {
			throw conflict("B02_PET_INACTIVE", "No se puede añadir información clínica a una ficha inactiva por revisión de duplicado.");
		}
		return pet;
	}

	private OwnerPair lockOwnerPair(UUID duplicateOwnerId, UUID referenceOwnerId) {
		if (duplicateOwnerId.compareTo(referenceOwnerId) < 0) {
			OwnerProfile first = lockOwnerOrNotFound(duplicateOwnerId);
			OwnerProfile second = lockOwnerOrNotFound(referenceOwnerId);
			return new OwnerPair(first, second);
		}
		OwnerProfile first = lockOwnerOrNotFound(referenceOwnerId);
		OwnerProfile second = lockOwnerOrNotFound(duplicateOwnerId);
		return new OwnerPair(second, first);
	}

	private PetPair lockPetPair(UUID duplicatePetId, UUID referencePetId) {
		if (duplicatePetId.compareTo(referencePetId) < 0) {
			PetProfile first = lockPetOrNotFound(duplicatePetId);
			PetProfile second = lockPetOrNotFound(referencePetId);
			return new PetPair(first, second);
		}
		PetProfile first = lockPetOrNotFound(referencePetId);
		PetProfile second = lockPetOrNotFound(duplicatePetId);
		return new PetPair(second, first);
	}

	private String duplicateOutcome(String value) {
		String normalized = required(value, "outcome").toUpperCase(Locale.ROOT);
		if (!List.of("DESCARTADA", "CONFIRMADA_INACTIVA").contains(normalized)) {
			throw validation("outcome", "Indica si la señal se descarta o confirma la inactivación.");
		}
		return normalized;
	}

	private String duplicateResolutionReason(String value) {
		String reason = required(value, "reason");
		if (reason.length() < 3 || reason.length() > 500) {
			throw validation("reason", "El motivo debe tener entre 3 y 500 caracteres.");
		}
		return reason;
	}

	private OwnerProfile clientOwnerOrRequired(SessionPrincipal actor) {
		return repository.findOwnerForClient(actor.session().accountId())
				.orElseThrow(() -> conflict("B02_CLIENT_OWNER_LINK_REQUIRED",
						"La operacion requiere una vinculacion digital aprobada para la ficha propia."));
	}

	private ClientPetProfile clientPetOrNotFound(SessionPrincipal actor, UUID petId) {
		return repository.findCurrentPetForClient(actor.session().accountId(), petId)
				.orElseThrow(() -> notFound("No se encontro la ficha propia autorizada."));
	}

	private ClinicalEpisode episodeOrNotFound(UUID episodeId) {
		return repository.findClinicalEpisode(episodeId)
				.orElseThrow(() -> notFound("No se encontró el recurso solicitado."));
	}

	private ClientOwnerLinkStatus statusOf(ClientOwnerLinkRequest request) {
		return new ClientOwnerLinkStatus(request.status(), request.requestReference(), request.requestedAt(), request.resolvedAt(),
				request.rejectionReason());
	}

	private ClinicalDraft clinicalDraft(ClinicalEpisodeCommand command) {
		return new ClinicalDraft(command.appointmentId(), optional(command.reason()), optional(command.anamnesis()),
				optional(command.assessment()), optional(command.diagnosis()), optional(command.treatmentPlan()),
				optional(command.observations()));
	}

	private VaccinationDraft vaccinationDraft(VaccinationCommand command) {
		if (command.operationId() == null) {
			throw validation("operationId", "El identificador de operacion es obligatorio.");
		}
		if (command.administeredOn() == null) {
			throw validation("administeredOn", "La fecha de vacunacion es obligatoria.");
		}
		if (command.nextDueOn() != null && command.nextDueOn().isBefore(command.administeredOn())) {
			throw validation("nextDueOn", "La proxima dosis no puede ser anterior a la vacunacion.");
		}
		return new VaccinationDraft(command.episodeId(), command.operationId(), required(command.vaccineName(), "vaccineName"),
				command.administeredOn(), optional(command.dose()), optional(command.batchCode()), command.nextDueOn());
	}

	private VaccinationRecord vaccinationForRetry(UUID tenantId, UUID petId, VaccinationDraft expected) {
		VaccinationRecord existing = repository.findVaccinationByOperationId(tenantId, expected.operationId())
				.orElseThrow(() -> conflict("B02_VACCINATION_OPERATION_CONCURRENT_CHANGE",
						"No fue posible recuperar la vacunacion creada durante el reintento."));
		if (existing.petId().equals(petId) && Objects.equals(existing.episodeId(), expected.episodeId())
				&& existing.vaccineName().equals(expected.vaccineName())
				&& existing.administeredOn().equals(expected.administeredOn())
				&& Objects.equals(existing.dose(), expected.dose())
				&& Objects.equals(existing.batchCode(), expected.batchCode())
				&& Objects.equals(existing.nextDueOn(), expected.nextDueOn())) {
			return existing;
		}
		throw conflict("B02_VACCINATION_OPERATION_CONFLICT",
				"El identificador de operacion ya corresponde a otra vacunacion del tenant.");
	}

	private PrescriptionDraft prescriptionDraft(PrescriptionCommand command) {
		if (command.prescribedOn() == null) {
			throw validation("prescribedOn", "La fecha de prescripcion es obligatoria.");
		}
		return new PrescriptionDraft(required(command.medication(), "medication"),
				required(command.presentation(), "presentation"), required(command.dose(), "dose"),
				required(command.route(), "route"), required(command.frequency(), "frequency"),
				required(command.duration(), "duration"), required(command.instructions(), "instructions"), command.prescribedOn());
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
	public record VaccinationCommand(UUID episodeId, UUID operationId, String vaccineName, LocalDate administeredOn,
			String dose, String batchCode, LocalDate nextDueOn) { }
	public record PrescriptionCommand(String medication, String presentation, String dose, String route, String frequency,
			String duration, String instructions, LocalDate prescribedOn) { }
	public record AmendmentCommand(String reason, String addendum) { }
	public record ClientOwnerLinkResolutionCommand(String status, UUID ownerId, String verificationMethod,
			String rejectionReason, boolean identityVerified) { }
	public record DuplicateReviewCommand(UUID duplicateId, UUID referenceId) { }
	public record DuplicateResolutionCommand(String outcome, String reason) { }
	public record ClientOwnerLinkStatus(String status, String requestReference, java.time.Instant requestedAt,
			java.time.Instant resolvedAt, String rejectionReason) { }
	private record OwnerPair(OwnerProfile duplicate, OwnerProfile reference) { }
	private record PetPair(PetProfile duplicate, PetProfile reference) { }
}
