package pe.arquitecturaveterinaria.identityaccess.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import pe.arquitecturaveterinaria.shared.api.ApiException;

@SpringBootTest
@Transactional(transactionManager = "jdbcTransactionManager")
class StaffAuthenticationDatabaseIntegrationTest {

	@Autowired
	private IdentityAccessService identityAccessService;

	@Test
	void authenticatesTheSyntheticAuroraStaffAccountWithinTheCurrentTransaction() {
		String password = System.getenv("AV_DEMO_AURORA_PASSWORD");
		assertNotNull(password, "La contraseña sintética debe existir solo en el entorno local de la prueba.");
		assertFalse(password.isBlank(), "La contraseña sintética local no puede estar vacía.");

		IdentityAccessService.CreatedSession created = identityAccessService.authenticateStaff(
				"veterinaria-aurora", "administracion@demo.invalid", password);

		assertEquals("veterinaria-aurora", created.context().tenantSlug());
		assertFalse(created.sessionToken().isBlank());
		assertFalse(created.csrfToken().isBlank());
	}

	@Test
	void rotatesTheCsrfTokenForAnActiveSessionWithoutCreatingAnotherSession() {
		String password = System.getenv("AV_DEMO_AURORA_PASSWORD");
		assertNotNull(password, "La contraseña sintética debe existir solo en el entorno local de la prueba.");
		IdentityAccessService.CreatedSession created = identityAccessService.authenticateStaff(
				"veterinaria-aurora", "administracion@demo.invalid", password);
		SessionPrincipal principal = new SessionPrincipal(created.context());

		assertTrue(identityAccessService.isCsrfTokenValid(principal, created.csrfToken()));
		String rotatedToken = identityAccessService.refreshCsrfToken(principal);

		assertFalse(rotatedToken.isBlank());
		assertFalse(identityAccessService.isCsrfTokenValid(principal, created.csrfToken()));
		assertTrue(identityAccessService.isCsrfTokenValid(principal, rotatedToken));
		assertEquals(created.context().sessionId(), identityAccessService.resolve(created.sessionToken()).orElseThrow().sessionId());
	}

	@Test
	void refusesToRotateTheCsrfTokenAfterTheSessionIsRevoked() {
		String password = System.getenv("AV_DEMO_AURORA_PASSWORD");
		assertNotNull(password, "La contraseña sintética debe existir solo en el entorno local de la prueba.");
		IdentityAccessService.CreatedSession created = identityAccessService.authenticateStaff(
				"veterinaria-aurora", "administracion@demo.invalid", password);
		SessionPrincipal principal = new SessionPrincipal(created.context());

		identityAccessService.logout(principal);

		ApiException error = assertThrows(ApiException.class, () -> identityAccessService.refreshCsrfToken(principal));
		assertEquals("UNAUTHENTICATED", error.code());
	}
}
