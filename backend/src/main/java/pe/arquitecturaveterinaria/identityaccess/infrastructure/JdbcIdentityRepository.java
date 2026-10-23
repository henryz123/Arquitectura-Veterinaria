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

import pe.arquitecturaveterinaria.identityaccess.application.TenantSession;
import pe.arquitecturaveterinaria.identityaccess.domain.AccountType;

/**
 * Identity authentication is delegated to bounded PostgreSQL routines. The
 * runtime role never reads stored password hashes or writes session rows.
 */
@Repository
public class JdbcIdentityRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcIdentityRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public Optional<TenantSession> authenticateAndCreateSession(AccountType accountType, String tenantSlug, String normalizedEmail,
			String password, String sessionTokenHash, String csrfTokenHash, Instant expiresAt) {
		List<TenantSession> sessions = jdbcTemplate.query("""
				select session_id, principal_id, tenant_id, tenant_slug, tenant_commercial_name, tenant_status,
				       account_type, roles, expires_at
				from identity_auth.authenticate_and_create_session(?, ?, ?, ?, ?, ?, ?)
				""", (resultSet, rowNum) -> mapSession(resultSet, rowNum, sessionTokenHash), accountType.name(), tenantSlug,
				normalizedEmail, password, sessionTokenHash, csrfTokenHash, Timestamp.from(expiresAt));
		return sessions.stream().findFirst();
	}

	public Optional<TenantSession> findActiveSession(String sessionTokenHash) {
		List<TenantSession> sessions = jdbcTemplate.query("""
				select session_id, principal_id, tenant_id, tenant_slug, tenant_commercial_name, tenant_status,
				       account_type, roles, expires_at
				from identity_auth.resolve_active_session_context(?)
				""", (resultSet, rowNum) -> mapSession(resultSet, rowNum, sessionTokenHash), sessionTokenHash);
		return sessions.stream().findFirst();
	}

	public boolean isCsrfTokenValid(String sessionTokenHash, String csrfTokenHash) {
		Boolean valid = jdbcTemplate.queryForObject(
				"select identity_auth.is_csrf_token_valid(?, ?)", Boolean.class, sessionTokenHash, csrfTokenHash);
		return Boolean.TRUE.equals(valid);
	}

	public void revokeSession(String sessionTokenHash) {
		jdbcTemplate.queryForObject("select identity_auth.revoke_session_by_token(?)", Boolean.class, sessionTokenHash);
	}

	private TenantSession mapSession(ResultSet resultSet, int rowNum, String sessionTokenHash) throws SQLException {
		return new TenantSession(
				resultSet.getObject("session_id", UUID.class),
				resultSet.getObject("principal_id", UUID.class),
				resultSet.getObject("tenant_id", UUID.class),
				resultSet.getString("tenant_slug"),
				resultSet.getString("tenant_commercial_name"),
				resultSet.getString("tenant_status"),
				AccountType.valueOf(resultSet.getString("account_type")),
				readRoles(resultSet.getString("roles")),
				sessionTokenHash,
				resultSet.getTimestamp("expires_at").toInstant());
	}

	private List<String> readRoles(String json) throws SQLException {
		String normalizedJson = json == null ? null : json.trim();
		if (normalizedJson == null || "[]".equals(normalizedJson)) {
			return List.of();
		}
		if (!normalizedJson.matches("^\\[(?:\\s*\\\"[A-Z_]+\\\"\\s*)(?:,\\s*\\\"[A-Z_]+\\\"\\s*)*\\]$")) {
			throw new SQLException("No fue posible interpretar los roles de sesión.");
		}
		return Arrays.stream(normalizedJson.substring(1, normalizedJson.length() - 1).split(","))
				.map(String::trim)
				.map(value -> value.substring(1, value.length() - 1))
				.toList();
	}
}
