package pe.arquitecturaveterinaria.appointmentmanagement.api;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.arquitecturaveterinaria.appointmentmanagement.application.AssignableProfessional;
import pe.arquitecturaveterinaria.appointmentmanagement.application.OfferedService;
import pe.arquitecturaveterinaria.appointmentmanagement.application.OperatingHours;
import pe.arquitecturaveterinaria.appointmentmanagement.application.ProfessionalAvailability;
import pe.arquitecturaveterinaria.appointmentmanagement.application.ScheduleAvailability;
import pe.arquitecturaveterinaria.appointmentmanagement.application.ScheduleRequest;
import pe.arquitecturaveterinaria.appointmentmanagement.application.ScheduleReservation;
import pe.arquitecturaveterinaria.appointmentmanagement.application.SchedulingService;
import pe.arquitecturaveterinaria.appointmentmanagement.application.SchedulingService.OfferedServiceCommand;
import pe.arquitecturaveterinaria.appointmentmanagement.application.SchedulingService.OfferedServiceUpdateCommand;
import pe.arquitecturaveterinaria.appointmentmanagement.application.SchedulingService.OperatingHoursCommand;
import pe.arquitecturaveterinaria.appointmentmanagement.application.SchedulingService.ProfessionalAvailabilityCommand;
import pe.arquitecturaveterinaria.appointmentmanagement.application.SchedulingService.AvailabilityCommand;
import pe.arquitecturaveterinaria.appointmentmanagement.application.SchedulingService.ClientScheduleRequestCommand;
import pe.arquitecturaveterinaria.appointmentmanagement.application.SchedulingService.ConfirmationCommand;
import pe.arquitecturaveterinaria.appointmentmanagement.application.SchedulingService.CancellationCommand;
import pe.arquitecturaveterinaria.appointmentmanagement.application.SchedulingService.CancellationResolutionCommand;
import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.shared.api.ApiEnvelope;

@RestController
@RequestMapping("/api/v1")
public class SchedulingController {

	private final SchedulingService schedulingService;

	public SchedulingController(SchedulingService schedulingService) {
		this.schedulingService = schedulingService;
	}

	@GetMapping("/schedule/assignable-professionals")
	ApiEnvelope<List<AssignableProfessional>> listAssignableProfessionals(
			@AuthenticationPrincipal SessionPrincipal principal) {
		return ApiEnvelope.of(schedulingService.listAssignableProfessionals(principal));
	}

	@GetMapping("/schedule/services")
	ApiEnvelope<List<OfferedService>> listOfferedServices(@AuthenticationPrincipal SessionPrincipal principal) {
		return ApiEnvelope.of(schedulingService.listOfferedServices(principal));
	}

	@PostMapping("/schedule/services")
	ResponseEntity<ApiEnvelope<OfferedService>> createOfferedService(@AuthenticationPrincipal SessionPrincipal principal,
			@Valid @RequestBody ServiceRequest request) {
		OfferedService service = schedulingService.createOfferedService(principal,
				new OfferedServiceCommand(request.serviceType(), request.name(), request.publicDescription(), request.durationMinutes(),
						request.referencePrice(), request.published()));
		return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/schedule/services/" + service.id()))
				.body(ApiEnvelope.of(service));
	}

	@PutMapping("/schedule/services/{serviceId}")
	ApiEnvelope<OfferedService> updateOfferedService(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID serviceId,
			@Valid @RequestBody ServiceUpdateRequest request) {
		return ApiEnvelope.of(schedulingService.updateOfferedService(principal, serviceId,
				new OfferedServiceUpdateCommand(request.name(), request.publicDescription(), request.durationMinutes(),
						request.referencePrice(), request.published(), request.status())));
	}

	@GetMapping("/schedule/operating-hours")
	ApiEnvelope<List<OperatingHours>> listOperatingHours(@AuthenticationPrincipal SessionPrincipal principal) {
		return ApiEnvelope.of(schedulingService.listOperatingHours(principal));
	}

	@PostMapping("/schedule/operating-hours")
	ResponseEntity<ApiEnvelope<OperatingHours>> createOperatingHours(@AuthenticationPrincipal SessionPrincipal principal,
			@Valid @RequestBody OperatingHoursRequest request) {
		OperatingHours operatingHours = schedulingService.createOperatingHours(principal,
				new OperatingHoursCommand(request.weekday(), request.startsAtLocal(), request.endsAtLocal()));
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(URI.create("/api/v1/schedule/operating-hours/" + operatingHours.id()))
				.body(ApiEnvelope.of(operatingHours));
	}

	@GetMapping("/schedule/professional-availability")
	ApiEnvelope<List<ProfessionalAvailability>> listProfessionalAvailability(
			@AuthenticationPrincipal SessionPrincipal principal) {
		return ApiEnvelope.of(schedulingService.listProfessionalAvailability(principal));
	}

	@PostMapping("/schedule/professional-availability")
	ResponseEntity<ApiEnvelope<ProfessionalAvailability>> createProfessionalAvailability(
			@AuthenticationPrincipal SessionPrincipal principal, @Valid @RequestBody ProfessionalAvailabilityRequest request) {
		ProfessionalAvailability availability = schedulingService.createProfessionalAvailability(principal,
				new ProfessionalAvailabilityCommand(request.professionalPrincipalId(), request.weekday(), request.startsAtLocal(),
						request.endsAtLocal()));
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(URI.create("/api/v1/schedule/professional-availability/" + availability.id()))
				.body(ApiEnvelope.of(availability));
	}

