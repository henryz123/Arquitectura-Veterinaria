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
	public List<ScheduleRequest> listScheduleRequests(SessionPrincipal actor) {
		accessPolicy.requireSchedulingManager(actor);
		setTrustedContext(actor);
		return repository.listScheduleRequests();
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
		if (!"PENDIENTE".equals(request.status())) {
			throw conflict("B03_CONFIRMATION_STATE", "Solo se pueden confirmar solicitudes pendientes.");
		}
		OfferedService service = activeService(request.serviceId(), false);
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
		if (repository.hasCurrentReservationConflict(professionalPrincipalId, interval.startsAt(), interval.endsAt())) {
			return unavailable(professionalPrincipalId, service.id(), interval, "PROFESSIONAL_INTERVAL_CONFLICT");
		}
		return new ScheduleAvailability(professionalPrincipalId, service.id(), interval.startsAt(), interval.endsAt(), true, null);
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

	private record Interval(Instant startsAt, Instant endsAt, int weekday, LocalTime startsAtLocal, LocalTime endsAtLocal) {
	}
}
