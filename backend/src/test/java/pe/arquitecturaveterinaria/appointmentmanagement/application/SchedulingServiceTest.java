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
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import pe.arquitecturaveterinaria.appointmentmanagement.infrastructure.SchedulingJdbcRepository;
import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.identityaccess.application.TenantSession;
import pe.arquitecturaveterinaria.identityaccess.domain.AccountType;
import pe.arquitecturaveterinaria.identityaccess.infrastructure.TenantContextJdbc;
import pe.arquitecturaveterinaria.shared.api.ApiException;

class SchedulingServiceTest {

	private final SchedulingJdbcRepository repository = mock(SchedulingJdbcRepository.class);
	private final TenantContextJdbc tenantContext = mock(TenantContextJdbc.class);
	private final SchedulingService service = new SchedulingService(repository, tenantContext);

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

	private SessionPrincipal manager() {
		return new SessionPrincipal(new TenantSession(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "aurora",
				"Aurora", "ACTIVO", AccountType.STAFF, List.of("ADMIN_VETERINARIA"), "session-hash",
				Instant.now().plusSeconds(3600)));
	}

	private OfferedService offeredService(UUID tenantId) {
		return new OfferedService(UUID.randomUUID(), tenantId, "CLINICA", "Consulta general", "Revision basica", 30,
				new BigDecimal("45.00"), true, "ACTIVO", Instant.now(), Instant.now());
	}
}