	@GetMapping("/schedule/requests")
	ApiEnvelope<List<ScheduleRequest>> listScheduleRequests(@AuthenticationPrincipal SessionPrincipal principal) {
		return ApiEnvelope.of(schedulingService.listScheduleRequests(principal));
	}

	@GetMapping("/schedule/availability")
	ApiEnvelope<ScheduleAvailability> checkAvailability(@AuthenticationPrincipal SessionPrincipal principal,
			@RequestParam UUID serviceId, @RequestParam UUID professionalPrincipalId, @RequestParam Instant startsAt) {
		return ApiEnvelope.of(schedulingService.checkAvailability(principal,
				new AvailabilityCommand(serviceId, professionalPrincipalId, startsAt)));
	}

	@PostMapping("/schedule/requests/{requestId}/confirmation")
	ApiEnvelope<ScheduleReservation> confirmScheduleRequest(@AuthenticationPrincipal SessionPrincipal principal,
			@PathVariable UUID requestId, @Valid @RequestBody ConfirmationRequest request) {
		return ApiEnvelope.of(schedulingService.confirmScheduleRequest(principal, requestId,
				new ConfirmationCommand(request.professionalPrincipalId())));
	}

	@PostMapping("/schedule/requests/{requestId}/cancellation-resolution")
	ApiEnvelope<ScheduleRequest> resolveCancellation(@AuthenticationPrincipal SessionPrincipal principal,
			@PathVariable UUID requestId, @Valid @RequestBody CancellationResolutionRequest request) {
		return ApiEnvelope.of(schedulingService.resolveCancellation(principal, requestId,
				new CancellationResolutionCommand(request.decision(), request.reason())));
	}

	@GetMapping("/client/schedule/services")
	ApiEnvelope<List<OfferedService>> listClientScheduleServices(@AuthenticationPrincipal SessionPrincipal principal) {
		return ApiEnvelope.of(schedulingService.listPublishedServicesForClient(principal));
	}

	@GetMapping("/client/schedule/requests")
	ApiEnvelope<List<ScheduleRequest>> listClientScheduleRequests(@AuthenticationPrincipal SessionPrincipal principal) {
		return ApiEnvelope.of(schedulingService.listClientScheduleRequests(principal));
	}

	@PostMapping("/client/schedule/requests")
	ResponseEntity<ApiEnvelope<ScheduleRequest>> createClientScheduleRequest(@AuthenticationPrincipal SessionPrincipal principal,
			@Valid @RequestBody ClientScheduleRequest request) {
		ScheduleRequest created = schedulingService.createClientScheduleRequest(principal,
				new ClientScheduleRequestCommand(request.petId(), request.serviceId(), request.requestedStartsAt(), request.observation(),
						request.idempotencyKey()));
		return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/client/schedule/requests/" + created.id()))
				.body(ApiEnvelope.of(created));
	}

	@PostMapping("/client/schedule/requests/{requestId}/cancellation")
	ApiEnvelope<ScheduleRequest> cancelClientScheduleRequest(@AuthenticationPrincipal SessionPrincipal principal,
			@PathVariable UUID requestId, @Valid @RequestBody ClientCancellationRequest request) {
		return ApiEnvelope.of(schedulingService.cancelClientScheduleRequest(principal, requestId,
				new CancellationCommand(request.reason())));
	}

	record ServiceRequest(
			@NotBlank @Pattern(regexp = "CLINICA|GROOMING") String serviceType,
			@NotBlank @Size(max = 160) String name,
			@Size(max = 1000) String publicDescription,
			@Min(15) @Max(480) int durationMinutes,
			@NotNull @DecimalMin("0.00") BigDecimal referencePrice,
			boolean published) {
	}

	record ServiceUpdateRequest(
			@NotBlank @Size(max = 160) String name,
			@Size(max = 1000) String publicDescription,
			@Min(15) @Max(480) int durationMinutes,
			@NotNull @DecimalMin("0.00") BigDecimal referencePrice,
			boolean published,
			@NotBlank @Pattern(regexp = "ACTIVO|INACTIVO") String status) {
	}

	record OperatingHoursRequest(
			@Min(1) @Max(7) int weekday,
			@NotNull LocalTime startsAtLocal,
			@NotNull LocalTime endsAtLocal) {
	}

	record ProfessionalAvailabilityRequest(
			@NotNull UUID professionalPrincipalId,
			@Min(1) @Max(7) int weekday,
			@NotNull LocalTime startsAtLocal,
			@NotNull LocalTime endsAtLocal) {
	}

	record ConfirmationRequest(@NotNull UUID professionalPrincipalId) {
	}

	record CancellationResolutionRequest(
			@NotBlank @Pattern(regexp = "CANCELADA|CONFIRMADA") String decision,
			@NotBlank @Size(min = 3, max = 500) String reason) {
	}

	record ClientScheduleRequest(
			@NotNull UUID petId,
			@NotNull UUID serviceId,
			@NotNull @Future Instant requestedStartsAt,
			@Size(max = 1000) String observation,
			@NotNull UUID idempotencyKey) {
	}

	record ClientCancellationRequest(@Size(max = 500) String reason) {
	}
}
