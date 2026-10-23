package pe.arquitecturaveterinaria.identityaccess.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.arquitecturaveterinaria.identityaccess.domain.AccountType;
import pe.arquitecturaveterinaria.identityaccess.domain.CredentialNormalizer;
import pe.arquitecturaveterinaria.identityaccess.domain.TokenHasher;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.JdbcIdentityRepository;
import pe.arquitecturaveterinaria.shared.api.ApiException;

@Service
public class IdentityAccessService {

	private final JdbcIdentityRepository identityRepository;
	private final LoginAttemptThrottle throttle;
	private final Duration sessionDuration;

	public IdentityAccessService(JdbcIdentityRepository identityRepository, LoginAttemptThrottle throttle,
			@Value("${av.security.session-duration}") Duration sessionDuration) {
		this.identityRepository = identityRepository;
		this.throttle = throttle;
		this.sessionDuration = sessionDuration;
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public CreatedSession authenticateStaff(String tenantSlug, String email, String password) {
		return authenticate(AccountType.STAFF, tenantSlug, email, password);
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public CreatedSession authenticateClient(String tenantSlug, String email, String password) {
		return authenticate(AccountType.CLIENT, tenantSlug, email, password);
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public CreatedSession authenticateSaas(String email, String password) {
		return authenticate(AccountType.SAAS, null, email, password);
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public Optional<TenantSession> resolve(String sessionToken) {
		String sessionTokenHash = TokenHasher.sha256(sessionToken);
		return identityRepository.findActiveSession(sessionTokenHash);
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public void logout(SessionPrincipal principal) {
		identityRepository.revokeSession(principal.session().sessionTokenHash());
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public boolean isCsrfTokenValid(SessionPrincipal principal, String csrfToken) {
		if (csrfToken == null || csrfToken.isBlank()) {
			return false;
		}
		return identityRepository.isCsrfTokenValid(principal.session().sessionTokenHash(), TokenHasher.sha256(csrfToken));
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public String refreshCsrfToken(SessionPrincipal principal) {
		String csrfToken = TokenHasher.newOpaqueToken();
		boolean rotated = identityRepository.rotateCsrfToken(principal.session().sessionTokenHash(), TokenHasher.sha256(csrfToken));
		if (!rotated) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Se requiere una sesión válida.");
		}
		return csrfToken;
	}

	private CreatedSession authenticate(AccountType accountType, String tenantSlug, String email, String password) {
		String normalizedEmail = CredentialNormalizer.normalizeEmail(email);
		String throttleKey = accountType + ":" + (tenantSlug == null ? "global" : tenantSlug) + ':' + normalizedEmail;
		throttle.assertAllowed(throttleKey);
		String sessionToken = TokenHasher.newOpaqueToken();
		String csrfToken = TokenHasher.newOpaqueToken();
		Instant expiresAt = Instant.now().plus(sessionDuration);
		String sessionTokenHash = TokenHasher.sha256(sessionToken);
		String csrfTokenHash = TokenHasher.sha256(csrfToken);
		Optional<TenantSession> session = identityRepository.authenticateAndCreateSession(accountType, tenantSlug, normalizedEmail, password,
				sessionTokenHash, csrfTokenHash, expiresAt);
		if (session.isEmpty()) {
			throttle.registerFailure(throttleKey);
			throw invalidCredentials();
		}
		throttle.clear(throttleKey);
		return new CreatedSession(sessionToken, csrfToken, session.get(), expiresAt);
	}

	private ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "No fue posible iniciar sesión con las credenciales proporcionadas.");
	}

	public record CreatedSession(String sessionToken, String csrfToken, TenantSession context, Instant expiresAt) {
	}
}
