package pe.arquitecturaveterinaria.appointmentmanagement.api;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.arquitecturaveterinaria.appointmentmanagement.application.AssignableProfessional;
import pe.arquitecturaveterinaria.appointmentmanagement.application.SchedulingService;
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
}
