package pe.arquitecturaveterinaria.appointmentmanagement.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import pe.arquitecturaveterinaria.appointmentmanagement.application.AssignableProfessional;

/** JDBC adapter for V020; it has no direct read privilege on identity tables. */
@Repository
public class SchedulingJdbcRepository {

	private final JdbcTemplate jdbcTemplate;

	public SchedulingJdbcRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<AssignableProfessional> listAssignableProfessionals(String sessionTokenHash) {
		return jdbcTemplate.query("""
				select principal_id, roles
				from identity_auth.list_assignable_professionals_for_session(?)
				""", this::mapAssignableProfessional, sessionTokenHash);
	}

	private AssignableProfessional mapAssignableProfessional(ResultSet resultSet, int rowNumber) throws SQLException {
		return new AssignableProfessional(resultSet.getObject("principal_id", java.util.UUID.class),
				parseRoles(resultSet.getString("roles")));
	}

	private List<String> parseRoles(String rawRoles) {
		// V020 emits only these sorted JSON arrays. Rejecting every other value is
		// intentional: the API must never widen its projection if the SQL changes.
		return switch (rawRoles) {
			case "[\"VETERINARIO\"]" -> List.of("VETERINARIO");
			case "[\"GROOMING\"]" -> List.of("GROOMING");
			case "[\"GROOMING\", \"VETERINARIO\"]" -> List.of("GROOMING", "VETERINARIO");
			default -> throw new IllegalStateException("V020 returned an invalid assignable-professional role set.");
		};
	}
}
