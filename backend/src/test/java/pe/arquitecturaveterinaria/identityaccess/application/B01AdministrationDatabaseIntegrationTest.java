package pe.arquitecturaveterinaria.identityaccess.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import pe.arquitecturaveterinaria.identityaccess.application.B01AdministrationService.ActivationResult;
import pe.arquitecturaveterinaria.identityaccess.application.B01AdministrationService.ClientRegistrationResult;
import pe.arquitecturaveterinaria.identityaccess.application.B01AdministrationService.RegistrationResult;
import pe.arquitecturaveterinaria.identityaccess.application.B01AdministrationService.StaffInvitationResult;
import pe.arquitecturaveterinaria.identityaccess.domain.AccountType;
import pe.arquitecturaveterinaria.identityaccess.domain.TokenHasher;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.B01JdbcRepository;
import pe.arquitecturaveterinaria.shared.api.ApiException;

@SpringBootTest
@Transactional(transactionManager = "jdbcTransactionManager")
class B01AdministrationDatabaseIntegrationTest {

	private static final SecureRandom RANDOM = new SecureRandom();

	@Autowired
	private B01AdministrationService administrationService;

	@Autowired
	private IdentityAccessService identityAccessService;

	@Autowired
	private LocalNotificationMailbox localMailbox;

	@Autowired
	private B01JdbcRepository repository;

	@Test
	void consumesTheExactOpaqueHashWrittenForATenantRegistration() {
		String rawVerificationToken = TokenHasher.newOpaqueToken();
		String rawCompletionGrant = TokenHasher.newOpaqueToken();
		var registration = repository.startTenantRegistration(
				new B01JdbcRepository.Registration("Veterinaria de Diagnóstico", "Calle Sintética 101", "988888888",
						"comercial-diagnostico@demo.invalid", "comercial-diagnostico@demo.invalid",
						"diagnostico@demo.invalid", "diagnostico@demo.invalid", null, null),
				TokenHasher.sha256(rawVerificationToken), java.time.Instant.now().plus(java.time.Duration.ofHours(23)));

		var verification = repository.verifyEmailLink(TokenHasher.sha256(rawVerificationToken),
				TokenHasher.sha256(rawCompletionGrant), java.time.Instant.now().plus(java.time.Duration.ofMinutes(14)));

		assertEquals(registration.registrationId(), verification.resourceId());
		assertEquals(AccountType.STAFF, verification.accountType());
	}

	@Test
	void handsTheServiceGeneratedRegistrationLinkToTheBoundedVerifier() {
		RegistrationResult registration = administrationService.registerTenant(
				new B01AdministrationService.TenantRegistrationCommand("Veterinaria de Enlace", "Calle Sintética 102",
						"977777777", "comercial-enlace@demo.invalid", "enlace@demo.invalid", syntheticRuc()));
		String rawVerificationToken = tokenFor(registration.registrationId());
		var verification = repository.verifyEmailLink(TokenHasher.sha256(rawVerificationToken),
				TokenHasher.sha256(TokenHasher.newOpaqueToken()), java.time.Instant.now().plus(java.time.Duration.ofMinutes(14)));

		assertEquals(registration.registrationId(), verification.resourceId());
	}

	@Test
	void serviceVerifierConsumesAnOpaqueTokenWrittenByTheJdbcAdapter() {
		String rawVerificationToken = TokenHasher.newOpaqueToken();
		var registration = repository.startTenantRegistration(
				new B01JdbcRepository.Registration("Veterinaria de Servicio", "Calle Sintética 103", "966666666",
						"comercial-servicio@demo.invalid", "comercial-servicio@demo.invalid",
						"servicio@demo.invalid", "servicio@demo.invalid", null, null),
				TokenHasher.sha256(rawVerificationToken), java.time.Instant.now().plus(java.time.Duration.ofHours(23)));

		var verification = administrationService.verifyEmailLink(rawVerificationToken);

		assertEquals(registration.registrationId(), verification.relatedId());
	}

	@Test
	void registersVerifiesActivatesAndAuthenticatesAnIsolatedTenant() {
		String email = syntheticEmail("registrante");
		String password = generatedPassword();
		RegistrationResult registration = administrationService.registerTenant(
				new B01AdministrationService.TenantRegistrationCommand("Veterinaria de Prueba", "Calle Sintética 100",
						"999999999", "comercial-" + email, email, syntheticRuc()));

		var verification = administrationService.verifyEmailLink(tokenFor(registration.registrationId()));
		assertEquals("PENDIENTE_CONTRASENA", verification.status());
		assertNotNull(verification.completionGrant());
		ActivationResult activated = administrationService.setInitialAdministratorPassword(registration.registrationId(),
				verification.completionGrant(), password);

		assertEquals("Veterinaria de Prueba", activated.commercialName());
		var session = identityAccessService.authenticateStaff(activated.tenantSlug(), email, password);
		assertEquals(activated.tenantSlug(), session.context().tenantSlug());
		assertEquals(List.of("ADMIN_VETERINARIA"), session.context().roles());
		assertThrows(ApiException.class, () -> administrationService.verifyEmailLink(tokenFor(registration.registrationId())));
	}

