package pe.arquitecturaveterinaria.identityaccess.application;

import java.net.URI;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.arquitecturaveterinaria.identityaccess.domain.AccountType;
import pe.arquitecturaveterinaria.identityaccess.domain.CredentialNormalizer;
import pe.arquitecturaveterinaria.identityaccess.domain.TokenHasher;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.B01JdbcRepository;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.B01JdbcRepository.AdministrativeRecovery;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.B01JdbcRepository.Organization;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.B01JdbcRepository.Registration;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.B01JdbcRepository.RucReview;
import pe.arquitecturaveterinaria.shared.api.ApiException;

/** Application service for the approved B01 use cases. */
@Service
public class B01AdministrationService {

	private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();
	private static final Set<String> STAFF_ROLES = Set.of(
			"ADMIN_VETERINARIA", "RECEPCION", "VETERINARIO", "CAJERO", "GROOMING");
	private static final Duration EMAIL_LINK_DURATION = Duration.ofHours(24);
	private static final Duration PASSWORD_RECOVERY_DURATION = Duration.ofMinutes(30);
	private static final Duration COMPLETION_GRANT_DURATION = Duration.ofMinutes(15);

	private final B01JdbcRepository repository;
	private final LocalNotificationMailbox localMailbox;
	private final String localLinkBaseUrl;

