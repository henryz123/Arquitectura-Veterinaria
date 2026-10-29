package pe.arquitecturaveterinaria.inventorysales.application;

import java.util.Set;

import org.springframework.http.HttpStatus;

import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.domain.AccountType;
import pe.arquitecturaveterinaria.shared.api.ApiException;

/** B04 permissions are derived only from the server-resolved STAFF session. */
public final class InventoryAccessPolicy {

	private static final Set<String> CATALOG_MANAGER_ROLES = Set.of("ADMIN_VETERINARIA");
	private static final Set<String> CASHIER_ROLES = Set.of("CAJERO");

	public void requireCatalogManager(SessionPrincipal actor) {
		requireActiveStaff(actor);
		if (actor.session().roles().stream().noneMatch(CATALOG_MANAGER_ROLES::contains)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "B04_CATALOG_ADMIN_REQUIRED",
					"Se requiere administracion de veterinaria para gestionar el catalogo.");
		}
	}

	/** A POS sale requires CAJERO explicitly; ADMIN does not imply that role. */
	public void requireCashier(SessionPrincipal actor) {
		requireActiveStaff(actor);
		if (actor.session().roles().stream().noneMatch(CASHIER_ROLES::contains)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "B04_CASHIER_REQUIRED",
					"La operacion de venta requiere el rol CAJERO vigente.");
		}
	}

	/** Returns and cancellations are commercial corrections, available to CAJERO or ADMIN_VETERINARIA. */
	public void requireCommercialOperator(SessionPrincipal actor) {
		requireActiveStaff(actor);
		boolean cashier = actor.session().roles().stream().anyMatch(CASHIER_ROLES::contains);
		boolean catalogManager = actor.session().roles().stream().anyMatch(CATALOG_MANAGER_ROLES::contains);
		if (!cashier && !catalogManager) {
			throw new ApiException(HttpStatus.FORBIDDEN, "B04_COMMERCIAL_OPERATOR_REQUIRED",
					"La correccion comercial requiere CAJERO o administracion de veterinaria vigente.");
		}
	}

	private void requireActiveStaff(SessionPrincipal actor) {
		if (actor == null || actor.accountType() != AccountType.STAFF || actor.session().tenantId() == null) {
			throw new ApiException(HttpStatus.FORBIDDEN, "B04_STAFF_REQUIRED",
					"La operacion requiere personal autorizado de la veterinaria.");
		}
		if (!"ACTIVO".equals(actor.session().tenantStatus())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "TENANT_NOT_ACTIVE",
					"La veterinaria no esta disponible para esta operacion.");
		}
	}
}
