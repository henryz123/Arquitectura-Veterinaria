package pe.arquitecturaveterinaria.identityaccess.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import pe.arquitecturaveterinaria.identityaccess.domain.AccountType;

/**
 * JDBC adapter for the bounded identity and administration operations. The
 * runtime role never queries or mutates identity tables directly.
 */
@Repository
public class B01JdbcRepository {

	private final JdbcTemplate jdbcTemplate;

	public B01JdbcRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * Returns PostgreSQL's transaction timestamp for a bounded-token operation.
	 * The database functions enforce their maxima against the same transaction
	 * timestamp, avoiding a clock-skew rejection at an otherwise valid boundary.
	 */
	public Instant transactionTimestamp() {
		Timestamp value = jdbcTemplate.queryForObject("select current_timestamp", Timestamp.class);
		if (value == null) {
			throw new IllegalStateException("PostgreSQL did not return a transaction timestamp.");
		}
		return value.toInstant();
	}

	public RegistrationStart startTenantRegistration(Registration registration, String verificationTokenHash, Instant expiresAt) {
		return jdbcTemplate.queryForObject("""
				select registration_id, registration_status, ruc_reserved_until
				from tenant_admin.start_tenant_registration(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", (resultSet, rowNum) -> new RegistrationStart(resultSet.getObject("registration_id", UUID.class),
				resultSet.getString("registration_status"), instant(resultSet, "ruc_reserved_until")), registration.commercialName(),
				registration.commercialAddress(), registration.commercialPhone(), registration.commercialEmail(),
				registration.normalizedCommercialEmail(), registration.initialAdministratorEmail(),
				registration.normalizedInitialAdministratorEmail(), registration.ruc(), timestamp(registration.rucReservedUntil()),
				verificationTokenHash, timestamp(expiresAt));
	}

	public RegistrationResumption resumeTenantRegistration(UUID registrationId, String normalizedInitialAdministratorEmail,
			String tokenHash, Instant expiresAt) {
		return jdbcTemplate.queryForObject("""
				select registration_id, registration_status, ruc_reserved_until, link_purpose
				from tenant_admin.resume_tenant_registration(?, ?, ?, ?)
				""", (resultSet, rowNum) -> new RegistrationResumption(resultSet.getObject("registration_id", UUID.class),
				resultSet.getString("registration_status"), instant(resultSet, "ruc_reserved_until"),
				resultSet.getString("link_purpose")), registrationId, normalizedInitialAdministratorEmail, tokenHash, timestamp(expiresAt));
	}

	public EmailVerification verifyEmailLink(String verificationTokenHash, String completionGrantHash, Instant completionExpiresAt) {
		return jdbcTemplate.queryForObject("""
				select verification_status, verified_account_type, resource_id
				from identity_auth.verify_email_link(?, ?, ?)
				""", (resultSet, rowNum) -> new EmailVerification(resultSet.getString("verification_status"),
				AccountType.valueOf(resultSet.getString("verified_account_type")), resultSet.getObject("resource_id", UUID.class)),
				verificationTokenHash, completionGrantHash, timestamp(completionExpiresAt));
	}

	public TenantActivation completeTenantRegistration(String completionGrantHash, String passwordHash, String tenantSlug) {
		return jdbcTemplate.queryForObject("""
				select tenant_id, tenant_slug, commercial_name
				from tenant_admin.complete_tenant_registration(?, ?, ?)
				""", (resultSet, rowNum) -> new TenantActivation(resultSet.getObject("tenant_id", UUID.class),
				resultSet.getString("tenant_slug"), resultSet.getString("commercial_name")), completionGrantHash, passwordHash, tenantSlug);
	}

	public UUID registerClient(String tenantSlug, String normalizedEmail, String passwordHash, String verificationTokenHash, Instant expiresAt) {
		return jdbcTemplate.queryForObject("select identity_auth.register_client(?, ?, ?, ?, ?)", UUID.class, tenantSlug, normalizedEmail,
				passwordHash, verificationTokenHash, timestamp(expiresAt));
	}

	public Optional<UUID> resendClientVerification(String tenantSlug, String normalizedEmail, String verificationTokenHash, Instant expiresAt) {
		return jdbcTemplate.query("select identity_auth.resend_client_email_verification(?, ?, ?, ?)",
				(resultSet, rowNum) -> resultSet.getObject(1, UUID.class), tenantSlug, normalizedEmail, verificationTokenHash,
				timestamp(expiresAt)).stream().filter(java.util.Objects::nonNull).findFirst();
	}

	public Optional<RecoveryTarget> requestPasswordRecovery(AccountType accountType, String tenantSlug, String normalizedEmail,
			String recoveryTokenHash, Instant expiresAt) {
		return jdbcTemplate.query("""
				select principal_id, tenant_id from identity_auth.request_password_recovery(?, ?, ?, ?, ?)
				""", (resultSet, rowNum) -> new RecoveryTarget(resultSet.getObject("principal_id", UUID.class),
				resultSet.getObject("tenant_id", UUID.class)), accountType.name(), tenantSlug, normalizedEmail, recoveryTokenHash,
				timestamp(expiresAt)).stream().findFirst();
	}

	public void resetPassword(String recoveryTokenHash, String passwordHash) {
		jdbcTemplate.queryForObject("select identity_auth.reset_password_with_recovery(?, ?)", Boolean.class, recoveryTokenHash, passwordHash);
	}

	public StaffInvitation inviteStaff(String sessionTokenHash, String normalizedEmail, String rolesJson, String invitationTokenHash,
			Instant expiresAt) {
		return jdbcTemplate.queryForObject("""
				select principal_id, roles from identity_auth.invite_staff(?, ?, ?::jsonb, ?, ?)
				""", (resultSet, rowNum) -> new StaffInvitation(resultSet.getObject("principal_id", UUID.class),
				readRoles(resultSet.getString("roles"))), sessionTokenHash, normalizedEmail, rolesJson, invitationTokenHash, timestamp(expiresAt));
	}

	public void acceptStaffInvitation(String invitationTokenHash, String passwordHash) {
		jdbcTemplate.queryForObject("select identity_auth.accept_staff_invitation(?, ?)", UUID.class, invitationTokenHash, passwordHash);
	}

	public List<StaffAccount> listStaffForSession(String sessionTokenHash) {
		return jdbcTemplate.query("""
				select principal_id, normalized_email, account_status, roles
				from identity_auth.list_staff_for_session(?)
				""", (resultSet, rowNum) -> new StaffAccount(resultSet.getObject("principal_id", UUID.class),
				resultSet.getString("normalized_email"), resultSet.getString("account_status"), readRoles(resultSet.getString("roles"))), sessionTokenHash);
	}

	public StaffAccount replaceStaffRoles(String sessionTokenHash, UUID targetPrincipalId, String rolesJson) {
		return jdbcTemplate.queryForObject("""
				select normalized_email, account_status, roles
				from identity_auth.replace_staff_roles(?, ?, ?::jsonb)
				""", (resultSet, rowNum) -> new StaffAccount(targetPrincipalId, resultSet.getString("normalized_email"),
				resultSet.getString("account_status"), readRoles(resultSet.getString("roles"))), sessionTokenHash, targetPrincipalId, rolesJson);
	}

	public boolean deactivateStaff(String sessionTokenHash, UUID targetPrincipalId) {
		Boolean deactivated = jdbcTemplate.queryForObject("select identity_auth.deactivate_staff(?, ?)", Boolean.class,
				sessionTokenHash, targetPrincipalId);
		return Boolean.TRUE.equals(deactivated);
	}

	public List<Organization> listOrganizations(String sessionTokenHash) {
		return jdbcTemplate.query("""
				select tenant_id, commercial_name, ruc, commercial_address, commercial_phone, commercial_email, registered_at, status
				from tenant_admin.list_organizations_for_session(?)
				""", this::mapOrganization, sessionTokenHash);
	}

	public boolean changeTenantStatus(String sessionTokenHash, UUID tenantId, String status, String reason) {
		Boolean changed = jdbcTemplate.queryForObject("select tenant_admin.change_tenant_status_for_session(?, ?, ?, ?)", Boolean.class,
				sessionTokenHash, tenantId, status, reason);
		return Boolean.TRUE.equals(changed);
	}

	public void createRucReview(RucReview review) {
		jdbcTemplate.queryForObject("select tenant_admin.submit_ruc_review(?, ?, ?, ?, ?)", UUID.class,
				review.ruc(), review.requesterEmail(), review.normalizedRequesterEmail(), review.contactChannel(), review.requestReason());
	}

	public List<RucReview> listRucReviews(String sessionTokenHash) {
		return jdbcTemplate.query("""
				select review_id as id, ruc, requester_email, normalized_requester_email, contact_channel, request_reason, status,
				       resolution_reason, resolved_by_principal_id, resolved_at, affected_tenant_id
				from tenant_admin.list_ruc_reviews_for_session(?)
				""", this::mapRucReview, sessionTokenHash);
	}

	public boolean resolveRucReview(String sessionTokenHash, UUID reviewId, String status, String reason, boolean releaseRuc) {
		Boolean resolved = jdbcTemplate.queryForObject("select tenant_admin.resolve_ruc_review_for_session(?, ?, ?, ?, ?)", Boolean.class,
				sessionTokenHash, reviewId, status, reason, releaseRuc);
		return Boolean.TRUE.equals(resolved);
	}

	public void requestAdministrativeRecovery(String tenantSlug, String applicantEmail, String normalizedApplicantEmail,
			String evidenceReference) {
		jdbcTemplate.query("select tenant_admin.request_administrative_recovery(?, ?, ?, ?)", resultSet -> { },
				tenantSlug, applicantEmail, normalizedApplicantEmail, evidenceReference);
	}

	public List<AdministrativeRecovery> listAdministrativeRecoveries(String sessionTokenHash) {
		return jdbcTemplate.query("""
				select recovery_id as id, tenant_id, applicant_email, normalized_applicant_email, evidence_reference, status,
				       decision_reason, reviewed_by_principal_id, reviewed_at
				from tenant_admin.list_administrative_recoveries_for_session(?)
				""", this::mapRecovery, sessionTokenHash);
	}

	public RecoveryReview reviewAdministrativeRecovery(String sessionTokenHash, UUID recoveryId, String status, String reason,
			boolean legitimacyChecked, String invitationTokenHash, Instant invitationExpiresAt) {
		return jdbcTemplate.queryForObject("""
				select principal_id, tenant_id, applicant_email, executed
				from tenant_admin.review_administrative_recovery_for_session(?, ?, ?, ?, ?, ?, ?)
				""", (resultSet, rowNum) -> new RecoveryReview(resultSet.getObject("principal_id", UUID.class),
				resultSet.getObject("tenant_id", UUID.class), resultSet.getString("applicant_email"), resultSet.getBoolean("executed")),
				sessionTokenHash, recoveryId, status, reason, legitimacyChecked, invitationTokenHash, timestamp(invitationExpiresAt));
	}

	private Organization mapOrganization(ResultSet resultSet, int rowNum) throws SQLException {
		return new Organization(resultSet.getObject("tenant_id", UUID.class), resultSet.getString("commercial_name"), resultSet.getString("ruc"),
				resultSet.getString("commercial_address"), resultSet.getString("commercial_phone"), resultSet.getString("commercial_email"),
				instant(resultSet, "registered_at"), resultSet.getString("status"));
	}

	private RucReview mapRucReview(ResultSet resultSet, int rowNum) throws SQLException {
		return new RucReview(resultSet.getObject("id", UUID.class), resultSet.getString("ruc"), resultSet.getString("requester_email"),
				resultSet.getString("normalized_requester_email"), resultSet.getString("contact_channel"), resultSet.getString("request_reason"),
				resultSet.getString("status"), resultSet.getString("resolution_reason"), resultSet.getObject("resolved_by_principal_id", UUID.class),
				instant(resultSet, "resolved_at"), resultSet.getObject("affected_tenant_id", UUID.class));
	}

	private AdministrativeRecovery mapRecovery(ResultSet resultSet, int rowNum) throws SQLException {
		return new AdministrativeRecovery(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
				resultSet.getString("applicant_email"), resultSet.getString("normalized_applicant_email"), resultSet.getString("evidence_reference"),
				resultSet.getString("status"), resultSet.getString("decision_reason"), resultSet.getObject("reviewed_by_principal_id", UUID.class),
				instant(resultSet, "reviewed_at"));
	}

	private List<String> readRoles(String json) throws SQLException {
		String normalizedJson = json == null ? null : json.trim();
		if (normalizedJson == null || "[]".equals(normalizedJson)) {
			return List.of();
		}
		if (!normalizedJson.matches("^\\[(?:\\s*\\\"[A-Z_]+\\\"\\s*)(?:,\\s*\\\"[A-Z_]+\\\"\\s*)*\\]$")) {
			throw new SQLException("No fue posible interpretar los roles laborales.");
		}
		return Arrays.stream(normalizedJson.substring(1, normalizedJson.length() - 1).split(","))
				.map(String::trim)
				.map(value -> value.substring(1, value.length() - 1))
				.toList();
	}

	private Instant instant(ResultSet resultSet, String column) throws SQLException {
		Timestamp value = resultSet.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	private Timestamp timestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	public record Registration(String commercialName, String commercialAddress, String commercialPhone, String commercialEmail,
			String normalizedCommercialEmail, String initialAdministratorEmail, String normalizedInitialAdministratorEmail,
			String ruc, Instant rucReservedUntil) { }
	public record RegistrationStart(UUID registrationId, String status, Instant rucReservedUntil) { }
	public record RegistrationResumption(UUID registrationId, String status, Instant rucReservedUntil, String linkPurpose) { }
	public record EmailVerification(String status, AccountType accountType, UUID resourceId) { }
	public record TenantActivation(UUID tenantId, String tenantSlug, String commercialName) { }
	public record RecoveryTarget(UUID principalId, UUID tenantId) { }
	public record StaffInvitation(UUID principalId, List<String> roles) { }
	public record StaffAccount(UUID principalId, String normalizedEmail, String status, List<String> roles) { }
	public record Organization(UUID id, String commercialName, String ruc, String commercialAddress, String commercialPhone,
			String commercialEmail, Instant registeredAt, String status) { }
	public record RucReview(UUID id, String ruc, String requesterEmail, String normalizedRequesterEmail, String contactChannel,
			String requestReason, String status, String resolutionReason, UUID resolvedByPrincipalId, Instant resolvedAt,
			UUID affectedTenantId) {
		public RucReview(UUID id, String ruc, String requesterEmail, String normalizedRequesterEmail, String contactChannel,
				String requestReason) {
			this(id, ruc, requesterEmail, normalizedRequesterEmail, contactChannel, requestReason, "PENDIENTE", null, null, null, null);
		}
	}
	public record AdministrativeRecovery(UUID id, UUID tenantId, String applicantEmail, String normalizedApplicantEmail,
			String evidenceReference, String status, String decisionReason, UUID reviewedByPrincipalId, Instant reviewedAt) { }
	public record RecoveryReview(UUID principalId, UUID tenantId, String applicantEmail, boolean executed) { }
}
