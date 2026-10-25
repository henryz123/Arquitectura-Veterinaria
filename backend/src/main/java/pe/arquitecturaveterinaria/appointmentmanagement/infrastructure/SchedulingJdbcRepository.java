package pe.arquitecturaveterinaria.appointmentmanagement.infrastructure;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import pe.arquitecturaveterinaria.appointmentmanagement.application.AssignableProfessional;
import pe.arquitecturaveterinaria.appointmentmanagement.application.OfferedService;
import pe.arquitecturaveterinaria.appointmentmanagement.application.OperatingHours;
import pe.arquitecturaveterinaria.appointmentmanagement.application.ProfessionalAvailability;
import pe.arquitecturaveterinaria.appointmentmanagement.application.OfferedServiceDraft;
import pe.arquitecturaveterinaria.appointmentmanagement.application.OfferedServiceUpdateDraft;

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

	public List<OfferedService> listOfferedServices() {
		return jdbcTemplate.query("""
				select id, tenant_id, service_type, name, public_description, duration_minutes, reference_price,
				       published, status, created_at, updated_at
				from appointment_management.offered_service
				order by service_type, status, name, id
				""", this::mapOfferedService);
	}

	public OfferedService createOfferedService(UUID tenantId, UUID actorPrincipalId, OfferedServiceDraft draft) {
		return jdbcTemplate.queryForObject("""
				insert into appointment_management.offered_service (
				    tenant_id, service_type, name, public_description, duration_minutes, reference_price, published,
				    created_by_principal_id
				) values (?, ?, ?, ?, ?, ?, ?, ?)
				returning id, tenant_id, service_type, name, public_description, duration_minutes, reference_price,
				          published, status, created_at, updated_at
				""", this::mapOfferedService, tenantId, draft.serviceType(), draft.name(), draft.publicDescription(),
				draft.durationMinutes(), draft.referencePrice(), draft.published(), actorPrincipalId);
	}

	public Optional<OfferedService> updateOfferedService(UUID tenantId, UUID serviceId, OfferedServiceUpdateDraft draft) {
		return jdbcTemplate.query("""
				update appointment_management.offered_service
				set name = ?, public_description = ?, duration_minutes = ?, reference_price = ?, published = ?, status = ?,
				    updated_at = statement_timestamp()
				where tenant_id = ? and id = ?
				returning id, tenant_id, service_type, name, public_description, duration_minutes, reference_price,
				          published, status, created_at, updated_at
				""", this::mapOfferedService, draft.name(), draft.publicDescription(), draft.durationMinutes(),
				draft.referencePrice(), draft.published(), draft.status(), tenantId, serviceId).stream().findFirst();
	}

	public List<OperatingHours> listOperatingHours() {
		return jdbcTemplate.query("""
				select id, weekday, starts_at_local, ends_at_local, created_at, updated_at
				from appointment_management.operating_hours
				order by weekday, starts_at_local, ends_at_local, id
				""", this::mapOperatingHours);
	}

	public OperatingHours createOperatingHours(UUID tenantId, UUID actorPrincipalId, int weekday, LocalTime startsAtLocal,
			LocalTime endsAtLocal) {
		return jdbcTemplate.queryForObject("""
				insert into appointment_management.operating_hours (
				    tenant_id, weekday, starts_at_local, ends_at_local, created_by_principal_id
				) values (?, ?, ?, ?, ?)
				returning id, weekday, starts_at_local, ends_at_local, created_at, updated_at
				""", this::mapOperatingHours, tenantId, weekday, startsAtLocal, endsAtLocal, actorPrincipalId);
	}

	public List<ProfessionalAvailability> listProfessionalAvailability() {
		return jdbcTemplate.query("""
				select id, professional_principal_id, weekday, starts_at_local, ends_at_local, created_at, updated_at
				from appointment_management.professional_availability
				order by weekday, starts_at_local, ends_at_local, professional_principal_id, id
				""", this::mapProfessionalAvailability);
	}

	public ProfessionalAvailability createProfessionalAvailability(UUID tenantId, UUID actorPrincipalId,
			UUID professionalPrincipalId, int weekday, LocalTime startsAtLocal, LocalTime endsAtLocal) {
		return jdbcTemplate.queryForObject("""
				insert into appointment_management.professional_availability (
				    tenant_id, professional_principal_id, weekday, starts_at_local, ends_at_local, created_by_principal_id
				) values (?, ?, ?, ?, ?, ?)
				returning id, professional_principal_id, weekday, starts_at_local, ends_at_local, created_at, updated_at
				""", this::mapProfessionalAvailability, tenantId, professionalPrincipalId, weekday, startsAtLocal,
				endsAtLocal, actorPrincipalId);
	}

	private AssignableProfessional mapAssignableProfessional(ResultSet resultSet, int rowNumber) throws SQLException {
		return new AssignableProfessional(resultSet.getObject("principal_id", java.util.UUID.class),
				parseRoles(resultSet.getString("roles")));
	}

	private OfferedService mapOfferedService(ResultSet resultSet, int rowNumber) throws SQLException {
		return new OfferedService(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
				resultSet.getString("service_type"), resultSet.getString("name"), resultSet.getString("public_description"),
				resultSet.getInt("duration_minutes"), resultSet.getObject("reference_price", BigDecimal.class),
				resultSet.getBoolean("published"), resultSet.getString("status"), instant(resultSet, "created_at"),
				instant(resultSet, "updated_at"));
	}

	private OperatingHours mapOperatingHours(ResultSet resultSet, int rowNumber) throws SQLException {
		return new OperatingHours(resultSet.getObject("id", UUID.class), resultSet.getInt("weekday"),
				resultSet.getObject("starts_at_local", LocalTime.class), resultSet.getObject("ends_at_local", LocalTime.class),
				instant(resultSet, "created_at"), instant(resultSet, "updated_at"));
	}

	private ProfessionalAvailability mapProfessionalAvailability(ResultSet resultSet, int rowNumber) throws SQLException {
		return new ProfessionalAvailability(resultSet.getObject("id", UUID.class),
				resultSet.getObject("professional_principal_id", UUID.class), resultSet.getInt("weekday"),
				resultSet.getObject("starts_at_local", LocalTime.class), resultSet.getObject("ends_at_local", LocalTime.class),
				instant(resultSet, "created_at"), instant(resultSet, "updated_at"));
	}

	private Instant instant(ResultSet resultSet, String column) throws SQLException {
		return resultSet.getTimestamp(column).toInstant();
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
