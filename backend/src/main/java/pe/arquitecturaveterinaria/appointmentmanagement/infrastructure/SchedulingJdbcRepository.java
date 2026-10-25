package pe.arquitecturaveterinaria.appointmentmanagement.infrastructure;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
import pe.arquitecturaveterinaria.appointmentmanagement.application.ScheduleRequest;
import pe.arquitecturaveterinaria.appointmentmanagement.application.ScheduleReservation;

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

	public Optional<OfferedService> findOfferedService(UUID serviceId) {
		return jdbcTemplate.query("""
				select id, tenant_id, service_type, name, public_description, duration_minutes, reference_price,
				       published, status, created_at, updated_at
				from appointment_management.offered_service
				where id = ?
				""", this::mapOfferedService, serviceId).stream().findFirst();
	}

	public List<OfferedService> listPublishedOfferedServices() {
		return jdbcTemplate.query("""
				select id, tenant_id, service_type, name, public_description, duration_minutes, reference_price,
				       published, status, created_at, updated_at
				from appointment_management.offered_service
				where status = 'ACTIVO' and published
				order by service_type, name, id
				""", this::mapOfferedService);
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

	public Optional<ScheduleRequest> createScheduleRequest(UUID tenantId, UUID requesterPrincipalId, UUID ownerId, UUID petId,
			UUID serviceId, Instant requestedStartsAt, int requestedDurationMinutes, String observation, UUID idempotencyKey) {
		return jdbcTemplate.query("""
				insert into appointment_management.schedule_request (
				    tenant_id, requester_principal_id, owner_id, pet_id, service_id, requested_starts_at,
				    requested_duration_minutes, observation, idempotency_key
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				on conflict (tenant_id, requester_principal_id, idempotency_key) do nothing
				returning id, tenant_id, requester_principal_id, owner_id, pet_id, service_id, requested_starts_at,
				          requested_duration_minutes, status, observation, idempotency_key, created_at, updated_at
				""", this::mapScheduleRequest, tenantId, requesterPrincipalId, ownerId, petId, serviceId, timestamp(requestedStartsAt),
				requestedDurationMinutes, observation, idempotencyKey).stream().findFirst();
	}

	public Optional<ScheduleRequest> findScheduleRequestByIdempotency(UUID requesterPrincipalId, UUID idempotencyKey) {
		return jdbcTemplate.query("""
				select id, tenant_id, requester_principal_id, owner_id, pet_id, service_id, requested_starts_at,
				       requested_duration_minutes, status, observation, idempotency_key, created_at, updated_at
				from appointment_management.schedule_request
				where requester_principal_id = ? and idempotency_key = ?
				""", this::mapScheduleRequest, requesterPrincipalId, idempotencyKey).stream().findFirst();
	}

	public Optional<ScheduleRequest> lockScheduleRequest(UUID requestId) {
		return jdbcTemplate.query("""
				select id, tenant_id, requester_principal_id, owner_id, pet_id, service_id, requested_starts_at,
				       requested_duration_minutes, status, observation, idempotency_key, created_at, updated_at
				from appointment_management.schedule_request
				where id = ?
				for update
				""", this::mapScheduleRequest, requestId).stream().findFirst();
	}

	public List<ScheduleRequest> listScheduleRequests() {
		return jdbcTemplate.query("""
				select id, tenant_id, requester_principal_id, owner_id, pet_id, service_id, requested_starts_at,
				       requested_duration_minutes, status, observation, idempotency_key, created_at, updated_at
				from appointment_management.schedule_request
				order by requested_starts_at, created_at, id
				""", this::mapScheduleRequest);
	}

	public List<ScheduleRequest> listScheduleRequestsForRequester(UUID requesterPrincipalId) {
		return jdbcTemplate.query("""
				select id, tenant_id, requester_principal_id, owner_id, pet_id, service_id, requested_starts_at,
				       requested_duration_minutes, status, observation, idempotency_key, created_at, updated_at
				from appointment_management.schedule_request
				where requester_principal_id = ?
				order by requested_starts_at, created_at, id
				""", this::mapScheduleRequest, requesterPrincipalId);
	}

	/** Serializes confirmation for one person until the surrounding JDBC transaction completes. */
	public void lockProfessionalSchedule(UUID professionalPrincipalId) {
		jdbcTemplate.query("select pg_advisory_xact_lock(hashtextextended(?::text, 8303))",
				(resultSet, rowNumber) -> null, professionalPrincipalId.toString());
	}

	public boolean isWithinOperatingHours(int weekday, LocalTime startsAtLocal, LocalTime endsAtLocal) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
				select exists (
				    select 1 from appointment_management.operating_hours
				    where weekday = ? and starts_at_local <= ? and ends_at_local >= ?
				)
				""", Boolean.class, weekday, startsAtLocal, endsAtLocal));
	}

	public boolean isWithinProfessionalAvailability(UUID professionalPrincipalId, int weekday, LocalTime startsAtLocal,
			LocalTime endsAtLocal) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
				select exists (
				    select 1 from appointment_management.professional_availability
				    where professional_principal_id = ? and weekday = ? and starts_at_local <= ? and ends_at_local >= ?
				)
				""", Boolean.class, professionalPrincipalId, weekday, startsAtLocal, endsAtLocal));
	}

	public boolean hasScheduleBlock(UUID professionalPrincipalId, Instant startsAt, Instant endsAt) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
				select exists (
				    select 1 from appointment_management.schedule_block
				    where starts_at < ? and ends_at > ?
				      and (block_scope = 'ORGANIZACION' or professional_principal_id = ?)
				)
				""", Boolean.class, timestamp(endsAt), timestamp(startsAt), professionalPrincipalId));
	}

	public boolean hasCurrentReservationConflict(UUID professionalPrincipalId, Instant startsAt, Instant endsAt) {
		return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
				select exists (
				    select 1 from appointment_management.schedule_reservation
				    where professional_principal_id = ? and is_current
				      and status in ('CONFIRMADA', 'EN_ATENCION')
				      and starts_at < ? and ends_at > ?
				)
				""", Boolean.class, professionalPrincipalId, timestamp(endsAt), timestamp(startsAt)));
	}

	public Optional<ScheduleReservation> findCurrentReservation(UUID requestId) {
		return jdbcTemplate.query("""
				select id, tenant_id, request_id, professional_principal_id, starts_at, ends_at, status, is_current,
				       version, confirmed_by_principal_id, created_at, updated_at
				from appointment_management.schedule_reservation
				where request_id = ? and is_current
				""", this::mapScheduleReservation, requestId).stream().findFirst();
	}

	public ScheduleReservation createConfirmedReservation(UUID tenantId, UUID requestId, UUID professionalPrincipalId,
			Instant startsAt, Instant endsAt, UUID confirmedByPrincipalId) {
		return jdbcTemplate.queryForObject("""
				insert into appointment_management.schedule_reservation (
				    tenant_id, request_id, professional_principal_id, starts_at, ends_at, confirmed_by_principal_id
				) values (?, ?, ?, ?, ?, ?)
				returning id, tenant_id, request_id, professional_principal_id, starts_at, ends_at, status, is_current,
				          version, confirmed_by_principal_id, created_at, updated_at
				""", this::mapScheduleReservation, tenantId, requestId, professionalPrincipalId, timestamp(startsAt), timestamp(endsAt),
				confirmedByPrincipalId);
	}

	public int markScheduleRequestConfirmed(UUID tenantId, UUID requestId) {
		return jdbcTemplate.update("""
				update appointment_management.schedule_request
				set status = 'CONFIRMADA', updated_at = statement_timestamp()
				where tenant_id = ? and id = ? and status = 'PENDIENTE'
				""", tenantId, requestId);
	}

	public void recordScheduleChange(UUID tenantId, UUID requestId, UUID actorPrincipalId, String actionCode,
			Instant previousStartsAt, Instant newStartsAt, UUID previousProfessionalPrincipalId,
			UUID newProfessionalPrincipalId, String reason) {
		jdbcTemplate.update("""
				insert into appointment_management.schedule_change_event (
				    tenant_id, request_id, actor_principal_id, action_code, previous_starts_at, new_starts_at,
				    previous_professional_principal_id, new_professional_principal_id, reason
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", tenantId, requestId, actorPrincipalId, actionCode, timestamp(previousStartsAt), timestamp(newStartsAt),
				previousProfessionalPrincipalId, newProfessionalPrincipalId, reason);
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

	private ScheduleRequest mapScheduleRequest(ResultSet resultSet, int rowNumber) throws SQLException {
		return new ScheduleRequest(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
				resultSet.getObject("requester_principal_id", UUID.class), resultSet.getObject("owner_id", UUID.class),
				resultSet.getObject("pet_id", UUID.class), resultSet.getObject("service_id", UUID.class),
				instant(resultSet, "requested_starts_at"), resultSet.getInt("requested_duration_minutes"),
				resultSet.getString("status"), resultSet.getString("observation"),
				resultSet.getObject("idempotency_key", UUID.class), instant(resultSet, "created_at"), instant(resultSet, "updated_at"));
	}

	private ScheduleReservation mapScheduleReservation(ResultSet resultSet, int rowNumber) throws SQLException {
		return new ScheduleReservation(resultSet.getObject("id", UUID.class), resultSet.getObject("tenant_id", UUID.class),
				resultSet.getObject("request_id", UUID.class), resultSet.getObject("professional_principal_id", UUID.class),
				instant(resultSet, "starts_at"), instant(resultSet, "ends_at"), resultSet.getString("status"),
				resultSet.getBoolean("is_current"), resultSet.getInt("version"),
				resultSet.getObject("confirmed_by_principal_id", UUID.class), instant(resultSet, "created_at"),
				instant(resultSet, "updated_at"));
	}

	private Instant instant(ResultSet resultSet, String column) throws SQLException {
		return resultSet.getTimestamp(column).toInstant();
	}

	private Timestamp timestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
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
