package pe.arquitecturaveterinaria.inventorysales.application;

import java.util.Set;

import org.springframework.http.HttpStatus;

import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.domain.AccountType;
import pe.arquitecturaveterinaria.shared.api.ApiException;

/**
 * B04 catalog permissions are derived solely from the server-resolved STAFF
 * session. CAJERO does not receive catalog-management permissions.
 */
public final class InventoryAccessPolicy {

	private static final Set<String> CATALOG_MANAGER_ROLES = Set.of("ADMIN_VETERINARIA");

	public void requireCatalogManager(SessionPrincipal actor) {
		if (actor == null || actor.accountType() != AccountType.STAFF || actor.session().tenantId() == null) {
			throw new ApiException(HttpStatus.FORBIDDEN, "B04_STAFF_REQUIRED",
					"La operación requiere personal autorizado de la veterinaria.");
		}
		if (!"ACTIVO".equals(actor.session().tenantStatus())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "TENANT_NOT_ACTIVE",
					"La veterinaria no está disponible para esta operación.");
		}
		if (actor.session().roles().stream().noneMatch(CATALOG_MANAGER_ROLES::contains)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "B04_CATALOG_ADMIN_REQUIRED",
					"Se requiere administración de veterinaria para gestionar el catálogo.");
		}
	}
}
