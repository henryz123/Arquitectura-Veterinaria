package pe.arquitecturaveterinaria.identityaccess.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.arquitecturaveterinaria.identityaccess.domain.CredentialNormalizer;
import pe.arquitecturaveterinaria.identityaccess.domain.TokenHasher;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.JdbcIdentityRepository;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.JdbcIdentityRepository.StaffIdentity;
import pe.arquitecturaveterinaria.shared.api.ApiException;

@Service
public class IdentityAccessService {

	private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
	private final JdbcIdentityRepository identityRepository;
	private final LoginAttemptThrottle throttle;
	private final Duration sessionDuration;

	public IdentityAccessService(JdbcIdentityRepository identityRepository, LoginAttemptThrottle throttle,
			@Value("${av.security.session-duration}") Duration sessionDuration) {
		this.identityRepository = identityRepository;
		this.throttle = throttle;
		this.sessionDuration = sessionDuration;
	}

	@Transactional
	public CreatedSession authenticateStaff(String tenantSlug, String email, String password) {
		String normalizedEmail = CredentialNormalizer.normalizeEmail(email);
		String throttleKey = tenantSlug + ':' + normalizedEmail;
		throttle.assertAllowed(throttleKey);
		Optional<StaffIdentity> identity = identityRepository.lookupStaff(tenantSlug, normalizedEmail);
		if (identity.isEmpty() || !isActive(identity.get()) || !PASSWORD_ENCODER.matches(password, identity.get().passwordHash())) {
			throttle.registerFailure(throttleKey);
			throw invalidCredentials();
		}

		StaffIdentity staff = identity.get();
		String sessionToken = TokenHasher.newOpaqueToken();
		String csrfToken = TokenHasher.newOpaqueToken();
		Instant expiresAt = Instant.now().plus(sessionDuration);
		UUID sessionId = UUID.randomUUID();
		identityRepository.createSession(sessionId, staff.accountId(), staff.tenantId(), TokenHasher.sha256(sessionToken),
				TokenHasher.sha256(csrfToken), staff.roleCode(), expiresAt);
		throttle.clear(throttleKey);
		return new CreatedSession(sessionToken, csrfToken, new TenantSession(
				sessionId, staff.accountId(), staff.tenantId(), staff.tenantSlug(), staff.tenantCommercialName(),
				staff.tenantStatus(), pe.arquitecturaveterinaria.identityaccess.domain.AccountType.STAFF,
				java.util.List.of(staff.roleCode()), "", expiresAt), expiresAt);
	}

	@Transactional(readOnly = true)
	public Optional<TenantSession> resolve(String sessionToken) {
		return identityRepository.findActiveSession(TokenHasher.sha256(sessionToken));
	}

	@Transactional
	public void logout(SessionPrincipal principal) {
		identityRepository.revokeSession(principal.session().sessionId(), principal.session().tenantId());
	}

	private boolean isActive(StaffIdentity identity) {
		return "ACTIVO".equals(identity.tenantStatus()) && "ACTIVA".equals(identity.accountStatus());
	}

	private ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "No fue posible iniciar sesi\u00f3n con las credenciales proporcionadas.");
	}

	public record CreatedSession(String sessionToken, String csrfToken, TenantSession context, Instant expiresAt) {
	}
}