	@Test
	void keepsClientCredentialsAndSessionsSeparateFromStaffCredentials() {
		String email = syntheticEmail("cliente");
		String password = generatedPassword();
		ClientRegistrationResult registration = administrationService.registerClient("veterinaria-aurora", email, password);
		assertEquals("PENDIENTE_VERIFICACION", registration.status());

		var verification = administrationService.verifyEmailLink(tokenFor(registration.principalId()));
		assertEquals("ACTIVA", verification.status());
		var clientSession = identityAccessService.authenticateClient("veterinaria-aurora", email, password);
		assertEquals(AccountType.CLIENT, clientSession.context().accountType());
		assertThrows(ApiException.class, () -> identityAccessService.authenticateStaff("veterinaria-aurora", email, password));
	}

	@Test
	void resendingClientVerificationInvalidatesThePriorOpaqueLink() {
		String email = syntheticEmail("resend");
		ClientRegistrationResult registration = administrationService.registerClient("veterinaria-aurora", email, generatedPassword());
		String firstToken = tokenFor(registration.principalId());

		administrationService.resendClientVerification("veterinaria-aurora", email);
		String secondToken = tokenFor(registration.principalId());

		assertNotEquals(firstToken, secondToken);
		assertThrows(ApiException.class, () -> administrationService.verifyEmailLink(firstToken));
	}

	@Test
	void acceptsTheReplacementClientVerificationLinkOnce() {
		String email = syntheticEmail("resend-replacement");
		ClientRegistrationResult registration = administrationService.registerClient("veterinaria-aurora", email, generatedPassword());

		administrationService.resendClientVerification("veterinaria-aurora", email);
		String replacementToken = tokenFor(registration.principalId());

		assertEquals("ACTIVA", administrationService.verifyEmailLink(replacementToken).status());
		assertThrows(ApiException.class, () -> administrationService.verifyEmailLink(replacementToken));
	}

	@Test
	void recoversAClientPasswordWithAOneTimeOpaqueLink() {
		String email = syntheticEmail("recovery");
		String originalPassword = generatedPassword();
		String replacementPassword = generatedPassword();
		ClientRegistrationResult registration = administrationService.registerClient("veterinaria-aurora", email, originalPassword);
		administrationService.verifyEmailLink(tokenFor(registration.principalId()));

		administrationService.requestPasswordRecovery(AccountType.CLIENT, "veterinaria-aurora", email);
		String recoveryToken = tokenFor(registration.principalId());
		administrationService.resetPassword(recoveryToken, replacementPassword);

		assertThrows(ApiException.class, () -> identityAccessService.authenticateClient("veterinaria-aurora", email, originalPassword));
		assertFalse(identityAccessService.authenticateClient("veterinaria-aurora", email, replacementPassword).sessionToken().isBlank());
		assertThrows(ApiException.class, () -> administrationService.resetPassword(recoveryToken, generatedPassword()));
	}

	@Test
	void scopesPasswordRecoveryToTheRequestedTenantWhenEmailsCoincide() {
		String email = syntheticEmail("recovery-scope");
		String auroraOriginalPassword = generatedPassword();
		String auroraReplacementPassword = generatedPassword();
		String bosqueOriginalPassword = generatedPassword();
		String auroraVerificationToken = TokenHasher.newOpaqueToken();
		String bosqueVerificationToken = TokenHasher.newOpaqueToken();
		BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
		UUID auroraPrincipalId = repository.registerClient("veterinaria-aurora", email, passwordEncoder.encode(auroraOriginalPassword),
				TokenHasher.sha256(auroraVerificationToken), Instant.now().plus(Duration.ofHours(23)));
		UUID bosquePrincipalId = repository.registerClient("veterinaria-bosque", email, passwordEncoder.encode(bosqueOriginalPassword),
				TokenHasher.sha256(bosqueVerificationToken), Instant.now().plus(Duration.ofHours(23)));
		administrationService.verifyEmailLink(auroraVerificationToken);
		administrationService.verifyEmailLink(bosqueVerificationToken);

		administrationService.requestPasswordRecovery(AccountType.CLIENT, "veterinaria-aurora", email);
		administrationService.resetPassword(tokenFor(auroraPrincipalId), auroraReplacementPassword);

		assertThrows(ApiException.class,
				() -> identityAccessService.authenticateClient("veterinaria-aurora", email, auroraOriginalPassword));
		assertFalse(identityAccessService.authenticateClient("veterinaria-aurora", email, auroraReplacementPassword).sessionToken().isBlank());
		assertFalse(identityAccessService.authenticateClient("veterinaria-bosque", email, bosqueOriginalPassword).sessionToken().isBlank());
		assertNotEquals(auroraPrincipalId, bosquePrincipalId);
	}

