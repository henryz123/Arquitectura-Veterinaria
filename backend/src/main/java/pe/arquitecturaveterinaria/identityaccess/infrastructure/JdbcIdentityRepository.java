package pe.arquitecturaveterinaria.identityaccess.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import pe.arquitecturaveterinaria.identityaccess.application.TenantSession;
import pe.arquitecturaveterinaria.identityaccess.domain.AccountType;

@Repository
public class JdbcIdentityRepository {

	private final JdbcTemplate jdbcTemplate;
	private final TenantContextJdbc tenantContext;

	public JdbcIdentityRepository(JdbcTemplate jdbcTemplate, TenantContextJdbc tenantContext) {
		this.jdbcTemplate = jdbcTemplate;
		this.tenantContext = tenantContext;
	}

	public Optional<StaffIdentity> lookupStaff(String tenantSlug, String normalizedEmail) {
		List<StaffIdentity> identities = jdbcTemplate.query("""
				select account_id, tenant_id, tenant_slug, tenant_commercial_name, tenant_status,
				       account_status, password_hash, role_code
				from identity_auth.lookup_staff_identity(?, ?)
				""", this::mapStaffIdentity, tenantSlug, normalizedEmail);
		return identities.stream().findFirst();
	}

	public void createSession(UUID sessionId, UUID accountId, UUID tenantId, String sessionTokenHash,
			String csrfTokenHash, String roleCode, Instant expiresAt) {
		tenantContext.setTenantId(tenantId);
		jdbcTemplate.update("""
				insert into identity_auth.user_session
				(id, account_id, tenant_id, account_type, role_code, token_hash, csrf_hash, expires_at)
				values (?, ?, ?, 'STAFF', ?, ?, ?, ?)
				""", sessionId, accountId, tenantId, roleCode, sessionTokenHash, csrfTokenHash, expiresAt);
	}

	public Optional<TenantSession> findActiveSession(String sessionTokenHash) {
		tenantContext.setSessionHash(sessionTokenHash);
		List<SessionRow> sessions = jdbcTemplate.query("""
				select id, account_id, tenant_id, account_type, role_code, csrf_hash, expires_at
				from identity_auth.user_session
				where token_hash = ? and revoked_at is null and expires_at > current_timestamp
				""", this::mapSessionRow, sessionTokenHash);
		return sessions.stream().findFirst().flatMap(session -> findTenantSession(session));
	}

	public void revokeSession(UUID sessionId, UUID tenantId) {
		tenantContext.setTenantId(tenantId);
		jdbcTemplate.update("update identity_auth.user_session set revoked_at = current_timestamp where id = ?", sessionId);
	}

	private Optional<TenantSession> findTenantSession(SessionRow session) {
		tenantContext.setTenantId(session.tenantId());
		List<TenantRow> tenants = jdbcTemplate.query("""
				select id, slug, commercial_name, status
				from tenancy.tenant
				where id = ? and status = 'ACTIVO'
				""", this::mapTenantRow, session.tenantId());
		return tenants.stream().findFirst().map(tenant -> new TenantSession(
				session.id(), session.accountId(), tenant.id(), tenant.slug(), tenant.commercialName(), tenant.status(),
				session.accountType(), List.of(session.roleCode()), session.csrfHash(), session.expiresAt()));
	}

	private StaffIdentity mapStaffIdentity(ResultSet resultSet, int rowNum) throws SQLException {
		return new StaffIdentity(
				resultSet.getObject("account_id", UUID.class),
				resultSet.getObject("tenant_id", UUID.class),
				resultSet.getString("tenant_slug"),
				resultSet.getString("tenant_commercial_name"),
				resultSet.getString("tenant_status"),
				resultSet.getString("account_status"),
				resultSet.getString("password_hash"),
				resultSet.getString("role_code"));
	}

	private SessionRow mapSessionRow(ResultSet resultSet, int rowNum) throws SQLException {
		return new SessionRow(
				resultSet.getObject("id", UUID.class),
				resultSet.getObject("account_id", UUID.class),
				resultSet.getObject("tenant_id", UUID.class),
				AccountType.valueOf(resultSet.getString("account_type")),
				resultSet.getString("role_code"),
				resultSet.getString("csrf_hash"),
				resultSet.getTimestamp("expires_at").toInstant());
	}

	private TenantRow mapTenantRow(ResultSet resultSet, int rowNum) throws SQLException {
		return new TenantRow(resultSet.getObject("id", UUID.class), resultSet.getString("slug"),
				resultSet.getString("commercial_name"), resultSet.getString("status"));
	}

	public record StaffIdentity(UUID accountId, UUID tenantId, String tenantSlug, String tenantCommercialName,
			String tenantStatus, String accountStatus, String passwordHash, String roleCode) {
	}

	private record SessionRow(UUID id, UUID accountId, UUID tenantId, AccountType accountType, String roleCode,
			String csrfHash, Instant expiresAt) {
	}

	private record TenantRow(UUID id, String slug, String commercialName, String status) {
	}
}
