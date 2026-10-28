package pe.arquitecturaveterinaria.appointmentmanagement.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import pe.arquitecturaveterinaria.identityaccess.application.B01AdministrationService;
import pe.arquitecturaveterinaria.identityaccess.application.B01AdministrationService.StaffInvitationResult;
import pe.arquitecturaveterinaria.identityaccess.application.IdentityAccessService;
import pe.arquitecturaveterinaria.identityaccess.application.LocalNotificationMailbox;
import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.ClientOwnerLinkResolutionCommand;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.OwnerCommand;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.PetCommand;
import pe.arquitecturaveterinaria.shared.api.ApiException;

/**
 * Uses the local synthetic database only. Spring rolls every fixture and B03
 * operation back after each test, so no synthetic request remains persisted.
 */
@SpringBootTest
@Transactional(transactionManager = "jdbcTransactionManager")
class SchedulingDatabaseIntegrationTest {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final ZoneId LIMA = ZoneId.of("America/Lima");

	@Autowired
	private SchedulingService schedulingService;

	@Autowired
	private OwnerPetCareService ownerPetCareService;

	@Autowired
	private B01AdministrationService administrationService;

	@Autowired
	private IdentityAccessService identityAccessService;

	@Autowired
	private LocalNotificationMailbox localMailbox;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void persistsAClientRequestThenConfirmsOnlyOneCompatibleProfessionalInterval() {
		SessionPrincipal administrator = administrator("veterinaria-aurora", "AV_DEMO_AURORA_PASSWORD");
		SessionPrincipal client = linkedClient(administrator);
		SessionPrincipal veterinarian = veterinarian(administrator);
		Instant startsAt = nextMondayAt(LocalTime.of(9, 0));
		var service = schedulingService.createOfferedService(administrator,
				new SchedulingService.OfferedServiceCommand("CLINICA", "B03 Integracion " + UUID.randomUUID(),
						"Servicio sintetico de integracion", 30, java.math.BigDecimal.valueOf(45), true));
		schedulingService.createOperatingHours(administrator,
				new SchedulingService.OperatingHoursCommand(DayOfWeek.MONDAY.getValue(), LocalTime.of(8, 0), LocalTime.of(18, 0)));
		schedulingService.createProfessionalAvailability(administrator,
				new SchedulingService.ProfessionalAvailabilityCommand(veterinarian.session().accountId(), DayOfWeek.MONDAY.getValue(),
						LocalTime.of(8, 0), LocalTime.of(18, 0)));

		UUID operationId = UUID.randomUUID();
		var request = schedulingService.createClientScheduleRequest(client,
				new SchedulingService.ClientScheduleRequestCommand(ownerPetCareService.listCurrentPetsForClient(client).getFirst().id(),
						service.id(), startsAt, "Control sintetico", operationId));
		assertEquals("PENDIENTE", request.status());
		assertEquals(request.id(), schedulingService.createClientScheduleRequest(client,
				new SchedulingService.ClientScheduleRequestCommand(request.petId(), service.id(), startsAt, "Control sintetico", operationId)).id());

		var availability = schedulingService.checkAvailability(administrator,
				new SchedulingService.AvailabilityCommand(service.id(), veterinarian.session().accountId(), startsAt));
		assertTrue(availability.available());
		var confirmed = schedulingService.confirmScheduleRequest(administrator, request.id(),
				new SchedulingService.ConfirmationCommand(veterinarian.session().accountId()));
		assertEquals("CONFIRMADA", confirmed.status());
		assertEquals(confirmed.id(), schedulingService.confirmScheduleRequest(administrator, request.id(),
				new SchedulingService.ConfirmationCommand(veterinarian.session().accountId())).id());

		var incompatible = schedulingService.createClientScheduleRequest(client,
				new SchedulingService.ClientScheduleRequestCommand(request.petId(), service.id(), startsAt, "Reintento paralelo", UUID.randomUUID()));
		ApiException conflict = assertThrows(ApiException.class, () -> schedulingService.confirmScheduleRequest(administrator,
				incompatible.id(), new SchedulingService.ConfirmationCommand(veterinarian.session().accountId())));
		assertEquals(HttpStatus.CONFLICT, conflict.status());
		assertEquals("B03_SLOT_UNAVAILABLE", conflict.code());

		var consecutive = schedulingService.createClientScheduleRequest(client,
				new SchedulingService.ClientScheduleRequestCommand(request.petId(), service.id(), startsAt.plusSeconds(1800),
						"Franja consecutiva", UUID.randomUUID()));
		assertEquals("CONFIRMADA", schedulingService.confirmScheduleRequest(administrator, consecutive.id(),
				new SchedulingService.ConfirmationCommand(veterinarian.session().accountId())).status());

		assertEquals("CANCELACION_SOLICITADA", schedulingService.cancelClientScheduleRequest(client, request.id(),
				new SchedulingService.CancellationCommand("Cambio de plan")).status());
		assertFalse(schedulingService.checkAvailability(administrator,
				new SchedulingService.AvailabilityCommand(service.id(), veterinarian.session().accountId(), startsAt)).available());
		assertEquals(1, scheduleEventCount(request.id(), "CANCELACION_SOLICITADA"));
		ApiException repeatedCancellation = assertThrows(ApiException.class, () -> schedulingService.cancelClientScheduleRequest(client,
				request.id(), new SchedulingService.CancellationCommand("Reintento sin efecto")));
		assertEquals(HttpStatus.CONFLICT, repeatedCancellation.status());
		assertEquals("B03_CANCELLATION_STATE", repeatedCancellation.code());
		assertEquals(1, scheduleEventCount(request.id(), "CANCELACION_SOLICITADA"));
		assertEquals("CONFIRMADA", schedulingService.resolveCancellation(administrator, request.id(),
				new SchedulingService.CancellationResolutionCommand("CONFIRMADA", "La agenda continua vigente.")).status());
		assertFalse(schedulingService.checkAvailability(administrator,
				new SchedulingService.AvailabilityCommand(service.id(), veterinarian.session().accountId(), startsAt)).available());
		assertEquals("CANCELACION_SOLICITADA", schedulingService.cancelClientScheduleRequest(client, request.id(),
				new SchedulingService.CancellationCommand("No podra asistir")).status());
		assertEquals("CANCELADA", schedulingService.resolveCancellation(administrator, request.id(),
				new SchedulingService.CancellationResolutionCommand("CANCELADA", "Solicitud confirmada por recepcion.")).status());
		assertEquals(1, scheduleEventCount(request.id(), "CANCELACION_APROBADA"));
		ApiException repeatedResolution = assertThrows(ApiException.class, () -> schedulingService.resolveCancellation(administrator,
				request.id(), new SchedulingService.CancellationResolutionCommand("CANCELADA", "Reintento sin efecto.")));
		assertEquals(HttpStatus.CONFLICT, repeatedResolution.status());
		assertEquals("B03_CANCELLATION_STATE", repeatedResolution.code());
		assertEquals(1, scheduleEventCount(request.id(), "CANCELACION_APROBADA"));
		assertTrue(schedulingService.checkAvailability(administrator,
				new SchedulingService.AvailabilityCommand(service.id(), veterinarian.session().accountId(), startsAt)).available());
		SessionPrincipal bosqueClient = demoClient("veterinaria-bosque", "AV_DEMO_CLIENT_BOSQUE_EMAIL", "AV_DEMO_CLIENT_BOSQUE_PASSWORD");
		ApiException crossTenantCancellation = assertThrows(ApiException.class, () -> schedulingService.cancelClientScheduleRequest(bosqueClient,
				request.id(), new SchedulingService.CancellationCommand("Intento aislado")));
		assertEquals(HttpStatus.NOT_FOUND, crossTenantCancellation.status());

		var pendingForCancellation = schedulingService.createClientScheduleRequest(client,
				new SchedulingService.ClientScheduleRequestCommand(request.petId(), service.id(), startsAt.plusSeconds(3600),
						"Solicitud pendiente a cancelar", UUID.randomUUID()));
		assertEquals("CANCELADA", schedulingService.cancelClientScheduleRequest(client, pendingForCancellation.id(),
				new SchedulingService.CancellationCommand("Ya no es necesaria")).status());

		SessionPrincipal bosqueAdministrator = administrator("veterinaria-bosque", "AV_DEMO_BOSQUE_PASSWORD");
		assertTrue(schedulingService.listScheduleRequests(bosqueAdministrator).stream().noneMatch(item -> item.id().equals(request.id())));
	}