	public B01AdministrationService(B01JdbcRepository repository, LocalNotificationMailbox localMailbox,
			@Value("${av.notifications.local-link-base-url:http://localhost:4200}") String localLinkBaseUrl) {
		this.repository = repository;
		this.localMailbox = localMailbox;
		this.localLinkBaseUrl = localLinkBaseUrl.replaceAll("/+$", "");
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public RegistrationResult registerTenant(TenantRegistrationCommand command) {
		Instant transactionNow = repository.transactionTimestamp();
		String ruc = normalizeOptionalRuc(command.ruc());
		String initialEmail = CredentialNormalizer.normalizeEmail(command.initialAdministratorEmail());
		String commercialEmail = CredentialNormalizer.normalizeEmail(command.commercialEmail());
		Instant reservationExpiry = ruc == null ? null : transactionNow.plus(EMAIL_LINK_DURATION);
		Registration registration = new Registration(trim(command.commercialName()), trim(command.commercialAddress()),
				trim(command.commercialPhone()), trim(command.commercialEmail()), commercialEmail,
				trim(command.initialAdministratorEmail()), initialEmail, ruc, reservationExpiry);
		String rawToken = TokenHasher.newOpaqueToken();
		try {
			var started = repository.startTenantRegistration(registration, TokenHasher.sha256(rawToken), transactionNow.plus(EMAIL_LINK_DURATION));
			acceptLocalLink(started.registrationId(), "REGISTRATION_EMAIL_VERIFICATION", rawToken);
			return new RegistrationResult(started.registrationId(), started.status(), started.rucReservedUntil());
		} catch (DuplicateKeyException exception) {
			throw conflict("RUC_NOT_AVAILABLE", "No fue posible registrar la solicitud con los datos proporcionados.");
		}
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public RegistrationResult resumeRegistration(UUID registrationId, String initialAdministratorEmail) {
		Instant transactionNow = repository.transactionTimestamp();
		String rawToken = TokenHasher.newOpaqueToken();
		try {
			var resumed = repository.resumeTenantRegistration(registrationId,
					CredentialNormalizer.normalizeEmail(initialAdministratorEmail), TokenHasher.sha256(rawToken),
					transactionNow.plus(EMAIL_LINK_DURATION));
			acceptLocalLink(resumed.registrationId(), resumed.linkPurpose(), rawToken);
			return new RegistrationResult(resumed.registrationId(), resumed.status(), resumed.rucReservedUntil());
		} catch (org.springframework.dao.DataAccessException exception) {
			throw invalidLink(exception);
		}
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public VerificationResult verifyEmailLink(String rawToken) {
		Instant transactionNow = repository.transactionTimestamp();
		String completionGrant = TokenHasher.newOpaqueToken();
		try {
			var verification = repository.verifyEmailLink(TokenHasher.sha256(rawToken), TokenHasher.sha256(completionGrant),
					transactionNow.plus(COMPLETION_GRANT_DURATION));
			return new VerificationResult(verification.status(), verification.accountType(), verification.resourceId(),
					verification.accountType() == AccountType.STAFF ? completionGrant : null);
		} catch (org.springframework.dao.DataAccessException exception) {
			throw invalidLink(exception);
		}
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public ActivationResult setInitialAdministratorPassword(UUID registrationId, String rawCompletionGrant, String password) {
		validatePassword(password);
		if (rawCompletionGrant == null) {
			throw invalidLink();
		}
		String tenantSlug = newTenantSlug("veterinaria", registrationId);
		try {
			var activated = repository.completeTenantRegistration(TokenHasher.sha256(rawCompletionGrant), PASSWORD_ENCODER.encode(password), tenantSlug);
			return new ActivationResult(activated.tenantId(), activated.tenantSlug(), activated.commercialName());
		} catch (org.springframework.dao.DataAccessException exception) {
			throw invalidLink();
		}
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public ClientRegistrationResult registerClient(String tenantSlug, String email, String password) {
		Instant transactionNow = repository.transactionTimestamp();
		validatePassword(password);
		String normalizedEmail = CredentialNormalizer.normalizeEmail(email);
		String rawToken = TokenHasher.newOpaqueToken();
		try {
			UUID principalId = repository.registerClient(tenantSlug, normalizedEmail, PASSWORD_ENCODER.encode(password),
					TokenHasher.sha256(rawToken), transactionNow.plus(EMAIL_LINK_DURATION));
			acceptLocalLink(principalId, "CLIENT_EMAIL_VERIFICATION", rawToken);
			return new ClientRegistrationResult(principalId, "PENDIENTE_VERIFICACION");
		} catch (DuplicateKeyException exception) {
			throw conflict("CLIENT_ACCOUNT_NOT_AVAILABLE", "No fue posible registrar la cuenta con los datos proporcionados.");
		}
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public void resendClientVerification(String tenantSlug, String email) {
		Instant transactionNow = repository.transactionTimestamp();
		String rawToken = TokenHasher.newOpaqueToken();
		repository.resendClientVerification(tenantSlug, CredentialNormalizer.normalizeEmail(email), TokenHasher.sha256(rawToken),
				transactionNow.plus(EMAIL_LINK_DURATION)).ifPresent(principalId ->
				acceptLocalLink(principalId, "CLIENT_EMAIL_VERIFICATION", rawToken));
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public void requestPasswordRecovery(AccountType accountType, String tenantSlug, String email) {
		Instant transactionNow = repository.transactionTimestamp();
		if (accountType == AccountType.SAAS && tenantSlug != null) {
			throw validation("tenantSlug", "El contexto global no usa tenant.");
		}
		String rawToken = TokenHasher.newOpaqueToken();
		repository.requestPasswordRecovery(accountType, tenantSlug, CredentialNormalizer.normalizeEmail(email), TokenHasher.sha256(rawToken),
				transactionNow.plus(PASSWORD_RECOVERY_DURATION)).ifPresent(target ->
				acceptLocalLink(target.principalId(), "PASSWORD_RECOVERY", rawToken));
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public void resetPassword(String rawToken, String password) {
		validatePassword(password);
		try {
			repository.resetPassword(TokenHasher.sha256(rawToken), PASSWORD_ENCODER.encode(password));
		} catch (org.springframework.dao.DataAccessException exception) {
			throw invalidLink();
		}
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public StaffInvitationResult inviteStaff(SessionPrincipal actor, String email, List<String> requestedRoles) {
		Instant transactionNow = repository.transactionTimestamp();
		requireTenantAdministrator(actor);
		Set<String> roles = validRoles(requestedRoles);
		String normalizedEmail = CredentialNormalizer.normalizeEmail(email);
		String rawToken = TokenHasher.newOpaqueToken();
		try {
			var invitation = repository.inviteStaff(actor.session().sessionTokenHash(), normalizedEmail, rolesJson(roles),
					TokenHasher.sha256(rawToken), transactionNow.plus(EMAIL_LINK_DURATION));
			acceptLocalLink(invitation.principalId(), "STAFF_INVITATION", rawToken);
			return new StaffInvitationResult(invitation.principalId(), "INVITADA", invitation.roles());
		} catch (org.springframework.dao.DataAccessException exception) {
			throw conflict("STAFF_ACCOUNT_NOT_AVAILABLE", "No fue posible emitir la invitación para esta cuenta.", exception);
		}
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public void acceptStaffInvitation(String rawToken, String password) {
		validatePassword(password);
		try {
			repository.acceptStaffInvitation(TokenHasher.sha256(rawToken), PASSWORD_ENCODER.encode(password));
		} catch (org.springframework.dao.DataAccessException exception) {
			throw invalidLink();
		}
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<StaffAccountView> listStaff(SessionPrincipal actor) {
		requireTenantAdministrator(actor);
		return repository.listStaffForSession(actor.session().sessionTokenHash()).stream()
				.map(profile -> new StaffAccountView(profile.principalId(), profile.normalizedEmail(), profile.status(), profile.roles()))
				.toList();
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public StaffAccountView replaceStaffRoles(SessionPrincipal actor, UUID targetPrincipalId, List<String> requestedRoles) {
		requireTenantAdministrator(actor);
		Set<String> desiredRoles = validRoles(requestedRoles);
		try {
			var account = repository.replaceStaffRoles(actor.session().sessionTokenHash(), targetPrincipalId, rolesJson(desiredRoles));
			return new StaffAccountView(account.principalId(), account.normalizedEmail(), account.status(), account.roles());
		} catch (org.springframework.dao.DataIntegrityViolationException exception) {
			throw conflict("LAST_TENANT_ADMINISTRATOR", "Debe permanecer al menos un administrador activo en la veterinaria.");
		} catch (org.springframework.dao.DataAccessException exception) {
			throw notAvailable();
		}
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public void deactivateStaff(SessionPrincipal actor, UUID targetPrincipalId) {
		requireTenantAdministrator(actor);
		try {
			if (!repository.deactivateStaff(actor.session().sessionTokenHash(), targetPrincipalId)) {
				throw notAvailable();
			}
		} catch (org.springframework.dao.DataIntegrityViolationException exception) {
			throw conflict("LAST_TENANT_ADMINISTRATOR", "Debe permanecer al menos un administrador activo en la veterinaria.");
		} catch (org.springframework.dao.DataAccessException exception) {
			throw notAvailable();
		}
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<Organization> listOrganizations(SessionPrincipal actor) {
		requireSaasAdministrator(actor);
		return repository.listOrganizations(actor.session().sessionTokenHash());
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public void suspendTenant(SessionPrincipal actor, UUID tenantId, String reason, boolean confirmed) {
		requireSaasAdministrator(actor);
		if (!confirmed) {
			throw validation("confirmed", "La suspensión requiere confirmación explícita.");
		}
		if (!repository.changeTenantStatus(actor.session().sessionTokenHash(), tenantId, "SUSPENDIDO", trim(reason))) {
			throw notAvailable();
		}
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public void reactivateTenant(SessionPrincipal actor, UUID tenantId, String reason, boolean confirmed) {
		requireSaasAdministrator(actor);
		if (!confirmed) {
			throw validation("confirmed", "La reactivación requiere confirmación explícita.");
		}
		if (!repository.changeTenantStatus(actor.session().sessionTokenHash(), tenantId, "ACTIVO", trim(reason))) {
			throw notAvailable();
		}
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public void requestRucReview(String ruc, String requesterEmail, String contactChannel, String reason) {
		String normalizedRuc = normalizeRequiredRuc(ruc);
		repository.createRucReview(new RucReview(UUID.randomUUID(), normalizedRuc, trim(requesterEmail),
				CredentialNormalizer.normalizeEmail(requesterEmail), trim(contactChannel), trim(reason)));
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<RucReview> listRucReviews(SessionPrincipal actor) {
		requireSaasAdministrator(actor);
		return repository.listRucReviews(actor.session().sessionTokenHash());
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public void resolveRucReview(SessionPrincipal actor, UUID reviewId, String status, String reason, boolean releaseRuc, boolean confirmed) {
		requireSaasAdministrator(actor);
		if (releaseRuc && !confirmed) {
			throw validation("confirmed", "La liberación excepcional requiere confirmación explícita.");
		}
		if (!repository.resolveRucReview(actor.session().sessionTokenHash(), reviewId, status, trim(reason), releaseRuc)) {
			throw notAvailable();
		}
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public void requestAdministrativeRecovery(String tenantSlug, String applicantEmail, String evidenceReference) {
		repository.requestAdministrativeRecovery(tenantSlug, trim(applicantEmail), CredentialNormalizer.normalizeEmail(applicantEmail),
				trim(evidenceReference));
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public List<AdministrativeRecovery> listAdministrativeRecoveries(SessionPrincipal actor) {
		requireSaasAdministrator(actor);
		return repository.listAdministrativeRecoveries(actor.session().sessionTokenHash());
	}

	@Transactional(transactionManager = "jdbcTransactionManager")
	public void reviewAdministrativeRecovery(SessionPrincipal actor, UUID recoveryId, String status, String reason, boolean legitimacyChecked) {
		Instant transactionNow = repository.transactionTimestamp();
		requireSaasAdministrator(actor);
		if (!Set.of("VERIFICANDO", "INFO_REQUERIDA", "RECHAZADA", "APROBADA").contains(status)) {
			throw validation("status", "El estado de revisión no es válido.");
		}
		if ("APROBADA".equals(status) && !legitimacyChecked) {
			throw validation("legitimacyChecked", "La aprobación exige verificación humana explícita.");
		}
		String rawToken = TokenHasher.newOpaqueToken();
		try {
			var result = repository.reviewAdministrativeRecovery(actor.session().sessionTokenHash(), recoveryId, status, trim(reason),
					legitimacyChecked, TokenHasher.sha256(rawToken), transactionNow.plus(EMAIL_LINK_DURATION));
			if (result.executed()) {
				acceptLocalLink(result.principalId(), "STAFF_INVITATION", rawToken);
			}
		} catch (org.springframework.dao.DataAccessException exception) {
			throw notAvailable();
		}
	}

	@Transactional(transactionManager = "jdbcTransactionManager", readOnly = true)
	public ClientSpace clientSpace(SessionPrincipal actor) {
		if (actor.accountType() != AccountType.CLIENT) {
			throw forbidden();
		}
		return new ClientSpace(actor.session().tenantSlug(), actor.session().tenantCommercialName(),
				"El espacio privado de cliente está autenticado. Las operaciones de mascotas, citas, grooming y pedidos se habilitan en sus bloques V1 correspondientes.");
	}

	private void acceptLocalLink(UUID relatedResourceId, String purpose, String rawToken) {
		localMailbox.accept(relatedResourceId, purpose, URI.create(localLinkBaseUrl + "/verify-email?token=" + rawToken));
	}

	private String rolesJson(Set<String> roles) {
		return roles.stream().sorted().map(role -> "\"" + role + "\"")
				.collect(java.util.stream.Collectors.joining(",", "[", "]"));
	}

	private void requireTenantAdministrator(SessionPrincipal actor) {
		if (actor.accountType() != AccountType.STAFF || actor.session().tenantId() == null
				|| !actor.session().roles().contains("ADMIN_VETERINARIA")) {
			throw forbidden();
		}
	}

	private void requireSaasAdministrator(SessionPrincipal actor) {
		if (actor.accountType() != AccountType.SAAS || !actor.session().roles().contains("ADMIN_SAAS")) {
			throw forbidden();
		}
	}

	private Set<String> validRoles(List<String> values) {
		Set<String> roles = values == null ? Set.of() : new HashSet<>(values);
		if (roles.isEmpty() || !STAFF_ROLES.containsAll(roles)) {
			throw validation("roles", "Debe indicar uno o más roles laborales válidos.");
		}
		return roles;
	}

	private void validatePassword(String password) {
		if (password == null || password.length() < 12 || password.length() > 72
				|| password.chars().noneMatch(Character::isUpperCase)
				|| password.chars().noneMatch(Character::isLowerCase)
				|| password.chars().noneMatch(Character::isDigit)) {
			throw validation("password", "La contraseña no cumple la política de seguridad.");
		}
	}

	private String normalizeOptionalRuc(String ruc) {
		if (ruc == null || ruc.isBlank()) {
			return null;
		}
		return normalizeRequiredRuc(ruc);
	}

	private String normalizeRequiredRuc(String ruc) {
		String normalized = trim(ruc);
		if (!normalized.matches("[0-9]{11}")) {
			throw validation("ruc", "El RUC debe contener 11 dígitos.");
		}
		return normalized;
	}

	private String newTenantSlug(String commercialName, UUID registrationId) {
		String normalized = Normalizer.normalize(commercialName, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		if (normalized.isBlank()) {
			normalized = "veterinaria";
		}
		normalized = normalized.length() > 60 ? normalized.substring(0, 60).replaceAll("-$", "") : normalized;
		return normalized + "-" + registrationId.toString().substring(0, 8);
	}

	private String trim(String value) {
		return value == null ? "" : value.trim();
	}

	private ApiException invalidLink() {
		return new ApiException(HttpStatus.CONFLICT, "LINK_NOT_VALID", "El enlace no es válido, ha vencido o ya fue utilizado.");
	}

	private ApiException invalidLink(Throwable cause) {
		return new ApiException(HttpStatus.CONFLICT, "LINK_NOT_VALID", "El enlace no es válido, ha vencido o ya fue utilizado.", cause);
	}

	private ApiException notAvailable() {
		return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "El recurso solicitado no está disponible.");
	}

	private ApiException forbidden() {
		return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "La operación no está autorizada.");
	}

	private ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}

	private ApiException conflict(String code, String message, Throwable cause) {
		return new ApiException(HttpStatus.CONFLICT, code, message, cause);
	}

	private ApiException validation(String field, String message) {
		return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", message + " [" + field + "]");
	}

	public record TenantRegistrationCommand(String commercialName, String commercialAddress, String commercialPhone,
			String commercialEmail, String initialAdministratorEmail, String ruc) { }
	public record RegistrationResult(UUID registrationId, String status, Instant rucReservedUntil) { }
	public record VerificationResult(String status, AccountType accountType, UUID relatedId, String completionGrant) { }
	public record ActivationResult(UUID tenantId, String tenantSlug, String commercialName) { }
	public record StaffInvitationResult(UUID principalId, String status, List<String> roles) { }
	public record StaffAccountView(UUID principalId, String email, String status, List<String> roles) { }
	public record ClientRegistrationResult(UUID principalId, String status) { }
	public record ClientSpace(String tenantSlug, String commercialName, String message) { }
}
