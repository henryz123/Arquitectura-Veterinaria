package pe.arquitecturaveterinaria.identityaccess.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import pe.arquitecturaveterinaria.shared.api.ApiException;

class LoginAttemptThrottleTest {

	@Test
	void rejectsTheSixthFailureInTheActiveWindow() {
		LoginAttemptThrottle throttle = new LoginAttemptThrottle(Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneOffset.UTC));
		String key = "aurora:personal@veterinaria-sintetica.test";

		for (int attempt = 0; attempt < 5; attempt++) {
			throttle.assertAllowed(key);
			throttle.registerFailure(key);
		}

		assertThrows(ApiException.class, () -> throttle.assertAllowed(key));
	}

	@Test
	void successClearsTheFailureWindow() {
		LoginAttemptThrottle throttle = new LoginAttemptThrottle(Clock.systemUTC());
		String key = "aurora:personal@veterinaria-sintetica.test";
		throttle.registerFailure(key);
		throttle.clear(key);

		assertDoesNotThrow(() -> throttle.assertAllowed(key));
	}
}
