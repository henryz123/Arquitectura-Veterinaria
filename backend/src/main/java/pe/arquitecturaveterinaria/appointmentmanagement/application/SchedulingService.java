package pe.arquitecturaveterinaria.appointmentmanagement.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.arquitecturaveterinaria.appointmentmanagement.infrastructure.SchedulingJdbcRepository;
import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.TenantContextJdbc;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.ClientPetProfile;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.OwnerProfile;
import pe.arquitecturaveterinaria.shared.api.ApiException;

@Service
public class SchedulingService {

	private final SchedulingJdbcRepository repository;
	private final TenantContextJdbc tenantContext;
	private final OwnerPetCareService ownerPetCareService;
	private final SchedulingAccessPolicy accessPolicy = new SchedulingAccessPolicy();
	private static final ZoneId LIMA = ZoneId.of("America/Lima");

	public SchedulingService(SchedulingJdbcRepository repository, TenantContextJdbc tenantContext,
			OwnerPetCareService ownerPetCareService) {
		this.repository = repository;
		this.tenantContext = tenantContext;
		this.ownerPetCareService = ownerPetCareService;
	}

	/**
	 * PostgreSQL receives only the hash resolved from the authenticated cookie.
	 * V020 verifies the session, role, tenant and target eligibility again at the
	 * database boundary.
	 */
	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<AssignableProfessional> listAssignableProfessionals(SessionPrincipal actor) {
		accessPolicy.requireSchedulingManager(actor);
		return repository.listAssignableProfessionals(actor.session().sessionTokenHash());
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<OfferedService> listOfferedServices(SessionPrincipal actor) {
		accessPolicy.requireSchedulingManager(actor);
		setTrustedContext(actor);
		return repository.listOfferedServices();
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public OfferedService createOfferedService(SessionPrincipal actor, OfferedServiceCommand command) {
		accessPolicy.requireSchedulingManager(actor);
		setTrustedContext(actor);
		return repository.createOfferedService(actor.session().tenantId(), actor.session().accountId(), serviceDraft(command));
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public OfferedService updateOfferedService(SessionPrincipal actor, UUID serviceId, OfferedServiceUpdateCommand command) {
		accessPolicy.requireSchedulingManager(actor);
		setTrustedContext(actor);
		return repository.updateOfferedService(actor.session().tenantId(), serviceId, serviceUpdateDraft(command))
				.orElseThrow(() -> notFound("No se encontro el servicio de agenda solicitado."));
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<OperatingHours> listOperatingHours(SessionPrincipal actor) {
		accessPolicy.requireSchedulingManager(actor);
		setTrustedContext(actor);
		return repository.listOperatingHours();
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public OperatingHours createOperatingHours(SessionPrincipal actor, OperatingHoursCommand command) {
		accessPolicy.requireSchedulingManager(actor);
		setTrustedContext(actor);
		validateWeeklyRange(command.weekday(), command.startsAtLocal(), command.endsAtLocal());
		return repository.createOperatingHours(actor.session().tenantId(), actor.session().accountId(), command.weekday(),
				command.startsAtLocal(), command.endsAtLocal());
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<ProfessionalAvailability> listProfessionalAvailability(SessionPrincipal actor) {
		accessPolicy.requireSchedulingManager(actor);
		setTrustedContext(actor);
		return repository.listProfessionalAvailability();
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public ProfessionalAvailability createProfessionalAvailability(SessionPrincipal actor,
			ProfessionalAvailabilityCommand command) {
		accessPolicy.requireSchedulingManager(actor);
		setTrustedContext(actor);
		if (command.professionalPrincipalId() == null || repository.listAssignableProfessionals(actor.session().sessionTokenHash())
				.stream().noneMatch(professional -> professional.principalId().equals(command.professionalPrincipalId()))) {
			throw forbidden("B03_PROFESSIONAL_NOT_ASSIGNABLE",
					"El profesional debe permanecer activo y tener un rol vigente para recibir disponibilidad.");
		}
		validateWeeklyRange(command.weekday(), command.startsAtLocal(), command.endsAtLocal());
		return repository.createProfessionalAvailability(actor.session().tenantId(), actor.session().accountId(),
				command.professionalPrincipalId(), command.weekday(), command.startsAtLocal(), command.endsAtLocal());
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<OfferedService> listPublishedServicesForClient(SessionPrincipal actor) {
		accessPolicy.requireClient(actor);
		setTrustedContext(actor);
		return repository.listPublishedOfferedServices();
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public ScheduleRequest createClientScheduleRequest(SessionPrincipal actor, ClientScheduleRequestCommand command) {
		accessPolicy.requireClient(actor);
		setTrustedContext(actor);
		if (command == null || command.petId() == null || command.serviceId() == null || command.idempotencyKey() == null) {
			throw validation("request", "Completa la mascota, el servicio y la clave de reintento de la solicitud.");
		}
		OwnerProfile owner = ownerPetCareService.getClientOwnerProfile(actor);
		ClientPetProfile pet = ownerPetCareService.getClientPet(actor, command.petId());
		if (!"ACTIVO".equals(owner.status()) || !"ACTIVA".equals(pet.status())) {
			throw conflict("B03_PET_NOT_SCHEDULABLE", "La mascota y su responsable deben estar activos para solicitar una cita.");
		}
		OfferedService offeredService = activeService(command.serviceId(), true);
		Interval interval = interval(command.requestedStartsAt(), offeredService.durationMinutes());
		String observation = optional(command.observation(), 1000);
		return repository.createScheduleRequest(actor.session().tenantId(), actor.session().accountId(), owner.id(), pet.id(),
				offeredService.id(), interval.startsAt(), offeredService.durationMinutes(), observation, command.idempotencyKey())
				.map(created -> {
			repository.recordScheduleChange(actor.session().tenantId(), created.id(), actor.session().accountId(),
					"SOLICITUD_CREADA", null, created.requestedStartsAt(), null, null, null);
			return created;
				})
				.orElseGet(() -> repository.findScheduleRequestByIdempotency(actor.session().accountId(), command.idempotencyKey())
						.orElseThrow(() -> conflict("B03_IDEMPOTENCY_STATE", "No se pudo recuperar la solicitud idempotente.")));
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<ScheduleRequest> listClientScheduleRequests(SessionPrincipal actor) {
		accessPolicy.requireClient(actor);
		setTrustedContext(actor);
		return repository.listScheduleRequestsForRequester(actor.session().accountId());
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<ClientScheduleProposal> listClientScheduleProposals(SessionPrincipal actor) {
		accessPolicy.requireClient(actor);
		setTrustedContext(actor);
		return repository.listOpenScheduleProposalsForRequester(actor.session().accountId());
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<ScheduleRequest> listScheduleRequests(SessionPrincipal actor) {
		accessPolicy.requireSchedulingManager(actor);
		setTrustedContext(actor);
		return repository.listScheduleRequests();
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public ScheduleRequest cancelClientScheduleRequest(SessionPrincipal actor, UUID requestId, CancellationCommand command) {
		accessPolicy.requireClient(actor);
		setTrustedContext(actor);
		ScheduleRequest request = repository.lockScheduleRequest(requestId)
				.orElseThrow(() -> notFound("No se encontro la solicitud de agenda."));
		if (!request.requesterPrincipalId().equals(actor.session().accountId())) {
			throw notFound("No se encontro la solicitud de agenda.");
		}
		String reason = optional(command == null ? null : command.reason(), 500);
		if ("PENDIENTE".equals(request.status()) || "PROPUESTA".equals(request.status())) {
			if (repository.transitionScheduleRequest(actor.session().tenantId(), request.id(), request.status(), "CANCELADA") != 1) {
				throw conflict("B03_CANCELLATION_STATE", "La solicitud cambio antes de cancelar.");
			}
			repository.closeOpenProposals(actor.session().tenantId(), request.id());
			repository.recordScheduleChange(actor.session().tenantId(), request.id(), actor.session().accountId(), "SOLICITUD_CANCELADA",
					request.requestedStartsAt(), null, null, null, reason);
			return repository.findScheduleRequest(request.id()).orElseThrow();
		}
		if ("CONFIRMADA".equals(request.status()) || "REPROGRAMACION_PROPUESTA".equals(request.status())
				|| "PENDIENTE_REPROGRAMACION".equals(request.status())) {
			ScheduleReservation reservation = repository.findCurrentReservation(request.id())
					.orElseThrow(() -> conflict("B03_CANCELLATION_STATE", "La cita confirmada no conserva una reserva vigente."));
			if (repository.transitionScheduleRequest(actor.session().tenantId(), request.id(), request.status(), "CANCELACION_SOLICITADA") != 1) {
				throw conflict("B03_CANCELLATION_STATE", "La cita cambio antes de solicitar la cancelacion.");
			}
			repository.closeOpenProposals(actor.session().tenantId(), request.id());
			repository.recordScheduleChange(actor.session().tenantId(), request.id(), actor.session().accountId(), "CANCELACION_SOLICITADA",
					reservation.startsAt(), reservation.startsAt(), reservation.professionalPrincipalId(), reservation.professionalPrincipalId(), reason);
			return repository.findScheduleRequest(request.id()).orElseThrow();
		}
		throw conflict("B03_CANCELLATION_STATE", "El cliente no puede cancelar esta solicitud en su estado actual.");
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public ScheduleRequest resolveCancellation(SessionPrincipal actor, UUID requestId, CancellationResolutionCommand command) {
		accessPolicy.requireSchedulingManager(actor);
		setTrustedContext(actor);
		if (command == null || (!"CANCELADA".equals(command.decision()) && !"CONFIRMADA".equals(command.decision()))) {
			throw validation("cancellation", "La resolucion debe cancelar o mantener confirmada la cita.");
		}
		String reason = required(command.reason(), "reason", 500);
		ScheduleRequest request = repository.lockScheduleRequest(requestId)
				.orElseThrow(() -> notFound("No se encontro la solicitud de agenda."));
		if (!"CANCELACION_SOLICITADA".equals(request.status())) {
			throw conflict("B03_CANCELLATION_STATE", "La solicitud no tiene una cancelacion pendiente de resolucion.");
		}
		ScheduleReservation reservation = repository.findCurrentReservation(request.id())
				.orElseThrow(() -> conflict("B03_CANCELLATION_STATE", "La cita no conserva la reserva requerida para resolver la cancelacion."));
		if ("CANCELADA".equals(command.decision())) {
			reservation = repository.changeCurrentReservationStatus(actor.session().tenantId(), request.id(), "CONFIRMADA", "CANCELADA")
					.orElseThrow(() -> conflict("B03_CANCELLATION_STATE", "La reserva cambio antes de cancelar."));
		}
		if (repository.transitionScheduleRequest(actor.session().tenantId(), request.id(), "CANCELACION_SOLICITADA", command.decision()) != 1) {
			throw conflict("B03_CANCELLATION_STATE", "La solicitud cambio antes de resolver la cancelacion.");
		}
		repository.recordScheduleChange(actor.session().tenantId(), request.id(), actor.session().accountId(),
				"CANCELADA".equals(command.decision()) ? "CANCELACION_APROBADA" : "CANCELACION_RECHAZADA",
				reservation.startsAt(), "CANCELADA".equals(command.decision()) ? null : reservation.startsAt(),
				reservation.professionalPrincipalId(), "CANCELADA".equals(command.decision()) ? null : reservation.professionalPrincipalId(), reason);
		return repository.findScheduleRequest(request.id()).orElseThrow();
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public ScheduleProposal proposeScheduleAlternative(SessionPrincipal actor, UUID requestId, ScheduleProposalCommand command) {
		accessPolicy.requireSchedulingManager(actor);
		setTrustedContext(actor);
		if (command == null || command.proposedStartsAt() == null || command.professionalPrincipalId() == null) {
			throw validation("proposal", "Selecciona una fecha futura y un profesional para la propuesta.");
		}
		String reason = required(command.reason(), "reason", 500);
		ScheduleRequest request = repository.lockScheduleRequest(requestId)
				.orElseThrow(() -> notFound("No se encontro la solicitud de agenda."));
		boolean reprogramming = "CONFIRMADA".equals(request.status()) || "REPROGRAMACION_PROPUESTA".equals(request.status());
		boolean pending = "PENDIENTE".equals(request.status()) || "PROPUESTA".equals(request.status());
		if (!reprogramming && !pending) {
			throw conflict("B03_PROPOSAL_STATE", "La solicitud no admite una contrapropuesta en su estado actual.");
		}
		OfferedService service = activeService(request.serviceId(), false);
		Interval proposedInterval = interval(command.proposedStartsAt(), request.requestedDurationMinutes());
		ScheduleReservation currentReservation = reprogramming ? repository.findCurrentReservation(request.id())
				.orElseThrow(() -> conflict("B03_PROPOSAL_STATE", "La reprogramacion no conserva una reserva vigente.")) : null;
		lockProfessionalSchedules(currentReservation == null ? null : currentReservation.professionalPrincipalId(), command.professionalPrincipalId());
		ScheduleAvailability availability = evaluateAvailability(actor, service, command.professionalPrincipalId(), proposedInterval,
				reprogramming ? request.id() : null);
		if (!availability.available()) {
			throw conflict("B03_SLOT_UNAVAILABLE", "El intervalo propuesto ya no esta disponible: " + availability.reasonCode());
		}
		repository.closeOpenProposals(actor.session().tenantId(), request.id());
		String nextStatus = reprogramming ? "REPROGRAMACION_PROPUESTA" : "PROPUESTA";
		if (repository.transitionScheduleRequest(actor.session().tenantId(), request.id(), request.status(), nextStatus) != 1) {
			throw conflict("B03_PROPOSAL_STATE", "La solicitud cambio antes de registrar la propuesta.");
		}
		ScheduleProposal proposal = repository.createScheduleProposal(actor.session().tenantId(), request.id(), proposedInterval.startsAt(),
				request.requestedDurationMinutes(), command.professionalPrincipalId(), actor.session().accountId());
		repository.recordScheduleChange(actor.session().tenantId(), request.id(), actor.session().accountId(),
				reprogramming ? "REPROGRAMACION_PROPUESTA" : "CONTRAPROPUESTA_ENVIADA",
				currentReservation == null ? request.requestedStartsAt() : currentReservation.startsAt(), proposedInterval.startsAt(),
				currentReservation == null ? null : currentReservation.professionalPrincipalId(), command.professionalPrincipalId(), reason);
		return proposal;
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public ScheduleRequest decideClientScheduleProposal(SessionPrincipal actor, UUID proposalId,
			ClientProposalDecisionCommand command) {
		accessPolicy.requireClient(actor);
		setTrustedContext(actor);
		if (command == null || (!"ACEPTADA".equals(command.decision()) && !"RECHAZADA".equals(command.decision()))) {
			throw validation("proposal", "La propuesta debe aceptarse o rechazarse.");
		}
		ScheduleProposal reference = repository.findScheduleProposal(proposalId)
				.orElseThrow(() -> notFound("No se encontro la propuesta de horario."));
		ScheduleRequest request = repository.lockScheduleRequest(reference.requestId())
				.orElseThrow(() -> notFound("No se encontro la solicitud de agenda."));
		if (!request.requesterPrincipalId().equals(actor.session().accountId())) {
			throw notFound("No se encontro la propuesta de horario.");
		}
		ScheduleProposal proposal = repository.lockScheduleProposal(proposalId)
				.orElseThrow(() -> notFound("No se encontro la propuesta de horario."));
		if (!"ENVIADA".equals(proposal.status())) {
			throw conflict("B03_PROPOSAL_STATE", "La propuesta ya fue resuelta o reemplazada.");
		}
		if (proposal.proposedProfessionalPrincipalId() == null) {
			throw conflict("B03_PROPOSAL_STATE", "La propuesta no conserva un profesional revalidable.");
		}
		boolean reprogramming = "REPROGRAMACION_PROPUESTA".equals(request.status());
		if (!reprogramming && !"PROPUESTA".equals(request.status())) {
			throw conflict("B03_PROPOSAL_STATE", "La solicitud no conserva una propuesta vigente.");
		}
		ScheduleReservation currentReservation = reprogramming ? repository.findCurrentReservation(request.id())
				.orElseThrow(() -> conflict("B03_PROPOSAL_STATE", "La cita no conserva la reserva original.")) : null;
		Interval proposedInterval = interval(proposal.proposedStartsAt(), proposal.proposedDurationMinutes());
		if ("ACEPTADA".equals(command.decision())) {
			lockProfessionalSchedules(currentReservation == null ? null : currentReservation.professionalPrincipalId(),
					proposal.proposedProfessionalPrincipalId());
			if (repository.hasCurrentReservationConflictExceptRequest(proposal.proposedProfessionalPrincipalId(),
					proposedInterval.startsAt(), proposedInterval.endsAt(), request.id())) {
				throw conflict("B03_SLOT_UNAVAILABLE",
						"La alternativa ya fue ocupada. La cita vigente se conserva y el personal debe proponer otro horario.");
			}
		}
		if (repository.decideScheduleProposal(actor.session().tenantId(), proposal.id(), "ENVIADA", command.decision()) != 1) {
			throw conflict("B03_PROPOSAL_STATE", "La propuesta cambio antes de registrar la decision.");
		}
		if ("RECHAZADA".equals(command.decision())) {
			String nextStatus = reprogramming ? "CONFIRMADA" : "PENDIENTE";
			if (repository.transitionScheduleRequest(actor.session().tenantId(), request.id(), request.status(), nextStatus) != 1) {
				throw conflict("B03_PROPOSAL_STATE", "La solicitud cambio antes de rechazar la propuesta.");
			}
			repository.recordScheduleChange(actor.session().tenantId(), request.id(), actor.session().accountId(),
					reprogramming ? "REPROGRAMACION_RECHAZADA" : "CONTRAPROPUESTA_RECHAZADA",
					currentReservation == null ? request.requestedStartsAt() : currentReservation.startsAt(),
					currentReservation == null ? request.requestedStartsAt() : currentReservation.startsAt(),
					currentReservation == null ? null : currentReservation.professionalPrincipalId(),
					currentReservation == null ? null : currentReservation.professionalPrincipalId(), null);
			return repository.findScheduleRequest(request.id()).orElseThrow();
		}
		if (reprogramming) {
			if (repository.transitionScheduleRequest(actor.session().tenantId(), request.id(), "REPROGRAMACION_PROPUESTA",
					"PENDIENTE_REPROGRAMACION") != 1) {
				throw conflict("B03_PROPOSAL_STATE", "La solicitud cambio antes de aceptar la reprogramacion.");
			}
			repository.recordScheduleChange(actor.session().tenantId(), request.id(), actor.session().accountId(),
					"REPROGRAMACION_ACEPTADA", currentReservation.startsAt(), proposal.proposedStartsAt(),
					currentReservation.professionalPrincipalId(), proposal.proposedProfessionalPrincipalId(), null);
		} else {
			if (repository.transitionScheduleRequestWithTime(actor.session().tenantId(), request.id(), "PROPUESTA", "PENDIENTE",
					proposal.proposedStartsAt(), proposal.proposedDurationMinutes()) != 1) {
				throw conflict("B03_PROPOSAL_STATE", "La solicitud cambio antes de aceptar la contrapropuesta.");
			}
			repository.recordScheduleChange(actor.session().tenantId(), request.id(), actor.session().accountId(),
					"CONTRAPROPUESTA_ACEPTADA", request.requestedStartsAt(), proposal.proposedStartsAt(), null,
					proposal.proposedProfessionalPrincipalId(), null);
		}
		return repository.findScheduleRequest(request.id()).orElseThrow();
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public ScheduleReservation reassignConfirmedScheduleProfessional(SessionPrincipal actor, UUID requestId,
			ReassignmentCommand command) {
		accessPolicy.requireSchedulingManager(actor);
		setTrustedContext(actor);
		if (command == null || command.professionalPrincipalId() == null) {
			throw validation("professionalPrincipalId", "Selecciona el profesional que asumira la cita confirmada.");
		}
		String reason = required(command.reason(), "reason", 500);
		ScheduleRequest request = repository.lockScheduleRequest(requestId)
				.orElseThrow(() -> notFound("No se encontro la solicitud de agenda."));
		if (!"CONFIRMADA".equals(request.status())) {
			throw conflict("B03_REASSIGNMENT_STATE", "Solo se puede reasignar una cita confirmada sin cambio de horario.");
		}
		ScheduleReservation current = repository.findCurrentReservation(request.id())
				.orElseThrow(() -> conflict("B03_REASSIGNMENT_STATE", "La cita confirmada no conserva una reserva vigente."));
		if (current.professionalPrincipalId().equals(command.professionalPrincipalId())) {
			return current;
		}
		OfferedService service = activeService(request.serviceId(), false);
		Interval currentInterval = interval(current.startsAt(), request.requestedDurationMinutes());
		lockProfessionalSchedules(current.professionalPrincipalId(), command.professionalPrincipalId());
		ScheduleAvailability availability = evaluateAvailability(actor, service, command.professionalPrincipalId(), currentInterval,
				request.id());
		if (!availability.available()) {
			throw conflict("B03_SLOT_UNAVAILABLE", "El profesional seleccionado no puede asumir esta cita: " + availability.reasonCode());
		}
		ScheduleReservation reassigned = repository.rescheduleCurrentReservation(actor.session().tenantId(), request.id(), current.version(),
				command.professionalPrincipalId(), current.startsAt(), current.endsAt())
				.orElseThrow(() -> conflict("B03_REASSIGNMENT_STATE", "La reserva cambio antes de reasignar al profesional."));
		repository.recordScheduleChange(actor.session().tenantId(), request.id(), actor.session().accountId(), "PROFESIONAL_REASIGNADO",
				current.startsAt(), current.startsAt(), current.professionalPrincipalId(), command.professionalPrincipalId(), reason);
		return reassigned;
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public ScheduleAvailability checkAvailability(SessionPrincipal actor, AvailabilityCommand command) {
		accessPolicy.requireSchedulingManager(actor);
		setTrustedContext(actor);
		if (command == null || command.serviceId() == null || command.professionalPrincipalId() == null) {
			throw validation("availability", "Selecciona un servicio y un profesional para consultar disponibilidad.");
		}
		OfferedService service = activeService(command.serviceId(), false);
		return evaluateAvailability(actor, service, command.professionalPrincipalId(), interval(command.startsAt(), service.durationMinutes()));
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public ScheduleReservation confirmScheduleRequest(SessionPrincipal actor, UUID requestId,
			ConfirmationCommand command) {
		accessPolicy.requireSchedulingManager(actor);
		setTrustedContext(actor);
		if (requestId == null || command == null || command.professionalPrincipalId() == null) {
			throw validation("confirmation", "Selecciona el profesional que confirmara la reserva.");
		}
		ScheduleRequest request = repository.lockScheduleRequest(requestId)
				.orElseThrow(() -> notFound("No se encontro la solicitud de agenda."));
		if ("CONFIRMADA".equals(request.status())) {
			return repository.findCurrentReservation(request.id())
					.orElseThrow(() -> conflict("B03_CONFIRMATION_STATE", "La solicitud confirmada no conserva su reserva vigente."));
		}
		if (!"PENDIENTE".equals(request.status()) && !"PENDIENTE_REPROGRAMACION".equals(request.status())) {
			throw conflict("B03_CONFIRMATION_STATE", "Solo se pueden confirmar solicitudes pendientes.");
		}
		OfferedService service = activeService(request.serviceId(), false);
		if ("PENDIENTE_REPROGRAMACION".equals(request.status())) {
			ScheduleProposal proposal = repository.findLatestAcceptedScheduleProposal(request.id())
					.orElseThrow(() -> conflict("B03_CONFIRMATION_STATE", "La reprogramacion aceptada no conserva su propuesta."));
			ScheduleReservation previous = repository.findCurrentReservation(request.id())
					.orElseThrow(() -> conflict("B03_CONFIRMATION_STATE", "La reprogramacion no conserva su reserva original."));
			Interval interval = interval(proposal.proposedStartsAt(), proposal.proposedDurationMinutes());
			lockProfessionalSchedules(previous.professionalPrincipalId(), command.professionalPrincipalId());
			ScheduleAvailability availability = evaluateAvailability(actor, service, command.professionalPrincipalId(), interval, request.id());
			if (!availability.available()) {
				throw conflict("B03_SLOT_UNAVAILABLE", "La alternativa aceptada ya no esta disponible: " + availability.reasonCode());
			}
			ScheduleReservation updated = repository.rescheduleCurrentReservation(actor.session().tenantId(), request.id(), previous.version(),
					command.professionalPrincipalId(), interval.startsAt(), interval.endsAt())
					.orElseThrow(() -> conflict("B03_CONFIRMATION_STATE", "La reserva cambio antes de reprogramar."));
			if (repository.transitionScheduleRequestWithTime(actor.session().tenantId(), request.id(), "PENDIENTE_REPROGRAMACION",
					"CONFIRMADA", interval.startsAt(), proposal.proposedDurationMinutes()) != 1) {
				throw conflict("B03_CONFIRMATION_STATE", "La solicitud cambio antes de confirmar la reprogramacion.");
			}
			repository.recordScheduleChange(actor.session().tenantId(), request.id(), actor.session().accountId(), "CITA_REPROGRAMADA",
					previous.startsAt(), interval.startsAt(), previous.professionalPrincipalId(), command.professionalPrincipalId(), null);
			return updated;
		}
		Interval interval = interval(request.requestedStartsAt(), request.requestedDurationMinutes());
		repository.lockProfessionalSchedule(command.professionalPrincipalId());
		ScheduleAvailability availability = evaluateAvailability(actor, service, command.professionalPrincipalId(), interval);
		if (!availability.available()) {
			throw conflict("B03_SLOT_UNAVAILABLE", "El profesional ya no tiene un intervalo confirmable para esta solicitud: "
					+ availability.reasonCode());
		}
		if (repository.findCurrentReservation(request.id()).isPresent()) {
			throw conflict("B03_CONFIRMATION_STATE", "La solicitud ya conserva una reserva vigente.");
		}
		ScheduleReservation reservation = repository.createConfirmedReservation(actor.session().tenantId(), request.id(),
				command.professionalPrincipalId(), interval.startsAt(), interval.endsAt(), actor.session().accountId());
		if (repository.markScheduleRequestConfirmed(actor.session().tenantId(), request.id()) != 1) {
			throw conflict("B03_CONFIRMATION_STATE", "La solicitud cambio antes de confirmar la reserva.");
		}
		repository.recordScheduleChange(actor.session().tenantId(), request.id(), actor.session().accountId(), "CITA_CONFIRMADA",
				null, interval.startsAt(), null, command.professionalPrincipalId(), null);
		return reservation;
	}

	private ScheduleAvailability evaluateAvailability(SessionPrincipal actor, OfferedService service, UUID professionalPrincipalId,
			Interval interval) {
		return evaluateAvailability(actor, service, professionalPrincipalId, interval, null);
	}

	private ScheduleAvailability evaluateAvailability(SessionPrincipal actor, OfferedService service, UUID professionalPrincipalId,
			Interval interval, UUID excludedRequestId) {
		String requiredRole = "CLINICA".equals(service.serviceType()) ? "VETERINARIO" : "GROOMING";
		boolean assignable = repository.listAssignableProfessionals(actor.session().sessionTokenHash()).stream()
				.anyMatch(candidate -> candidate.principalId().equals(professionalPrincipalId)
						&& candidate.roles().contains(requiredRole));
		if (!assignable) {
			return unavailable(professionalPrincipalId, service.id(), interval, "PROFESSIONAL_NOT_ASSIGNABLE");
		}
		if (!repository.isWithinOperatingHours(interval.weekday(), interval.startsAtLocal(), interval.endsAtLocal())) {
			return unavailable(professionalPrincipalId, service.id(), interval, "OUTSIDE_OPERATING_HOURS");
		}
		if (!repository.isWithinProfessionalAvailability(professionalPrincipalId, interval.weekday(), interval.startsAtLocal(),
				interval.endsAtLocal())) {
			return unavailable(professionalPrincipalId, service.id(), interval, "OUTSIDE_PROFESSIONAL_AVAILABILITY");
		}
		if (repository.hasScheduleBlock(professionalPrincipalId, interval.startsAt(), interval.endsAt())) {
			return unavailable(professionalPrincipalId, service.id(), interval, "SCHEDULE_BLOCKED");
		}
		boolean hasConflict = excludedRequestId == null
				? repository.hasCurrentReservationConflict(professionalPrincipalId, interval.startsAt(), interval.endsAt())
				: repository.hasCurrentReservationConflictExceptRequest(professionalPrincipalId, interval.startsAt(), interval.endsAt(),
						excludedRequestId);
		if (hasConflict) {
			return unavailable(professionalPrincipalId, service.id(), interval, "PROFESSIONAL_INTERVAL_CONFLICT");
		}
		return new ScheduleAvailability(professionalPrincipalId, service.id(), interval.startsAt(), interval.endsAt(), true, null);
	}

	private void lockProfessionalSchedules(UUID firstProfessionalId, UUID secondProfessionalId) {
		if (firstProfessionalId == null || firstProfessionalId.equals(secondProfessionalId)) {
			repository.lockProfessionalSchedule(secondProfessionalId);
			return;
		}
		if (firstProfessionalId.toString().compareTo(secondProfessionalId.toString()) < 0) {
			repository.lockProfessionalSchedule(firstProfessionalId);
			repository.lockProfessionalSchedule(secondProfessionalId);
		} else {
			repository.lockProfessionalSchedule(secondProfessionalId);
			repository.lockProfessionalSchedule(firstProfessionalId);
		}
	}

	private ScheduleAvailability unavailable(UUID professionalPrincipalId, UUID serviceId, Interval interval, String reasonCode) {
		return new ScheduleAvailability(professionalPrincipalId, serviceId, interval.startsAt(), interval.endsAt(), false, reasonCode);
	}

	private OfferedService activeService(UUID serviceId, boolean requirePublished) {
		OfferedService service = repository.findOfferedService(serviceId)
				.orElseThrow(() -> notFound("No se encontro el servicio de agenda solicitado."));
		if (!"ACTIVO".equals(service.status()) || (requirePublished && !service.published())) {
			throw conflict("B03_SERVICE_NOT_AVAILABLE", "El servicio no esta disponible para esta operacion.");
		}
		return service;
	}

	private Interval interval(Instant startsAt, int durationMinutes) {
		if (startsAt == null || !startsAt.isAfter(Instant.now())) {
			throw validation("requestedStartsAt", "La cita debe solicitarse para una fecha futura.");
		}
		int duration = duration(durationMinutes);
		Instant endsAt = startsAt.plus(Duration.ofMinutes(duration));
		LocalDateTime localStart = startsAt.atZone(LIMA).toLocalDateTime();
		LocalDateTime localEnd = endsAt.atZone(LIMA).toLocalDateTime();
		if (!localStart.toLocalDate().equals(localEnd.toLocalDate())) {
			throw validation("requestedStartsAt", "V1 registra solicitudes dentro de un mismo dÃ­a operativo local.");
		}
		return new Interval(startsAt, endsAt, localStart.getDayOfWeek().getValue(), localStart.toLocalTime(),
				localEnd.toLocalTime());
	}

	private OfferedServiceDraft serviceDraft(OfferedServiceCommand command) {
		if (command == null) {
			throw validation("service", "Se requiere la informacion del servicio.");
		}
		return new OfferedServiceDraft(serviceType(command.serviceType()), required(command.name(), "name", 160),
				optional(command.publicDescription(), 1000), duration(command.durationMinutes()), price(command.referencePrice()),
				command.published());
	}

	private OfferedServiceUpdateDraft serviceUpdateDraft(OfferedServiceUpdateCommand command) {
		if (command == null) {
			throw validation("service", "Se requiere la informacion del servicio.");
		}
		String status = command.status();
		if (!"ACTIVO".equals(status) && !"INACTIVO".equals(status)) {
			throw validation("status", "El estado del servicio no es valido.");
		}
		return new OfferedServiceUpdateDraft(required(command.name(), "name", 160), optional(command.publicDescription(), 1000),
				duration(command.durationMinutes()), price(command.referencePrice()), command.published(), status);
	}

	private void validateWeeklyRange(int weekday, LocalTime startsAtLocal, LocalTime endsAtLocal) {
		if (weekday < 1 || weekday > 7) {
			throw validation("weekday", "El dia de disponibilidad debe estar entre lunes y domingo.");
		}
		if (startsAtLocal == null || endsAtLocal == null || !endsAtLocal.isAfter(startsAtLocal)) {
			throw validation("endsAtLocal", "La hora final debe ser posterior a la hora inicial.");
		}
	}

	private String serviceType(String value) {
		if (!"CLINICA".equals(value) && !"GROOMING".equals(value)) {
			throw validation("serviceType", "El tipo de servicio debe ser CLINICA o GROOMING.");
		}
		return value;
	}

	private int duration(int value) {
		if (value < 15 || value > 480 || value % 15 != 0) {
			throw validation("durationMinutes", "La duracion debe estar entre 15 y 480 minutos, en intervalos de 15.");
		}
		return value;
	}

	private BigDecimal price(BigDecimal value) {
		if (value == null || value.signum() < 0) {
			throw validation("referencePrice", "El precio referencial no puede ser negativo.");
		}
		return value;
	}

	private String required(String value, String field, int maxLength) {
		String normalized = optional(value, maxLength);
		if (normalized == null) {
			throw validation(field, "Este dato es obligatorio.");
		}
		return normalized;
	}

	private String optional(String value, int maxLength) {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		String normalized = value.trim();
		if (normalized.length() > maxLength) {
			throw validation("value", "El texto supera la longitud permitida.");
		}
		return normalized;
	}

	private void setTrustedContext(SessionPrincipal actor) {
		tenantContext.setTenantId(actor.session().tenantId());
		tenantContext.setPrincipalId(actor.session().accountId());
	}

	private ApiException validation(String code, String message) {
		return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "B03_" + code.toUpperCase(), message);
	}

	private ApiException forbidden(String code, String message) {
		return new ApiException(HttpStatus.FORBIDDEN, code, message);
	}

	private ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}

	private ApiException notFound(String message) {
		return new ApiException(HttpStatus.NOT_FOUND, "B03_NOT_FOUND", message);
	}

	public record OfferedServiceCommand(String serviceType, String name, String publicDescription, int durationMinutes,
			BigDecimal referencePrice, boolean published) {
	}

	public record OfferedServiceUpdateCommand(String name, String publicDescription, int durationMinutes,
			BigDecimal referencePrice, boolean published, String status) {
	}

	public record OperatingHoursCommand(int weekday, LocalTime startsAtLocal, LocalTime endsAtLocal) {
	}

	public record ProfessionalAvailabilityCommand(UUID professionalPrincipalId, int weekday, LocalTime startsAtLocal,
			LocalTime endsAtLocal) {
	}

	public record ClientScheduleRequestCommand(UUID petId, UUID serviceId, Instant requestedStartsAt, String observation,
			UUID idempotencyKey) {
	}

	public record AvailabilityCommand(UUID serviceId, UUID professionalPrincipalId, Instant startsAt) {
	}

	public record ConfirmationCommand(UUID professionalPrincipalId) {
	}

	public record CancellationCommand(String reason) {
	}

	public record CancellationResolutionCommand(String decision, String reason) {
	}

	public record ScheduleProposalCommand(Instant proposedStartsAt, UUID professionalPrincipalId, String reason) {
	}

	public record ClientProposalDecisionCommand(String decision) {
	}

	public record ReassignmentCommand(UUID professionalPrincipalId, String reason) {
	}

	private record Interval(Instant startsAt, Instant endsAt, int weekday, LocalTime startsAtLocal, LocalTime endsAtLocal) {
	}
}
