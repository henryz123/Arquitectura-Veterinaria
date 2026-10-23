package pe.arquitecturaveterinaria;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Starts the Spring context used only by the explicitly authorised Flyway launcher.
 *
 * <p>Flyway remains disabled by default. When the launcher opts in, Spring Boot
 * validates the immutable history and migrates only up to the pinned target.
 * This class contains no business setup and does not create synthetic data.</p>
 */
@SpringBootTest
class AuthorizedFlywayMigrationIntegrationTest {

	@Test
	void startsOnlyAfterTheControlledFlywayPreflightSucceeds() {
		// Context startup is the assertion: Flyway validates and migrates before it is ready.
	}
}
