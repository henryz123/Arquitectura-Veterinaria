package pe.arquitecturaveterinaria.identityaccess.infrastructure;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TenantContextJdbc {

	private final JdbcTemplate jdbcTemplate;

	public TenantContextJdbc(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void setSessionHash(String sessionHash) {
		jdbcTemplate.queryForObject("select set_config('app.session_hash', ?, true)", String.class, sessionHash);
	}

	public void setTenantId(UUID tenantId) {
		jdbcTemplate.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
	}

	public void setPrincipalId(UUID principalId) {
		jdbcTemplate.queryForObject("select set_config('app.principal_id', ?, true)", String.class, principalId.toString());
	}
}
