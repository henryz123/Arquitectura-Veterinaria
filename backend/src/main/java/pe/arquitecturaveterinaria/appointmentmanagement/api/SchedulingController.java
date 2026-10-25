package pe.arquitecturaveterinaria.appointmentmanagement.api;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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
import org.springframework.web.bind.annotation.RestController;

import pe.arquitecturaveterinaria.appointmentmanagement.application.AssignableProfessional;
import pe.arquitecturaveterinaria.appointmentmanagement.application.OfferedService;
import pe.arquitecturaveterinaria.appointmentmanagement.application.OperatingHours;
import pe.arquitecturaveterinaria.appointmentmanagement.application.ProfessionalAvailability;
import pe.arquitecturaveterinaria.appointmentmanagement.application.SchedulingService;
import pe.arquitecturaveterinaria.appointmentmanagement.application.SchedulingService.OfferedServiceCommand;
import pe.arquitecturaveterinaria.appointmentmanagement.application.SchedulingService.OfferedServiceUpdateCommand;
import pe.arquitecturaveterinaria.appointmentmanagement.application.SchedulingService.OperatingHoursCommand;
import pe.arquitecturaveterinaria.appointmentmanagement.application.SchedulingService.ProfessionalAvailabilityCommand;
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
}
