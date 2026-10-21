package pe.arquitecturaveterinaria.identityaccess.infrastructure;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class TenantContextJdbc {

	private final JdbcTemplate jdbcTemplate;

	TenantContextJdbc(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	void setSessionHash(String sessionHash) {
		jdbcTemplate.queryForObject("select set_config('app.session_hash', ?, true)", String.class, sessionHash);
	}

	void setTenantId(UUID tenantId) {
		jdbcTemplate.queryForObject("select set_config('app.tenant_id', ?, true)", String.class, tenantId.toString());
	}
}
