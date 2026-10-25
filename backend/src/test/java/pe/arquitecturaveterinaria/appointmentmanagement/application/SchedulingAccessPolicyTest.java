package pe.arquitecturaveterinaria.appointmentmanagement.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.application.TenantSession;
import pe.arquitecturaveterinaria.identityaccess.domain.AccountType;
import pe.arquitecturaveterinaria.shared.api.ApiException;

class SchedulingAccessPolicyTest {

	private final SchedulingAccessPolicy policy = new SchedulingAccessPolicy();

	@Test
	void administratorAndReceptionCanChooseAssignableProfessionals() {
		assertDoesNotThrow(() -> policy.requireSchedulingManager(principal(AccountType.STAFF, "ACTIVO",
				List.of("ADMIN_VETERINARIA"))));
		assertDoesNotThrow(() -> policy.requireSchedulingManager(principal(AccountType.STAFF, "ACTIVO",
				List.of("RECEPCION"))));
	}

	@Test
	void clientAndUnauthorisedStaffAreDeniedBeforeTheDatabaseQuery() {
		ApiException client = assertThrows(ApiException.class,
				() -> policy.requireSchedulingManager(principal(AccountType.CLIENT, "ACTIVO", List.of())));
		ApiException veterinarian = assertThrows(ApiException.class,
				() -> policy.requireSchedulingManager(principal(AccountType.STAFF, "ACTIVO", List.of("VETERINARIO"))));

		assertEquals(HttpStatus.FORBIDDEN, client.status());
		assertEquals("B03_STAFF_REQUIRED", client.code());
		assertEquals("B03_SCHEDULING_ROLE_REQUIRED", veterinarian.code());
	}

	@Test
	void aSuspendedTenantCannotChooseProfessionals() {
		ApiException exception = assertThrows(ApiException.class,
				() -> policy.requireSchedulingManager(principal(AccountType.STAFF, "SUSPENDIDO", List.of("RECEPCION"))));

		assertEquals("TENANT_NOT_ACTIVE", exception.code());
	}

	private SessionPrincipal principal(AccountType accountType, String tenantStatus, List<String> roles) {
		return new SessionPrincipal(new TenantSession(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "tenant-prueba",
				"Veterinaria Prueba", tenantStatus, accountType, roles, "session-hash", Instant.now().plusSeconds(3600)));
	}
}
