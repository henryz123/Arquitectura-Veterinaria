package pe.arquitecturaveterinaria.appointmentmanagement.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

		SessionPrincipal bosqueAdministrator = administrator("veterinaria-bosque", "AV_DEMO_BOSQUE_PASSWORD");
		assertTrue(schedulingService.listScheduleRequests(bosqueAdministrator).stream().noneMatch(item -> item.id().equals(request.id())));
	}

	private SessionPrincipal linkedClient(SessionPrincipal administrator) {
		String tenantSlug = "veterinaria-aurora";
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
		return new SessionPrincipal(identityAccessService.authenticateStaff("veterinaria-aurora", email, password).context());
	}

	private SessionPrincipal administrator(String tenantSlug, String passwordVariable) {
		String password = System.getenv(passwordVariable);
		assertNotNull(password, "La prueba requiere credenciales sintÃ©ticas locales.");
		return new SessionPrincipal(identityAccessService.authenticateStaff(tenantSlug, "administracion@demo.invalid", password).context());
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
