package pe.arquitecturaveterinaria.ownerpetcare.application;

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

class B02AccessPolicyTest {

	private final B02AccessPolicy policy = new B02AccessPolicy();

	@Test
	void receptionCanManageTheBasicRegistryButCannotWriteClinicalHistory() {
		SessionPrincipal receptionist = principal(AccountType.STAFF, "ACTIVO", List.of("RECEPCION"));

		assertDoesNotThrow(() -> policy.requireRegistryManager(receptionist));
		ApiException exception = assertThrows(ApiException.class, () -> policy.requireVeterinarian(receptionist));

		assertEquals(HttpStatus.FORBIDDEN, exception.status());
		assertEquals("B02_CLINICAL_ROLE_REQUIRED", exception.code());
	}

	@Test
	void veterinarianCanReadAndWriteClinicalHistoryButDoesNotGainReceptionPowers() {
		SessionPrincipal veterinarian = principal(AccountType.STAFF, "ACTIVO", List.of("VETERINARIO"));

		assertDoesNotThrow(() -> policy.requirePetReader(veterinarian));
		assertDoesNotThrow(() -> policy.requireVeterinarian(veterinarian));
		ApiException exception = assertThrows(ApiException.class, () -> policy.requireRegistryManager(veterinarian));

		assertEquals("B02_REGISTRY_ROLE_REQUIRED", exception.code());
	}

	@Test
	void administratorWithoutVeterinarianRoleCannotWriteClinicalHistory() {
		SessionPrincipal administrator = principal(AccountType.STAFF, "ACTIVO", List.of("ADMIN_VETERINARIA"));

		assertDoesNotThrow(() -> policy.requireRegistryManager(administrator));
		ApiException exception = assertThrows(ApiException.class, () -> policy.requireVeterinarian(administrator));

		assertEquals("B02_CLINICAL_ROLE_REQUIRED", exception.code());
	}

	@Test
	void clientAndSuspendedTenantAreDeniedBeforeB02DataAccess() {
		SessionPrincipal client = principal(AccountType.CLIENT, "ACTIVO", List.of());
		SessionPrincipal suspendedReceptionist = principal(AccountType.STAFF, "SUSPENDIDO", List.of("RECEPCION"));

		assertEquals("B02_STAFF_REQUIRED",
				assertThrows(ApiException.class, () -> policy.requirePetReader(client)).code());
		assertDoesNotThrow(() -> policy.requireClient(client));
		assertEquals("TENANT_NOT_ACTIVE",
				assertThrows(ApiException.class, () -> policy.requireRegistryManager(suspendedReceptionist)).code());
	}

	@Test
	void staffCannotUseClientOnlyOperations() {
		SessionPrincipal receptionist = principal(AccountType.STAFF, "ACTIVO", List.of("RECEPCION"));

		ApiException exception = assertThrows(ApiException.class, () -> policy.requireClient(receptionist));

		assertEquals(HttpStatus.FORBIDDEN, exception.status());
		assertEquals("B02_CLIENT_REQUIRED", exception.code());
	}

	private SessionPrincipal principal(AccountType accountType, String tenantStatus, List<String> roles) {
		UUID tenantId = UUID.randomUUID();
		return new SessionPrincipal(new TenantSession(UUID.randomUUID(), UUID.randomUUID(), tenantId, "tenant-prueba",
				"Veterinaria Prueba", tenantStatus, accountType, roles, "session-hash", Instant.now().plusSeconds(3600)));
	}
}