	@Test
	void preservesTheConfirmedIntervalUntilClientAcceptsAndStaffRevalidatesAReprogramming() {
		SessionPrincipal administrator = administrator("veterinaria-aurora", "AV_DEMO_AURORA_PASSWORD");
		SessionPrincipal client = linkedClient(administrator);
		SessionPrincipal veterinarian = veterinarian(administrator);
		SessionPrincipal replacementVeterinarian = veterinarian(administrator);
		Instant startsAt = nextMondayAt(LocalTime.of(9, 0));
		var service = schedulingService.createOfferedService(administrator,
				new SchedulingService.OfferedServiceCommand("CLINICA", "B03 Reprogramacion " + UUID.randomUUID(),
						"Servicio sintetico de reprogramacion", 30, java.math.BigDecimal.valueOf(45), true));
		schedulingService.createOperatingHours(administrator,
				new SchedulingService.OperatingHoursCommand(DayOfWeek.MONDAY.getValue(), LocalTime.of(8, 0), LocalTime.of(18, 0)));
		schedulingService.createProfessionalAvailability(administrator,
				new SchedulingService.ProfessionalAvailabilityCommand(veterinarian.session().accountId(), DayOfWeek.MONDAY.getValue(),
						LocalTime.of(8, 0), LocalTime.of(18, 0)));
		schedulingService.createProfessionalAvailability(administrator,
				new SchedulingService.ProfessionalAvailabilityCommand(replacementVeterinarian.session().accountId(), DayOfWeek.MONDAY.getValue(),
						LocalTime.of(8, 0), LocalTime.of(18, 0)));
		UUID petId = ownerPetCareService.listCurrentPetsForClient(client).getFirst().id();
		var request = schedulingService.createClientScheduleRequest(client,
				new SchedulingService.ClientScheduleRequestCommand(petId, service.id(), startsAt, "Cita a reprogramar", UUID.randomUUID()));
		assertEquals("CONFIRMADA", schedulingService.confirmScheduleRequest(administrator, request.id(),
				new SchedulingService.ConfirmationCommand(veterinarian.session().accountId())).status());

		Instant firstAlternative = startsAt.plusSeconds(3600);
		var rejectedProposal = schedulingService.proposeScheduleAlternative(administrator, request.id(),
				new SchedulingService.ScheduleProposalCommand(firstAlternative, veterinarian.session().accountId(), "Cambio solicitado por agenda."));
		assertEquals("ENVIADA", rejectedProposal.status());
		assertEquals("REPROGRAMACION_PROPUESTA", schedulingService.listScheduleRequests(administrator).stream()
				.filter(item -> item.id().equals(request.id())).findFirst().orElseThrow().status());
		assertTrue(schedulingService.listClientScheduleProposals(client).stream().anyMatch(item -> item.id().equals(rejectedProposal.id())));
		assertEquals("CONFIRMADA", schedulingService.decideClientScheduleProposal(client, rejectedProposal.id(),
				new SchedulingService.ClientProposalDecisionCommand("RECHAZADA")).status());
		assertFalse(schedulingService.checkAvailability(administrator,
				new SchedulingService.AvailabilityCommand(service.id(), veterinarian.session().accountId(), startsAt)).available());
		assertTrue(schedulingService.checkAvailability(administrator,
				new SchedulingService.AvailabilityCommand(service.id(), veterinarian.session().accountId(), firstAlternative)).available());

		Instant occupiedAlternative = startsAt.plusSeconds(7200);
		var occupiedProposal = schedulingService.proposeScheduleAlternative(administrator, request.id(),
				new SchedulingService.ScheduleProposalCommand(occupiedAlternative, veterinarian.session().accountId(), "Nueva hora propuesta por agenda."));
		var competingRequest = schedulingService.createClientScheduleRequest(client,
				new SchedulingService.ClientScheduleRequestCommand(petId, service.id(), occupiedAlternative, "Cita que ocupa la alternativa", UUID.randomUUID()));
		assertEquals("CONFIRMADA", schedulingService.confirmScheduleRequest(administrator, competingRequest.id(),
				new SchedulingService.ConfirmationCommand(veterinarian.session().accountId())).status());
		ApiException occupiedDecision = assertThrows(ApiException.class, () -> schedulingService.decideClientScheduleProposal(client,
				occupiedProposal.id(), new SchedulingService.ClientProposalDecisionCommand("ACEPTADA")));
		assertEquals(HttpStatus.CONFLICT, occupiedDecision.status());
		assertEquals("B03_SLOT_UNAVAILABLE", occupiedDecision.code());
		assertEquals("REPROGRAMACION_PROPUESTA", schedulingService.listScheduleRequests(administrator).stream()
				.filter(item -> item.id().equals(request.id())).findFirst().orElseThrow().status());
		assertFalse(schedulingService.checkAvailability(administrator,
				new SchedulingService.AvailabilityCommand(service.id(), veterinarian.session().accountId(), startsAt)).available());

		Instant acceptedAlternative = startsAt.plusSeconds(10800);
		var acceptedProposal = schedulingService.proposeScheduleAlternative(administrator, request.id(),
				new SchedulingService.ScheduleProposalCommand(acceptedAlternative, veterinarian.session().accountId(), "Nueva hora aprobada por agenda."));
		SessionPrincipal bosqueClient = demoClient("veterinaria-bosque", "AV_DEMO_CLIENT_BOSQUE_EMAIL", "AV_DEMO_CLIENT_BOSQUE_PASSWORD");
		ApiException crossTenantDecision = assertThrows(ApiException.class, () -> schedulingService.decideClientScheduleProposal(bosqueClient,
				acceptedProposal.id(), new SchedulingService.ClientProposalDecisionCommand("ACEPTADA")));
		assertEquals(HttpStatus.NOT_FOUND, crossTenantDecision.status());
		assertEquals("PENDIENTE_REPROGRAMACION", schedulingService.decideClientScheduleProposal(client, acceptedProposal.id(),
				new SchedulingService.ClientProposalDecisionCommand("ACEPTADA")).status());
		ApiException repeatedDecision = assertThrows(ApiException.class, () -> schedulingService.decideClientScheduleProposal(client,
				acceptedProposal.id(), new SchedulingService.ClientProposalDecisionCommand("ACEPTADA")));
		assertEquals(HttpStatus.CONFLICT, repeatedDecision.status());
		assertFalse(schedulingService.checkAvailability(administrator,
				new SchedulingService.AvailabilityCommand(service.id(), veterinarian.session().accountId(), startsAt)).available());
		assertTrue(schedulingService.checkAvailability(administrator,
				new SchedulingService.AvailabilityCommand(service.id(), veterinarian.session().accountId(), acceptedAlternative)).available());
		assertEquals("CONFIRMADA", schedulingService.confirmScheduleRequest(administrator, request.id(),
				new SchedulingService.ConfirmationCommand(veterinarian.session().accountId())).status());
		assertTrue(schedulingService.checkAvailability(administrator,
				new SchedulingService.AvailabilityCommand(service.id(), veterinarian.session().accountId(), startsAt)).available());
		assertFalse(schedulingService.checkAvailability(administrator,
				new SchedulingService.AvailabilityCommand(service.id(), veterinarian.session().accountId(), acceptedAlternative)).available());

		var reassigned = schedulingService.reassignConfirmedScheduleProfessional(administrator, request.id(),
				new SchedulingService.ReassignmentCommand(replacementVeterinarian.session().accountId(), "Veterinario original no disponible."));
		assertEquals(replacementVeterinarian.session().accountId(), reassigned.professionalPrincipalId());
		assertEquals(1, scheduleEventCount(request.id(), "PROFESIONAL_REASIGNADO"));
		assertTrue(schedulingService.checkAvailability(administrator,
				new SchedulingService.AvailabilityCommand(service.id(), veterinarian.session().accountId(), acceptedAlternative)).available());
		assertFalse(schedulingService.checkAvailability(administrator,
				new SchedulingService.AvailabilityCommand(service.id(), replacementVeterinarian.session().accountId(), acceptedAlternative)).available());
		assertEquals(reassigned.id(), schedulingService.reassignConfirmedScheduleProfessional(administrator, request.id(),
				new SchedulingService.ReassignmentCommand(replacementVeterinarian.session().accountId(), "Reintento idempotente.")).id());
		assertEquals(1, scheduleEventCount(request.id(), "PROFESIONAL_REASIGNADO"));
	}