	@Test
	void inviteAcceptanceAndLastAdministratorProtectionAreEnforced() {
		SessionPrincipal administrator = auroraAdministrator();
		String inviteeEmail = syntheticEmail("trabajador");
		String inviteePassword = generatedPassword();
		StaffInvitationResult invitation = administrationService.inviteStaff(administrator, inviteeEmail, List.of("RECEPCION", "GROOMING"));
		administrationService.acceptStaffInvitation(tokenFor(invitation.principalId()), inviteePassword);

		var invitedSession = identityAccessService.authenticateStaff("veterinaria-aurora", inviteeEmail, inviteePassword);
		assertEquals(List.of("GROOMING", "RECEPCION"), invitedSession.context().roles());
		assertThrows(ApiException.class, () -> administrationService.listStaff(new SessionPrincipal(invitedSession.context())));
		assertThrows(ApiException.class, () -> administrationService.deactivateStaff(administrator, administrator.session().accountId()));
		assertThrows(ApiException.class, () -> administrationService.acceptStaffInvitation(tokenFor(invitation.principalId()), generatedPassword()));
	}

	@Test
	void rejectsAnInvitationBeyondTheApprovedTwentyFourHourBoundary() {
		SessionPrincipal auroraAdministrator = auroraAdministrator();

		assertThrows(DataAccessException.class, () -> repository.inviteStaff(auroraAdministrator.session().sessionTokenHash(),
				syntheticEmail("overlong-invitation"), "[\"RECEPCION\"]", TokenHasher.sha256(TokenHasher.newOpaqueToken()),
				Instant.now().plus(Duration.ofHours(24).plusMinutes(1))));
	}

	@Test
	void rejectsPasswordRecoveryBeyondTheApprovedThirtyMinuteBoundary() {
		assertTrue(repository.requestPasswordRecovery(AccountType.CLIENT, "veterinaria-aurora", syntheticEmail("overlong-recovery"),
				TokenHasher.sha256(TokenHasher.newOpaqueToken()), Instant.now().plus(Duration.ofMinutes(31))).isEmpty());
	}

	@Test
	void rejectsCrossTenantStaffAdministration() {
		SessionPrincipal auroraAdministrator = auroraAdministrator();
		SessionPrincipal bosqueAdministrator = bosqueAdministrator();
		assertThrows(ApiException.class, () -> administrationService.deactivateStaff(bosqueAdministrator,
				auroraAdministrator.session().accountId()));
	}

	@Test
	void rejectsSaasAccountsFromTheTenantStaffInvitationFlow() {
		SessionPrincipal syntheticSaas = new SessionPrincipal(new TenantSession(UUID.randomUUID(), UUID.randomUUID(), null, null, null,
				null, AccountType.SAAS, List.of("ADMIN_SAAS"), TokenHasher.sha256(TokenHasher.newOpaqueToken()),
				Instant.now().plus(Duration.ofHours(1))));
		assertThrows(ApiException.class, () -> administrationService.inviteStaff(syntheticSaas,
				syntheticEmail("saas-invitation"), List.of("RECEPCION")));
	}

	@Test
	void globalSuspensionRevokesOnlyTheAffectedTenantSessions() {
		String globalEmail = System.getenv("AV_DEMO_SAAS_EMAIL");
		String globalPassword = System.getenv("AV_DEMO_SAAS_PASSWORD");
		Assumptions.assumeTrue(globalEmail != null && !globalEmail.isBlank()
				&& globalPassword != null && !globalPassword.isBlank(),
				"Requiere una cuenta SaaS sintética creada por el procedimiento privado de operador.");
		var globalSession = identityAccessService.authenticateSaas(globalEmail, globalPassword);
		SessionPrincipal globalAdministrator = new SessionPrincipal(globalSession.context());
		var aurora = administrationService.listOrganizations(globalAdministrator).stream()
				.filter(item -> item.commercialName().startsWith("Veterinaria Aurora")).findFirst().orElseThrow();

		administrationService.suspendTenant(globalAdministrator, aurora.id(), "Prueba sintética de suspensión", true);
		assertThrows(ApiException.class, () -> identityAccessService.authenticateStaff("veterinaria-aurora",
				"administracion@demo.invalid", System.getenv("AV_DEMO_AURORA_PASSWORD")));
		assertFalse(identityAccessService.authenticateStaff("veterinaria-bosque", "administracion@demo.invalid",
				System.getenv("AV_DEMO_BOSQUE_PASSWORD")).sessionToken().isBlank());
	}

