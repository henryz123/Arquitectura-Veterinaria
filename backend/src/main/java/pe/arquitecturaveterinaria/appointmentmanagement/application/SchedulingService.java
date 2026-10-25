package pe.arquitecturaveterinaria.appointmentmanagement.application;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.arquitecturaveterinaria.appointmentmanagement.infrastructure.SchedulingJdbcRepository;
import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.TenantContextJdbc;
import pe.arquitecturaveterinaria.shared.api.ApiException;
import org.springframework.http.HttpStatus;

@Service
public class SchedulingService {

	private final SchedulingJdbcRepository repository;
	private final TenantContextJdbc tenantContext;
	private final SchedulingAccessPolicy accessPolicy = new SchedulingAccessPolicy();

	public SchedulingService(SchedulingJdbcRepository repository, TenantContextJdbc tenantContext) {
		this.repository = repository;
		this.tenantContext = tenantContext;
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
}