	@Test
	void letsOnlyTheAssignedGroomerStartAndCompleteAnOperationalTask() {
		SessionPrincipal administrator = administrator("veterinaria-aurora", "AV_DEMO_AURORA_PASSWORD");
		SessionPrincipal client = linkedClient(administrator);
		SessionPrincipal groomer = groomer(administrator);
		SessionPrincipal veterinarian = veterinarian(administrator);
		Instant startsAt = nextMondayAt(LocalTime.of(13, 0));
		var service = schedulingService.createOfferedService(administrator,
				new SchedulingService.OfferedServiceCommand("GROOMING", "B03 Grooming " + UUID.randomUUID(),
						"Servicio sintetico de grooming", 30, java.math.BigDecimal.valueOf(35), true));
		schedulingService.createOperatingHours(administrator,
				new SchedulingService.OperatingHoursCommand(DayOfWeek.MONDAY.getValue(), LocalTime.of(8, 0), LocalTime.of(18, 0)));
		schedulingService.createProfessionalAvailability(administrator,
				new SchedulingService.ProfessionalAvailabilityCommand(groomer.session().accountId(), DayOfWeek.MONDAY.getValue(),
						LocalTime.of(8, 0), LocalTime.of(18, 0)));
		UUID petId = ownerPetCareService.listCurrentPetsForClient(client).getFirst().id();
		var request = schedulingService.createClientScheduleRequest(client,
				new SchedulingService.ClientScheduleRequestCommand(petId, service.id(), startsAt, "Bano y cepillado", UUID.randomUUID()));
		var reservation = schedulingService.confirmScheduleRequest(administrator, request.id(),
				new SchedulingService.ConfirmationCommand(groomer.session().accountId()));
		assertEquals("CONFIRMADA", reservation.status());
		assertTrue(schedulingService.listGroomingTasks(groomer).stream().anyMatch(task -> task.reservationId().equals(reservation.id())
				&& "CONFIRMADA".equals(task.reservationStatus()) && task.attention() == null));
		ApiException unauthorized = assertThrows(ApiException.class, () -> schedulingService.startGroomingAttention(veterinarian, reservation.id()));
		assertEquals(HttpStatus.FORBIDDEN, unauthorized.status());

		var started = schedulingService.startGroomingAttention(groomer, reservation.id());
		assertNotNull(started.startedAt());
		assertEquals(started.id(), schedulingService.startGroomingAttention(groomer, reservation.id()).id());
		assertEquals("EN_ATENCION", schedulingService.listScheduleRequests(administrator).stream()
				.filter(item -> item.id().equals(request.id())).findFirst().orElseThrow().status());
		var completed = schedulingService.completeGroomingAttention(groomer, reservation.id(),
				new SchedulingService.GroomingCompletionCommand("Servicio realizado", "Cepillado y bano sin incidencias."));
		assertNotNull(completed.completedAt());
		assertEquals("Servicio realizado", completed.result());
		assertEquals(completed.id(), schedulingService.completeGroomingAttention(groomer, reservation.id(),
				new SchedulingService.GroomingCompletionCommand("Reintento", null)).id());
		assertEquals("COMPLETADA", schedulingService.listScheduleRequests(administrator).stream()
				.filter(item -> item.id().equals(request.id())).findFirst().orElseThrow().status());
		assertTrue(schedulingService.listGroomingTasks(groomer).stream().anyMatch(task -> task.reservationId().equals(reservation.id())
				&& "COMPLETADA".equals(task.reservationStatus()) && task.attention() != null
				&& completed.id().equals(task.attention().id())));
		assertEquals(1, scheduleEventCount(request.id(), "GROOMING_INICIADO"));
		assertEquals(1, scheduleEventCount(request.id(), "GROOMING_COMPLETADO"));
	}

