package pe.arquitecturaveterinaria.appointmentmanagement.application;

import java.util.Set;

import org.springframework.http.HttpStatus;

import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.domain.AccountType;
import pe.arquitecturaveterinaria.shared.api.ApiException;

/**
 * B03 authorization is derived only from a session resolved by the server.
 * Angular never supplies the tenant or role used for this decision.
 */
public final class SchedulingAccessPolicy {

	private static final Set<String> SCHEDULING_ROLES = Set.of("ADMIN_VETERINARIA", "RECEPCION");

	public void requireSchedulingManager(SessionPrincipal actor) {
		if (actor == null || actor.accountType() != AccountType.STAFF || actor.session().tenantId() == null) {
			throw new ApiException(HttpStatus.FORBIDDEN, "B03_STAFF_REQUIRED",
					"La operaciÃ³n requiere personal autorizado de la veterinaria.");
		}
		if (!"ACTIVO".equals(actor.session().tenantStatus())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "TENANT_NOT_ACTIVE",
					"La veterinaria no estÃ¡ disponible para esta operaciÃ³n.");
		}
		if (actor.session().roles().stream().noneMatch(SCHEDULING_ROLES::contains)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "B03_SCHEDULING_ROLE_REQUIRED",
					"Se requiere administraciÃ³n o recepciÃ³n para gestionar la agenda.");
		}
	}
}
