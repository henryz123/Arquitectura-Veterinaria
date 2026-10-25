package pe.arquitecturaveterinaria.appointmentmanagement.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import pe.arquitecturaveterinaria.appointmentmanagement.infrastructure.SchedulingJdbcRepository;
import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.application.TenantSession;
import pe.arquitecturaveterinaria.identityaccess.domain.AccountType;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.TenantContextJdbc;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.ClientPetProfile;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.OwnerProfile;
import pe.arquitecturaveterinaria.shared.api.ApiException;

class SchedulingServiceTest {

	private final SchedulingJdbcRepository repository = mock(SchedulingJdbcRepository.class);
	private final TenantContextJdbc tenantContext = mock(TenantContextJdbc.class);
	private final OwnerPetCareService ownerPetCareService = mock(OwnerPetCareService.class);
	private final SchedulingService service = new SchedulingService(repository, tenantContext, ownerPetCareService);

	@Test
	void createsTenantLocalServiceOnlyForAnAuthorizedSchedulingManager() {
		SessionPrincipal actor = manager();
		OfferedService expected = offeredService(actor.session().tenantId());
		when(repository.createOfferedService(eq(actor.session().tenantId()), eq(actor.session().accountId()), any()))
				.thenReturn(expected);

		OfferedService actual = service.createOfferedService(actor,
				new SchedulingService.OfferedServiceCommand("CLINICA", "Consulta general", "Revision basica", 30,
						new BigDecimal("45.00"), true));

		assertEquals(expected, actual);
		verify(tenantContext).setTenantId(actor.session().tenantId());
		verify(tenantContext).setPrincipalId(actor.session().accountId());
		verify(repository).createOfferedService(eq(actor.session().tenantId()), eq(actor.session().accountId()),
				any(OfferedServiceDraft.class));
	}