	@Test
	void keepsAssignedGroomingTasksInsideTheirTenant() {
		SessionPrincipal auroraAdministrator = administrator("veterinaria-aurora", "AV_DEMO_AURORA_PASSWORD");
		SessionPrincipal auroraGroomer = groomer(auroraAdministrator);
		SessionPrincipal bosqueAdministrator = administrator("veterinaria-bosque", "AV_DEMO_BOSQUE_PASSWORD");
		SessionPrincipal bosqueClient = linkedClient(bosqueAdministrator);
		SessionPrincipal bosqueGroomer = groomer(bosqueAdministrator);
		Instant startsAt = nextMondayAt(LocalTime.of(14, 0));
		var service = schedulingService.createOfferedService(bosqueAdministrator,
				new SchedulingService.OfferedServiceCommand("GROOMING", "B03 Grooming Bosque " + UUID.randomUUID(),
						"Servicio sintetico aislado", 30, java.math.BigDecimal.valueOf(35), true));
		schedulingService.createOperatingHours(bosqueAdministrator,
				new SchedulingService.OperatingHoursCommand(DayOfWeek.MONDAY.getValue(), LocalTime.of(8, 0), LocalTime.of(18, 0)));
		schedulingService.createProfessionalAvailability(bosqueAdministrator,
				new SchedulingService.ProfessionalAvailabilityCommand(bosqueGroomer.session().accountId(), DayOfWeek.MONDAY.getValue(),
						LocalTime.of(8, 0), LocalTime.of(18, 0)));
		UUID petId = ownerPetCareService.listCurrentPetsForClient(bosqueClient).getFirst().id();
		var request = schedulingService.createClientScheduleRequest(bosqueClient,
				new SchedulingService.ClientScheduleRequestCommand(petId, service.id(), startsAt, "Fixture aislado", UUID.randomUUID()));
		var reservation = schedulingService.confirmScheduleRequest(bosqueAdministrator, request.id(),
				new SchedulingService.ConfirmationCommand(bosqueGroomer.session().accountId()));

		assertTrue(schedulingService.listGroomingTasks(auroraGroomer).stream().noneMatch(task -> task.reservationId().equals(reservation.id())));
		ApiException crossTenantStart = assertThrows(ApiException.class,
				() -> schedulingService.startGroomingAttention(auroraGroomer, reservation.id()));
		assertEquals(HttpStatus.NOT_FOUND, crossTenantStart.status());
	}

