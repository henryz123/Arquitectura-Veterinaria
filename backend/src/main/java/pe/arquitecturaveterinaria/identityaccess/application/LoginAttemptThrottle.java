package pe.arquitecturaveterinaria.identityaccess.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import pe.arquitecturaveterinaria.shared.api.ApiException;

import org.springframework.http.HttpStatus;

/**
 * L\u00edmite local deliberadamente conservador. Debe sustituirse por un control distribuido
 * antes de cualquier despliegue con m\u00e1s de una instancia.
 */
@Component
public class LoginAttemptThrottle {

	private static final int MAX_FAILURES = 5;
	private static final Duration WINDOW = Duration.ofMinutes(5);
	private final Map<String, AttemptWindow> windows = new HashMap<>();
	private final Clock clock;

	public LoginAttemptThrottle() {
		this(Clock.systemUTC());
	}

	LoginAttemptThrottle(Clock clock) {
		this.clock = clock;
	}

	public synchronized void assertAllowed(String key) {
		AttemptWindow window = windows.get(key);
		if (window != null && window.isActive(clock.instant()) && window.failures >= MAX_FAILURES) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_RATE_LIMITED", "Intente nuevamente m\u00e1s tarde.");
		}
	}

	public synchronized void registerFailure(String key) {
		Instant now = clock.instant();
		AttemptWindow window = windows.get(key);
		if (window == null || !window.isActive(now)) {
			windows.put(key, new AttemptWindow(now, 1));
			return;
		}
		window.failures++;
	}

	public synchronized void clear(String key) {
		windows.remove(key);
	}

	private static final class AttemptWindow {
		private final Instant startedAt;
		private int failures;

		private AttemptWindow(Instant startedAt, int failures) {
			this.startedAt = startedAt;
			this.failures = failures;
		}

		private boolean isActive(Instant now) {
			return !now.isAfter(startedAt.plus(WINDOW));
		}
	}
}
