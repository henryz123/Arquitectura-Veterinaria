package pe.arquitecturaveterinaria.identityaccess.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
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
}