	@Test
	void globalReactivationRestoresOnlyTheAffectedTenantAuthentication() {
		String globalEmail = System.getenv("AV_DEMO_SAAS_EMAIL");
		String globalPassword = System.getenv("AV_DEMO_SAAS_PASSWORD");
		String auroraPassword = System.getenv("AV_DEMO_AURORA_PASSWORD");
		String bosquePassword = System.getenv("AV_DEMO_BOSQUE_PASSWORD");
		Assumptions.assumeTrue(globalEmail != null && !globalEmail.isBlank()
				&& globalPassword != null && !globalPassword.isBlank()
				&& auroraPassword != null && !auroraPassword.isBlank()
				&& bosquePassword != null && !bosquePassword.isBlank(),
				"Requires local synthetic SaaS credentials.");
		SessionPrincipal globalAdministrator = new SessionPrincipal(identityAccessService.authenticateSaas(globalEmail, globalPassword).context());
		var aurora = administrationService.listOrganizations(globalAdministrator).stream()
				.filter(item -> item.commercialName().startsWith("Veterinaria Aurora")).findFirst().orElseThrow();

		administrationService.suspendTenant(globalAdministrator, aurora.id(), "Synthetic reactivation verification", true);
		assertThrows(ApiException.class, () -> identityAccessService.authenticateStaff("veterinaria-aurora",
				"administracion@demo.invalid", auroraPassword));
		administrationService.reactivateTenant(globalAdministrator, aurora.id(), "Synthetic reactivation verification", true);

		assertFalse(identityAccessService.authenticateStaff("veterinaria-aurora", "administracion@demo.invalid",
				auroraPassword).sessionToken().isBlank());
		assertFalse(identityAccessService.authenticateStaff("veterinaria-bosque", "administracion@demo.invalid",
				bosquePassword).sessionToken().isBlank());
	}

	@Test
	void authenticatedSaasAdministratorCannotUseTenantStaffAdministration() {
		String globalEmail = System.getenv("AV_DEMO_SAAS_EMAIL");
		String globalPassword = System.getenv("AV_DEMO_SAAS_PASSWORD");
		Assumptions.assumeTrue(globalEmail != null && !globalEmail.isBlank()
				&& globalPassword != null && !globalPassword.isBlank(),
				"Requires a local synthetic SaaS credential.");
		SessionPrincipal globalAdministrator = new SessionPrincipal(identityAccessService.authenticateSaas(globalEmail, globalPassword).context());

		assertThrows(ApiException.class, () -> administrationService.listStaff(globalAdministrator));
	}

	private SessionPrincipal auroraAdministrator() {
		String password = System.getenv("AV_DEMO_AURORA_PASSWORD");
		assertNotNull(password);
		return new SessionPrincipal(identityAccessService.authenticateStaff("veterinaria-aurora", "administracion@demo.invalid", password).context());
	}

	private SessionPrincipal bosqueAdministrator() {
		String password = System.getenv("AV_DEMO_BOSQUE_PASSWORD");
		assertNotNull(password);
		return new SessionPrincipal(identityAccessService.authenticateStaff("veterinaria-bosque", "administracion@demo.invalid", password).context());
	}

	private String tokenFor(UUID relatedResourceId) {
		String query = localMailbox.requireLatest(relatedResourceId).link().getRawQuery();
		String token = query.substring("token=".length());
		assertEquals(43, token.length(), "El enlace local debe conservar el token opaco completo.");
		assertTrue(token.matches("[A-Za-z0-9_-]{43}"), "El enlace local debe contener un token Base64URL sin alterar.");
		return token;
	}

	private String syntheticEmail(String prefix) {
		return prefix + "-" + UUID.randomUUID() + "@demo.invalid";
	}

	private String syntheticRuc() {
		return String.format("%011d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 100_000_000_000L));
	}

	private String generatedPassword() {
		char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%".toCharArray();
		char[] value = new char[24];
		value[0] = 'A';
		value[1] = 'a';
		value[2] = '2';
		for (int index = 3; index < value.length; index++) {
			value[index] = alphabet[RANDOM.nextInt(alphabet.length)];
		}
		return new String(value);
	}
}