	private SessionPrincipal linkedClient(SessionPrincipal administrator) {
		String tenantSlug = administrator.session().tenantSlug();
		String email = "b03-client-" + UUID.randomUUID() + "@demo.invalid";
		String password = generatedPassword();
		var registration = administrationService.registerClient(tenantSlug, email, password);
		administrationService.verifyEmailLink(tokenFor(registration.principalId()));
		SessionPrincipal client = new SessionPrincipal(identityAccessService.authenticateClient(tenantSlug, email, password).context());
		var owner = ownerPetCareService.createOwner(administrator,
				new OwnerCommand("B03", "Cliente", null, null, "Direccion sintetica"));
		ownerPetCareService.createPet(administrator, owner.id(),
				new PetCommand("B03 Mascota", "Canino", null, "DESCONOCIDO", null, 24, "Fixture B03"));
		var requested = ownerPetCareService.requestClientOwnerLink(client);
		var pending = ownerPetCareService.listClientOwnerLinkRequests(administrator, null).stream()
				.filter(item -> item.requestReference().equals(requested.requestReference())).findFirst().orElseThrow();
		ownerPetCareService.resolveClientOwnerLinkRequest(administrator, pending.id(),
				new ClientOwnerLinkResolutionCommand("APROBADA", owner.id(), "Verificacion sintetica", null, true));
		return client;
	}

