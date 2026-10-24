package pe.arquitecturaveterinaria.ownerpetcare.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import java.time.LocalDate;
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
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.ClinicalEpisodeCommand;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.OwnerCommand;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.PetCommand;
import pe.arquitecturaveterinaria.shared.api.ApiException;

@SpringBootTest
@Transactional(transactionManager = "jdbcTransactionManager")
class OwnerPetCareDatabaseIntegrationTest {

	private static final SecureRandom RANDOM = new SecureRandom();

	@Autowired
	private OwnerPetCareService ownerPetCareService;

	@Autowired
	private B01AdministrationService administrationService;

	@Autowired
	private IdentityAccessService identityAccessService;

	@Autowired
	private LocalNotificationMailbox localMailbox;

	@Test
	void persistsTheFirstB02VerticalAndHidesItFromAnotherTenant() {
		SessionPrincipal auroraAdministrator = administrator("veterinaria-aurora", "AV_DEMO_AURORA_PASSWORD");
		var owner = ownerPetCareService.createOwner(auroraAdministrator,
				new OwnerCommand("Marta", "Prueba", "999111222", null, "Calle sintética 101"));
		assertTrue(ownerPetCareService.listOwners(auroraAdministrator, null)
				.stream().map(item -> item.id()).toList().contains(owner.id()));
		var pet = ownerPetCareService.createPet(auroraAdministrator, owner.id(),
				new PetCommand("Nube", "Canino", null, "HEMBRA", LocalDate.of(2021, 6, 15), null, "Collar rojo"));
		assertEquals(List.of(pet.id()), ownerPetCareService.listPetsByOwner(auroraAdministrator, owner.id())
				.stream().map(item -> item.id()).toList());

		SessionPrincipal veterinarian = veterinarian(auroraAdministrator);
		var draft = ownerPetCareService.createClinicalEpisode(veterinarian, pet.id(),
				new ClinicalEpisodeCommand(null, "Control anual", "Sin cambios relevantes", "Paciente estable",
						"Sin hallazgos de alarma", "Control preventivo", null));
		var finalized = ownerPetCareService.finalizeClinicalEpisode(veterinarian, draft.id(),
				new ClinicalEpisodeCommand(null, "Control anual", "Sin cambios relevantes", "Paciente estable",
						"Sin hallazgos de alarma", "Control preventivo", "Continuar observación."));

		assertEquals("FINALIZADO", finalized.status());
		assertNotNull(finalized.finalizedAt());
		assertEquals(List.of(finalized.id()),
				ownerPetCareService.listClinicalHistory(veterinarian, pet.id()).stream().map(item -> item.id()).toList());

		SessionPrincipal bosqueAdministrator = administrator("veterinaria-bosque", "AV_DEMO_BOSQUE_PASSWORD");
		ApiException exception = assertThrows(ApiException.class, () -> ownerPetCareService.getPet(bosqueAdministrator, pet.id()));
		assertEquals(HttpStatus.NOT_FOUND, exception.status());
		assertEquals("B02_RESOURCE_NOT_FOUND", exception.code());
		ApiException ownerException = assertThrows(ApiException.class,
				() -> ownerPetCareService.listPetsByOwner(bosqueAdministrator, owner.id()));
		assertEquals(HttpStatus.NOT_FOUND, ownerException.status());
	}

	@Test
	void administratorWithoutVeterinarianRoleCannotCreateClinicalEpisode() {
		SessionPrincipal auroraAdministrator = administrator("veterinaria-aurora", "AV_DEMO_AURORA_PASSWORD");
		var owner = ownerPetCareService.createOwner(auroraAdministrator,
				new OwnerCommand("Luis", "Permisos", null, null, null));
		var pet = ownerPetCareService.createPet(auroraAdministrator, owner.id(),
				new PetCommand("Sol", "Felino", null, "DESCONOCIDO", null, 18, null));

		ApiException exception = assertThrows(ApiException.class, () -> ownerPetCareService.createClinicalEpisode(
				auroraAdministrator, pet.id(), new ClinicalEpisodeCommand(null, "Motivo", null, "Evaluación", null, null, null)));

		assertEquals(HttpStatus.FORBIDDEN, exception.status());
		assertEquals("B02_CLINICAL_ROLE_REQUIRED", exception.code());
	}

	private SessionPrincipal veterinarian(SessionPrincipal administrator) {
		String email = "veterinario-" + UUID.randomUUID() + "@demo.invalid";
		String password = generatedPassword();
		StaffInvitationResult invitation = administrationService.inviteStaff(administrator, email, List.of("VETERINARIO"));
		administrationService.acceptStaffInvitation(tokenFor(invitation.principalId()), password);
		return new SessionPrincipal(identityAccessService.authenticateStaff("veterinaria-aurora", email, password).context());
	}

	private SessionPrincipal administrator(String tenantSlug, String passwordVariable) {
		String password = System.getenv(passwordVariable);
		assertNotNull(password, "La prueba requiere credenciales sintéticas locales.");
		return new SessionPrincipal(identityAccessService.authenticateStaff(tenantSlug, "administracion@demo.invalid", password).context());
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
