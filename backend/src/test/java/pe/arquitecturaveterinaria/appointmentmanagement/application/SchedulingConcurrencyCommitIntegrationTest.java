package pe.arquitecturaveterinaria.appointmentmanagement.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Array;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import pe.arquitecturaveterinaria.identityaccess.application.IdentityAccessService;
import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService;
import pe.arquitecturaveterinaria.shared.api.ApiException;

/**
 * The two confirmation calls use independent transactions. A temporary holder
 * makes both wait on PostgreSQL's same per-professional lock before their
 * commits compete; the exact synthetic rows are removed after every execution.
 */
@SpringBootTest
class SchedulingConcurrencyCommitIntegrationTest {

	private static final ZoneId LIMA = ZoneId.of("America/Lima");

	@Autowired
	private SchedulingService schedulingService;

	@Autowired
	private OwnerPetCareService ownerPetCareService;

	@Autowired
	private IdentityAccessService identityAccessService;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final List<UUID> fixtureRequestIds = new ArrayList<>();
	private UUID fixtureTenantId;

	@Test
	void commitsAtMostOneConfirmationForConcurrentOverlappingRequests() throws Exception {
		SessionPrincipal administrator = administrator();
		SessionPrincipal client = client();
		OfferedService service = schedulingService.listPublishedServicesForClient(client).stream()
				.filter(item -> "CLINICA".equals(item.serviceType()))
				.findFirst().orElseThrow(() -> new AssertionError("Se requiere un servicio clinico publicado."));
		UUID professionalId = schedulingService.listAssignableProfessionals(administrator).stream()
				.filter(item -> item.roles().contains("VETERINARIO"))
				.map(AssignableProfessional::principalId).findFirst()
				.orElseThrow(() -> new AssertionError("Se requiere un veterinario asignable."));
		UUID petId = ownerPetCareService.listCurrentPetsForClient(client).stream().findFirst()
				.orElseThrow(() -> new AssertionError("Se requiere una mascota CLIENT sintetica activa.")).id();
		Instant startsAt = nextAvailableSlot(administrator, service, professionalId);
		fixtureTenantId = administrator.session().tenantId();
		ScheduleRequest firstRequest = createRequest(client, petId, service.id(), startsAt, "Carrera sintetica uno");
		ScheduleRequest secondRequest = createRequest(client, petId, service.id(), startsAt, "Carrera sintetica dos");

		try (Connection heldLock = dataSource.getConnection()) {
			heldLock.setAutoCommit(false);
			acquireProfessionalLock(heldLock, professionalId);
			int baselineWaiters = advisoryWaiterCount();
			CountDownLatch ready = new CountDownLatch(2);
			CountDownLatch start = new CountDownLatch(1);
			ExecutorService executor = Executors.newFixedThreadPool(2);
			try {
				Future<ConfirmationAttempt> first = executor.submit(
						() -> confirmWhenReleased(administrator, firstRequest.id(), professionalId, ready, start));
				Future<ConfirmationAttempt> second = executor.submit(
						() -> confirmWhenReleased(administrator, secondRequest.id(), professionalId, ready, start));
				assertTrue(ready.await(5, TimeUnit.SECONDS), "Las dos transacciones deben estar preparadas.");
				start.countDown();
				assertTrue(awaitAdvisoryWaiters(baselineWaiters + 2, Duration.ofSeconds(5)),
						"Las dos confirmaciones deben esperar el mismo candado antes del COMMIT.");
				heldLock.commit();

				List<ConfirmationAttempt> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
				assertEquals(1, outcomes.stream().filter(ConfirmationAttempt::confirmed).count());
				assertEquals(1, outcomes.stream().filter(item -> "B03_SLOT_UNAVAILABLE".equals(item.errorCode())).count());
			} finally {
				if (!heldLock.getAutoCommit()) {
					heldLock.rollback();
				}
				executor.shutdownNow();
			}
		}

		List<ScheduleRequest> persisted = schedulingService.listScheduleRequests(administrator).stream()
				.filter(item -> fixtureRequestIds.contains(item.id())).toList();
		assertEquals(2, persisted.size());
		assertEquals(1, persisted.stream().filter(item -> "CONFIRMADA".equals(item.status())).count());
		assertEquals(1, persisted.stream().filter(item -> "PENDIENTE".equals(item.status())).count());
		assertFalse(schedulingService.checkAvailability(administrator,
				new SchedulingService.AvailabilityCommand(service.id(), professionalId, startsAt)).available());
	}

	@AfterEach
	void removeCommittedFixtures() throws SQLException {
		if (fixtureRequestIds.isEmpty()) {
			return;
		}
		try (Connection connection = DriverManager.getConnection(requiredEnvironment("AV_DB_URL"),
				requiredEnvironment("AV_MIGRATION_DB_USER"), requiredEnvironment("AV_MIGRATION_DB_PASSWORD"))) {
			connection.setAutoCommit(false);
			deleteByRequestIds(connection, """
					delete from appointment_management.grooming_attention
					where reservation_id in (
					    select id from appointment_management.schedule_reservation where request_id = any (?)
					)
					""");
			deleteByRequestIds(connection, "delete from appointment_management.schedule_change_event where request_id = any (?)");
			deleteByRequestIds(connection, "delete from appointment_management.schedule_reservation where request_id = any (?)");
			deleteByRequestIds(connection, "delete from appointment_management.schedule_proposal where request_id = any (?)");
			deleteByRequestIds(connection, "delete from appointment_management.schedule_request where tenant_id = ? and id = any (?)");
			assertEquals(0, remainingFixtures(connection));
			connection.commit();
		}
	}