	private SessionPrincipal veterinarian(SessionPrincipal administrator) {
		String email = "b03-veterinario-" + UUID.randomUUID() + "@demo.invalid";
		String password = generatedPassword();
		StaffInvitationResult invitation = administrationService.inviteStaff(administrator, email, List.of("VETERINARIO"));
		administrationService.acceptStaffInvitation(tokenFor(invitation.principalId()), password);
		return new SessionPrincipal(identityAccessService.authenticateStaff(administrator.session().tenantSlug(), email, password).context());
	}

	private SessionPrincipal groomer(SessionPrincipal administrator) {
		String email = "b03-groomer-" + UUID.randomUUID() + "@demo.invalid";
		String password = generatedPassword();
		StaffInvitationResult invitation = administrationService.inviteStaff(administrator, email, List.of("GROOMING"));
		administrationService.acceptStaffInvitation(tokenFor(invitation.principalId()), password);
		return new SessionPrincipal(identityAccessService.authenticateStaff(administrator.session().tenantSlug(), email, password).context());
	}

	private SessionPrincipal administrator(String tenantSlug, String passwordVariable) {
		String password = System.getenv(passwordVariable);
		assertNotNull(password, "La prueba requiere credenciales sintÃ©ticas locales.");
		return new SessionPrincipal(identityAccessService.authenticateStaff(tenantSlug, "administracion@demo.invalid", password).context());
	}

	private SessionPrincipal demoClient(String tenantSlug, String emailVariable, String passwordVariable) {
		String email = System.getenv(emailVariable);
		String password = System.getenv(passwordVariable);
		assertNotNull(email, "La prueba requiere un correo CLIENT sintÃ©tico local.");
		assertNotNull(password, "La prueba requiere una contraseÃ±a CLIENT sintÃ©tica local.");
		return new SessionPrincipal(identityAccessService.authenticateClient(tenantSlug, email, password).context());
	}

	private int scheduleEventCount(UUID requestId, String actionCode) {
		Integer count = jdbcTemplate.queryForObject("""
				select count(*)
				from appointment_management.schedule_change_event
				where request_id = ? and action_code = ?
				""", Integer.class, requestId, actionCode);
		return count == null ? 0 : count;
	}

	private Instant nextMondayAt(LocalTime time) {
		LocalDate monday = LocalDate.now(LIMA).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
		return monday.atTime(time).atZone(LIMA).toInstant();
	}

	private String tokenFor(UUID resourceId) {
		String query = localMailbox.requireLatest(resourceId).link().getRawQuery();
		return query.substring("token=".length());
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
