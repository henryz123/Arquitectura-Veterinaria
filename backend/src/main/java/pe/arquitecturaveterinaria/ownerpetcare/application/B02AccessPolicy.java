package pe.arquitecturaveterinaria.ownerpetcare.application;

import java.util.Set;

import org.springframework.http.HttpStatus;

import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.domain.AccountType;
import pe.arquitecturaveterinaria.shared.api.ApiException;

/**
 * B02 authorization is evaluated from the server-resolved session, never from
 * Angular identifiers or requested roles.
 */
public final class B02AccessPolicy {

	private static final Set<String> REGISTRY_ROLES = Set.of("ADMIN_VETERINARIA", "RECEPCION");
	private static final Set<String> PET_READER_ROLES = Set.of("ADMIN_VETERINARIA", "RECEPCION", "VETERINARIO");

	public void requireRegistryManager(SessionPrincipal actor) {
		requireStaffInActiveTenant(actor);
		requireAnyRole(actor, REGISTRY_ROLES, "B02_REGISTRY_ROLE_REQUIRED",
				"Se requiere un rol autorizado para gestionar propietarios y mascotas.");
	}

	public void requirePetReader(SessionPrincipal actor) {
		requireStaffInActiveTenant(actor);
		requireAnyRole(actor, PET_READER_ROLES, "B02_PET_READ_ROLE_REQUIRED",
				"No se puede consultar esta ficha.");
	}

	public void requireVeterinarian(SessionPrincipal actor) {
		requireStaffInActiveTenant(actor);
		requireAnyRole(actor, Set.of("VETERINARIO"), "B02_CLINICAL_ROLE_REQUIRED",
				"Se requiere el rol veterinario para acceder a la historia clínica.");
	}

	private void requireStaffInActiveTenant(SessionPrincipal actor) {
		if (actor == null || actor.accountType() != AccountType.STAFF || actor.session().tenantId() == null) {
			throw new ApiException(HttpStatus.FORBIDDEN, "B02_STAFF_REQUIRED",
					"La operación requiere personal autorizado de la veterinaria.");
		}
		if (!"ACTIVO".equals(actor.session().tenantStatus())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "TENANT_NOT_ACTIVE",
					"La veterinaria no está disponible para esta operación.");
		}
	}

	private void requireAnyRole(SessionPrincipal actor, Set<String> allowedRoles, String code, String message) {
		if (actor.session().roles().stream().noneMatch(allowedRoles::contains)) {
			throw new ApiException(HttpStatus.FORBIDDEN, code, message);
		}
	}
}