	private ScheduleRequest createRequest(SessionPrincipal client, UUID petId, UUID serviceId, Instant startsAt, String observation) {
		ScheduleRequest request = schedulingService.createClientScheduleRequest(client,
				new SchedulingService.ClientScheduleRequestCommand(petId, serviceId, startsAt, observation, UUID.randomUUID()));
		fixtureRequestIds.add(request.id());
		return request;
	}

	private ConfirmationAttempt confirmWhenReleased(SessionPrincipal administrator, UUID requestId, UUID professionalId,
			CountDownLatch ready, CountDownLatch start) throws InterruptedException {
		ready.countDown();
		assertTrue(start.await(5, TimeUnit.SECONDS), "La barrera de concurrencia no fue liberada.");
		try {
			schedulingService.confirmScheduleRequest(administrator, requestId,
					new SchedulingService.ConfirmationCommand(professionalId));
			return new ConfirmationAttempt(true, null);
		} catch (ApiException exception) {
			return new ConfirmationAttempt(false, exception.code());
		}
	}

	private Instant nextAvailableSlot(SessionPrincipal administrator, OfferedService service, UUID professionalId) {
		List<OperatingHours> organizationHours = schedulingService.listOperatingHours(administrator);
		List<ProfessionalAvailability> professionalHours = schedulingService.listProfessionalAvailability(administrator).stream()
				.filter(item -> professionalId.equals(item.professionalPrincipalId())).toList();
		LocalDate deadline = LocalDate.now(LIMA).plusDays(22);
		for (LocalDate date = LocalDate.now(LIMA).plusDays(1); date.isBefore(deadline); date = date.plusDays(1)) {
			int weekday = date.getDayOfWeek().getValue();
			for (OperatingHours organizationRange : organizationHours) {
				for (ProfessionalAvailability professionalRange : professionalHours) {
					if (organizationRange.weekday() != weekday || professionalRange.weekday() != weekday) {
						continue;
					}
					LocalTime start = later(organizationRange.startsAtLocal(), professionalRange.startsAtLocal());
					LocalTime end = earlier(organizationRange.endsAtLocal(), professionalRange.endsAtLocal());
					for (LocalTime candidate = start; !candidate.plusMinutes(service.durationMinutes()).isAfter(end);
							candidate = candidate.plusMinutes(15)) {
						Instant startsAt = date.atTime(candidate).atZone(LIMA).toInstant();
						if (schedulingService.checkAvailability(administrator,
								new SchedulingService.AvailabilityCommand(service.id(), professionalId, startsAt)).available()) {
							return startsAt;
						}
					}
				}
			}
		}
		throw new AssertionError("No se encontro una franja futura disponible para la regresion concurrente.");
	}

	private void acquireProfessionalLock(Connection connection, UUID professionalId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"select pg_advisory_xact_lock(hashtextextended(?::text, 8303))")) {
			statement.setString(1, professionalId.toString());
			statement.execute();
		}
	}

	private int advisoryWaiterCount() {
		Integer count = jdbcTemplate.queryForObject("select count(*) from pg_locks where locktype = 'advisory' and not granted",
				Integer.class);
		return count == null ? 0 : count;
	}

	private boolean awaitAdvisoryWaiters(int expected, Duration timeout) throws InterruptedException {
		Instant deadline = Instant.now().plus(timeout);
		while (Instant.now().isBefore(deadline)) {
			if (advisoryWaiterCount() >= expected) {
				return true;
			}
			Thread.sleep(25);
		}
		return false;
	}

	private void deleteByRequestIds(Connection connection, String sql) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			Array requestIds = connection.createArrayOf("uuid", fixtureRequestIds.toArray(UUID[]::new));
			if (sql.contains("tenant_id = ?")) {
				statement.setObject(1, fixtureTenantId);
				statement.setArray(2, requestIds);
			} else {
				statement.setArray(1, requestIds);
			}
			statement.executeUpdate();
			requestIds.free();
		}
	}

	private int remainingFixtures(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"select count(*) from appointment_management.schedule_request where tenant_id = ? and id = any (?)")) {
			Array requestIds = connection.createArrayOf("uuid", fixtureRequestIds.toArray(UUID[]::new));
			statement.setObject(1, fixtureTenantId);
			statement.setArray(2, requestIds);
			try (ResultSet result = statement.executeQuery()) {
				result.next();
				int count = result.getInt(1);
				requestIds.free();
				return count;
			}
		}
	}

	private SessionPrincipal administrator() {
		return new SessionPrincipal(identityAccessService.authenticateStaff("veterinaria-aurora", "administracion@demo.invalid",
				requiredEnvironment("AV_DEMO_AURORA_PASSWORD")).context());
	}

	private SessionPrincipal client() {
		return new SessionPrincipal(identityAccessService.authenticateClient("veterinaria-aurora",
				requiredEnvironment("AV_DEMO_CLIENT_AURORA_EMAIL"), requiredEnvironment("AV_DEMO_CLIENT_AURORA_PASSWORD")).context());
	}

	private String requiredEnvironment(String name) {
		String value = System.getenv(name);
		assertNotNull(value, "La regresion requiere la variable local " + name + ".");
		return value;
	}

	private LocalTime later(LocalTime first, LocalTime second) {
		return first.isAfter(second) ? first : second;
	}

	private LocalTime earlier(LocalTime first, LocalTime second) {
		return first.isBefore(second) ? first : second;
	}

	private record ConfirmationAttempt(boolean confirmed, String errorCode) {
	}
}
