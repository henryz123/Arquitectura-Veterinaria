package pe.arquitecturaveterinaria.inventorysales.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.application.TenantSession;
import pe.arquitecturaveterinaria.identityaccess.domain.AccountType;
import pe.arquitecturaveterinaria.shared.api.ApiException;

class InventoryAccessPolicyTest {

	private final InventoryAccessPolicy policy = new InventoryAccessPolicy();

	@Test
	void administratorCanManageCatalogButCashierCannotEscalateToCatalogManagement() {
		assertDoesNotThrow(() -> policy.requireCatalogManager(principal(AccountType.STAFF, "ACTIVO", List.of("ADMIN_VETERINARIA"))));

		ApiException exception = assertThrows(ApiException.class,
				() -> policy.requireCatalogManager(principal(AccountType.STAFF, "ACTIVO", List.of("CAJERO"))));

		assertEquals("B04_CATALOG_ADMIN_REQUIRED", exception.code());
	}

	@Test
	void clientAndSuspendedAdministratorAreDeniedBeforeCatalogAccess() {
		assertEquals("B04_STAFF_REQUIRED", assertThrows(ApiException.class,
				() -> policy.requireCatalogManager(principal(AccountType.CLIENT, "ACTIVO", List.of()))).code());
		assertEquals("TENANT_NOT_ACTIVE", assertThrows(ApiException.class,
				() -> policy.requireCatalogManager(principal(AccountType.STAFF, "SUSPENDIDO", List.of("ADMIN_VETERINARIA")))).code());
	}

	private SessionPrincipal principal(AccountType accountType, String tenantStatus, List<String> roles) {
		return new SessionPrincipal(new TenantSession(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "tenant-prueba",
				"Veterinaria Prueba", tenantStatus, accountType, roles, "session-hash", Instant.now().plusSeconds(3600)));
	}
}