	@Test
	void rejectsAnInvalidServiceDurationBeforeItReachesPostgreSql() {
		ApiException exception = assertThrows(ApiException.class, () -> service.createOfferedService(manager(),
				new SchedulingService.OfferedServiceCommand("CLINICA", "Consulta", null, 20, BigDecimal.ZERO, false)));

		assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.status());
		assertEquals("B03_DURATIONMINUTES", exception.code());
		verifyNoInteractions(repository);
	}

	@Test
	void revalidatesTheProfessionalRoleBeforePersistingAvailability() {
		SessionPrincipal actor = manager();
		UUID professionalId = UUID.randomUUID();
		when(repository.listAssignableProfessionals(actor.session().sessionTokenHash()))
				.thenReturn(List.of(new AssignableProfessional(professionalId, List.of("VETERINARIO"))));
		ProfessionalAvailability expected = new ProfessionalAvailability(UUID.randomUUID(), professionalId, 1,
				LocalTime.of(8, 0), LocalTime.of(12, 0), Instant.now(), Instant.now());
		when(repository.createProfessionalAvailability(eq(actor.session().tenantId()), eq(actor.session().accountId()),
				eq(professionalId), eq(1), eq(LocalTime.of(8, 0)), eq(LocalTime.of(12, 0)))).thenReturn(expected);

		ProfessionalAvailability actual = service.createProfessionalAvailability(actor,
				new SchedulingService.ProfessionalAvailabilityCommand(professionalId, 1, LocalTime.of(8, 0), LocalTime.of(12, 0)));

		assertEquals(expected, actual);
		verify(repository).listAssignableProfessionals(actor.session().sessionTokenHash());
		verify(repository).createProfessionalAvailability(eq(actor.session().tenantId()), eq(actor.session().accountId()),
				eq(professionalId), eq(1), eq(LocalTime.of(8, 0)), eq(LocalTime.of(12, 0)));
	}

	@Test
	void createsOneClientRequestForItsLinkedActivePetAndReturnsTheSameRequestOnRetry() {
		SessionPrincipal actor = client();
		UUID petId = UUID.randomUUID();
		UUID serviceId = UUID.randomUUID();
		UUID operationId = UUID.randomUUID();
		Instant requestedStartsAt = Instant.now().plus(Duration.ofDays(2));
		OwnerProfile owner = owner(actor.session().tenantId());
		ClientPetProfile pet = new ClientPetProfile(petId, "Luna", "Canino", null, "HEMBRA", LocalDate.of(2022, 1, 1),
				null, null, "ACTIVA");
		ScheduleRequest expected = request(actor.session().tenantId(), actor.session().accountId(), owner.id(), petId, serviceId,
				requestedStartsAt, operationId, "PENDIENTE");
		when(ownerPetCareService.getClientOwnerProfile(actor)).thenReturn(owner);
		when(ownerPetCareService.getClientPet(actor, petId)).thenReturn(pet);
		when(repository.findOfferedService(serviceId)).thenReturn(Optional.of(offeredService(actor.session().tenantId(), serviceId)));
		when(repository.createScheduleRequest(eq(actor.session().tenantId()), eq(actor.session().accountId()), eq(owner.id()), eq(petId),
				eq(serviceId), eq(requestedStartsAt), eq(30), eq("Consulta general"), eq(operationId))).thenReturn(Optional.of(expected));

		ScheduleRequest actual = service.createClientScheduleRequest(actor,
				new SchedulingService.ClientScheduleRequestCommand(petId, serviceId, requestedStartsAt, "Consulta general", operationId));

		assertEquals(expected, actual);
		verify(repository).recordScheduleChange(eq(actor.session().tenantId()), eq(expected.id()), eq(actor.session().accountId()),
				eq("SOLICITUD_CREADA"), eq(null), eq(requestedStartsAt), eq(null), eq(null), eq(null));
	}

	@Test
	void confirmsExactlyOneReservationAfterRevalidatingTheProfessionalSlot() {
		SessionPrincipal actor = manager();
		UUID professionalId = UUID.randomUUID();
		UUID requestId = UUID.randomUUID();
		Instant startsAt = Instant.now().plus(Duration.ofDays(2));
		ScheduleRequest pending = request(actor.session().tenantId(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), startsAt, UUID.randomUUID(), "PENDIENTE");
		ScheduleReservation expected = reservation(actor.session().tenantId(), pending.id(), professionalId, startsAt);
		when(repository.lockScheduleRequest(requestId)).thenReturn(Optional.of(pending));
		when(repository.findOfferedService(pending.serviceId())).thenReturn(Optional.of(offeredService(actor.session().tenantId(), pending.serviceId())));
		when(repository.listAssignableProfessionals(actor.session().sessionTokenHash()))
				.thenReturn(List.of(new AssignableProfessional(professionalId, List.of("VETERINARIO"))));
		when(repository.isWithinOperatingHours(any(Integer.class), any(LocalTime.class), any(LocalTime.class))).thenReturn(true);
		when(repository.isWithinProfessionalAvailability(eq(professionalId), any(Integer.class), any(LocalTime.class), any(LocalTime.class)))
				.thenReturn(true);
		when(repository.hasScheduleBlock(eq(professionalId), eq(startsAt), eq(startsAt.plusSeconds(1800)))).thenReturn(false);
		when(repository.hasCurrentReservationConflict(eq(professionalId), eq(startsAt), eq(startsAt.plusSeconds(1800)))).thenReturn(false);
		when(repository.findCurrentReservation(pending.id())).thenReturn(Optional.empty());
		when(repository.createConfirmedReservation(eq(actor.session().tenantId()), eq(pending.id()), eq(professionalId), eq(startsAt),
				eq(startsAt.plusSeconds(1800)), eq(actor.session().accountId()))).thenReturn(expected);
		when(repository.markScheduleRequestConfirmed(actor.session().tenantId(), pending.id())).thenReturn(1);

		ScheduleReservation actual = service.confirmScheduleRequest(actor, requestId,
				new SchedulingService.ConfirmationCommand(professionalId));

		assertEquals(expected, actual);
		verify(repository).lockProfessionalSchedule(professionalId);
		verify(repository).recordScheduleChange(eq(actor.session().tenantId()), eq(pending.id()), eq(actor.session().accountId()),
				eq("CITA_CONFIRMADA"), eq(null), eq(startsAt), eq(null), eq(professionalId), eq(null));
	}

	@Test
	void rejectsAConflictingSlotWithoutCreatingAReservation() {
		SessionPrincipal actor = manager();
		UUID professionalId = UUID.randomUUID();
		Instant startsAt = Instant.now().plus(Duration.ofDays(2));
		ScheduleRequest pending = request(actor.session().tenantId(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), startsAt, UUID.randomUUID(), "PENDIENTE");
		when(repository.lockScheduleRequest(pending.id())).thenReturn(Optional.of(pending));
		when(repository.findOfferedService(pending.serviceId())).thenReturn(Optional.of(offeredService(actor.session().tenantId(), pending.serviceId())));
		when(repository.listAssignableProfessionals(actor.session().sessionTokenHash()))
				.thenReturn(List.of(new AssignableProfessional(professionalId, List.of("VETERINARIO"))));
		when(repository.isWithinOperatingHours(any(Integer.class), any(LocalTime.class), any(LocalTime.class))).thenReturn(true);
		when(repository.isWithinProfessionalAvailability(eq(professionalId), any(Integer.class), any(LocalTime.class), any(LocalTime.class)))
				.thenReturn(true);
		when(repository.hasCurrentReservationConflict(eq(professionalId), eq(startsAt), eq(startsAt.plusSeconds(1800)))).thenReturn(true);

		ApiException exception = assertThrows(ApiException.class, () -> service.confirmScheduleRequest(actor, pending.id(),
				new SchedulingService.ConfirmationCommand(professionalId)));

		assertEquals(HttpStatus.CONFLICT, exception.status());
		assertEquals("B03_SLOT_UNAVAILABLE", exception.code());
		verifyNoInteractions(ownerPetCareService);
	}

	@Test
	void returnsTheExistingReservationWhenConfirmationIsRetried() {
		SessionPrincipal actor = manager();
		ScheduleRequest confirmed = request(actor.session().tenantId(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), Instant.now().plus(Duration.ofDays(2)), UUID.randomUUID(), "CONFIRMADA");
		ScheduleReservation expected = reservation(actor.session().tenantId(), confirmed.id(), UUID.randomUUID(),
				confirmed.requestedStartsAt());
		when(repository.lockScheduleRequest(confirmed.id())).thenReturn(Optional.of(confirmed));
		when(repository.findCurrentReservation(confirmed.id())).thenReturn(Optional.of(expected));

		assertEquals(expected, service.confirmScheduleRequest(actor, confirmed.id(),
				new SchedulingService.ConfirmationCommand(expected.professionalPrincipalId())));
		verify(repository).findCurrentReservation(confirmed.id());
	}

	private SessionPrincipal manager() {
		return new SessionPrincipal(new TenantSession(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "aurora",
				"Aurora", "ACTIVO", AccountType.STAFF, List.of("ADMIN_VETERINARIA"), "session-hash",
				Instant.now().plusSeconds(3600)));
	}

	private SessionPrincipal client() {
		return new SessionPrincipal(new TenantSession(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "aurora",
				"Aurora", "ACTIVO", AccountType.CLIENT, List.of(), "client-session-hash", Instant.now().plusSeconds(3600)));
	}

	private OfferedService offeredService(UUID tenantId) {
		return offeredService(tenantId, UUID.randomUUID());
	}

	private OfferedService offeredService(UUID tenantId, UUID serviceId) {
		return new OfferedService(serviceId, tenantId, "CLINICA", "Consulta general", "Revision basica", 30,
				new BigDecimal("45.00"), true, "ACTIVO", Instant.now(), Instant.now());
	}

	private OwnerProfile owner(UUID tenantId) {
		return new OwnerProfile(UUID.randomUUID(), tenantId, "Cliente", "Sintetico", null, null, null, "ACTIVO", "PRESENCIAL",
				Instant.now(), Instant.now());
	}

	private ScheduleRequest request(UUID tenantId, UUID requesterPrincipalId, UUID ownerId, UUID petId, UUID serviceId,
			Instant startsAt, UUID idempotencyKey, String status) {
		return new ScheduleRequest(UUID.randomUUID(), tenantId, requesterPrincipalId, ownerId, petId, serviceId, startsAt, 30,
				status, "Consulta general", idempotencyKey, Instant.now(), Instant.now());
	}

	private ScheduleReservation reservation(UUID tenantId, UUID requestId, UUID professionalId, Instant startsAt) {
		return new ScheduleReservation(UUID.randomUUID(), tenantId, requestId, professionalId, startsAt, startsAt.plusSeconds(1800),
				"CONFIRMADA", true, 1, UUID.randomUUID(), Instant.now(), Instant.now());
	}
}
