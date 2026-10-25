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
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.AmendmentCommand;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.ClientOwnerLinkResolutionCommand;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.DuplicateResolutionCommand;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.DuplicateReviewCommand;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.OwnerCommand;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.OwnershipTransferCommand;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.PetCommand;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.PrescriptionCommand;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.VaccinationCommand;
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
		var updatedOwner = ownerPetCareService.updateOwner(auroraAdministrator, owner.id(),
				new OwnerCommand("Marta", "Actualizada", "999111223", "marta.actualizada@demo.invalid",
						"Calle sintética 102"));
		assertEquals("Actualizada", updatedOwner.familyNames());
		assertEquals("999111223", updatedOwner.phone());
		assertEquals("PRESENCIAL", updatedOwner.origin());
		var pet = ownerPetCareService.createPet(auroraAdministrator, owner.id(),
				new PetCommand("Nube", "Canino", null, "HEMBRA", LocalDate.of(2021, 6, 15), null, "Collar rojo"));
		assertEquals(List.of(pet.id()), ownerPetCareService.listPetsByOwner(auroraAdministrator, owner.id())
				.stream().map(item -> item.id()).toList());
		var successor = ownerPetCareService.createOwner(auroraAdministrator,
				new OwnerCommand("SofÃ­a", "Sucesora", null, null, null));
		var transferred = ownerPetCareService.transferPetOwnership(auroraAdministrator, pet.id(),
				new OwnershipTransferCommand(owner.id(), successor.id(), "Cambio de responsable verificado", true));
		assertEquals(successor.id(), transferred.currentOwnerId());
		var periods = ownerPetCareService.listOwnershipPeriods(auroraAdministrator, pet.id());
		assertEquals(2, periods.size());
		assertEquals(successor.id(), periods.getFirst().ownerId());
		assertNotNull(periods.get(1).effectiveUntil());
		ApiException unverifiedTransfer = assertThrows(ApiException.class,
				() -> ownerPetCareService.transferPetOwnership(auroraAdministrator, pet.id(),
						new OwnershipTransferCommand(successor.id(), owner.id(), "manual verification required", false)));
		assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, unverifiedTransfer.status());
		assertEquals("VALIDATION_FAILED", unverifiedTransfer.code());

		SessionPrincipal bosqueAdministrator = administrator("veterinaria-bosque", "AV_DEMO_BOSQUE_PASSWORD");
		var bosqueOwner = ownerPetCareService.createOwner(bosqueAdministrator,
				new OwnerCommand("Rosa", "Bosque", null, null, null));
		ApiException crossTenantOwnerUpdate = assertThrows(ApiException.class,
				() -> ownerPetCareService.updateOwner(bosqueAdministrator, owner.id(),
						new OwnerCommand("Marta", "No visible", null, null, null)));
		assertEquals(HttpStatus.NOT_FOUND, crossTenantOwnerUpdate.status());
		assertEquals("B02_RESOURCE_NOT_FOUND", crossTenantOwnerUpdate.code());
		ApiException crossTenantTransfer = assertThrows(ApiException.class,
				() -> ownerPetCareService.transferPetOwnership(auroraAdministrator, pet.id(),
						new OwnershipTransferCommand(successor.id(), bosqueOwner.id(), "cross tenant", true)));
		assertEquals(HttpStatus.NOT_FOUND, crossTenantTransfer.status());
		assertEquals("B02_RESOURCE_NOT_FOUND", crossTenantTransfer.code());
		ApiException staleTransfer = assertThrows(ApiException.class,
				() -> ownerPetCareService.transferPetOwnership(auroraAdministrator, pet.id(),
						new OwnershipTransferCommand(owner.id(), successor.id(), "Reintento obsoleto", true)));
		assertEquals("B02_OWNER_TRANSFER_STALE", staleTransfer.code());

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

	@Test
	void linksAClientOnlyAfterManualReviewAndShowsOnlyCurrentBasicPets() {
		SessionPrincipal auroraAdministrator = administrator("veterinaria-aurora", "AV_DEMO_AURORA_PASSWORD");
		SessionPrincipal client = client("veterinaria-aurora");
		var owner = ownerPetCareService.createOwner(auroraAdministrator,
				new OwnerCommand("Elena", "Vinculo", null, null, null));
		var pet = ownerPetCareService.createPet(auroraAdministrator, owner.id(),
				new PetCommand("Brisa", "Canino", null, "HEMBRA", null, 24, null));

		var requested = ownerPetCareService.requestClientOwnerLink(client);
		assertEquals("PENDIENTE", requested.status());
		assertNotNull(requested.requestReference());
		assertTrue(ownerPetCareService.listCurrentPetsForClient(client).isEmpty());
		var pending = ownerPetCareService.listClientOwnerLinkRequests(auroraAdministrator, null).stream()
				.filter(item -> item.requestReference().equals(requested.requestReference())).findFirst().orElseThrow();

		SessionPrincipal bosqueAdministrator = administrator("veterinaria-bosque", "AV_DEMO_BOSQUE_PASSWORD");
		ApiException crossTenantResolution = assertThrows(ApiException.class,
				() -> ownerPetCareService.resolveClientOwnerLinkRequest(bosqueAdministrator, pending.id(),
						new ClientOwnerLinkResolutionCommand("APROBADA", owner.id(), "Verificacion presencial", null, true)));
		assertEquals(HttpStatus.NOT_FOUND, crossTenantResolution.status());

		ApiException unverifiedResolution = assertThrows(ApiException.class,
				() -> ownerPetCareService.resolveClientOwnerLinkRequest(auroraAdministrator, pending.id(),
						new ClientOwnerLinkResolutionCommand("APROBADA", owner.id(), "Verificacion presencial", null, false)));
		assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, unverifiedResolution.status());

		var approved = ownerPetCareService.resolveClientOwnerLinkRequest(auroraAdministrator, pending.id(),
				new ClientOwnerLinkResolutionCommand("APROBADA", owner.id(), "Verificacion presencial", null, true));
		assertEquals("APROBADA", approved.status());
		assertEquals(List.of(pet.id()), ownerPetCareService.listCurrentPetsForClient(client).stream().map(item -> item.id()).toList());
		assertEquals("APROBADA", ownerPetCareService.getClientOwnerLinkStatus(client).status());
		assertEquals("B02_CLIENT_ALREADY_LINKED", assertThrows(ApiException.class,
				() -> ownerPetCareService.requestClientOwnerLink(client)).code());

		var successor = ownerPetCareService.createOwner(auroraAdministrator,
				new OwnerCommand("Nora", "Nueva", null, null, null));
		ownerPetCareService.transferPetOwnership(auroraAdministrator, pet.id(),
				new OwnershipTransferCommand(owner.id(), successor.id(), "Cambio de responsable", true));
		assertTrue(ownerPetCareService.listCurrentPetsForClient(client).isEmpty());
	}

	@Test
	void letsAnApprovedClientCreateAndEditOnlyItsOwnBasicProfiles() {
		SessionPrincipal auroraAdministrator = administrator("veterinaria-aurora", "AV_DEMO_AURORA_PASSWORD");
		SessionPrincipal client = client("veterinaria-aurora");
		var owner = ownerPetCareService.createOwner(auroraAdministrator,
				new OwnerCommand("Claudia", "Propietaria", "999100200", "claudia@demo.invalid", "Direccion inicial"));
		var originalPet = ownerPetCareService.createPet(auroraAdministrator, owner.id(),
				new PetCommand("Trigo", "Canino", null, "MACHO", null, 30, null));
		var requested = ownerPetCareService.requestClientOwnerLink(client);
		var pending = ownerPetCareService.listClientOwnerLinkRequests(auroraAdministrator, null).stream()
				.filter(item -> item.requestReference().equals(requested.requestReference())).findFirst().orElseThrow();
		ownerPetCareService.resolveClientOwnerLinkRequest(auroraAdministrator, pending.id(),
				new ClientOwnerLinkResolutionCommand("APROBADA", owner.id(), "Verificacion presencial", null, true));

		assertEquals(owner.id(), ownerPetCareService.getClientOwnerProfile(client).id());
		var updatedOwner = ownerPetCareService.updateClientOwnerProfile(client,
				new OwnerCommand("Claudia", "Propietaria", "999300400", "contacto@demo.invalid", "Direccion actualizada"));
		assertEquals("999300400", updatedOwner.phone());
		assertEquals("contacto@demo.invalid", updatedOwner.email());

		var createdPet = ownerPetCareService.createClientPet(client,
				new PetCommand("Mora", "Felino", "Comun", "HEMBRA", LocalDate.of(2023, 4, 1), null,
						"Collar verde"));
		assertTrue(ownerPetCareService.listCurrentPetsForClient(client).stream().map(item -> item.id()).toList()
				.containsAll(List.of(originalPet.id(), createdPet.id())));
		var updatedPet = ownerPetCareService.updateClientPet(client, createdPet.id(),
				new PetCommand("Mora", "Felino", "Comun europeo", "HEMBRA", null, 42, "Collar azul"));
		assertEquals("Comun europeo", updatedPet.breed());
		assertEquals(Integer.valueOf(42), updatedPet.estimatedAgeMonths());

		ApiException clinicalDenied = assertThrows(ApiException.class,
				() -> ownerPetCareService.listClinicalHistory(client, createdPet.id()));
		assertEquals("B02_STAFF_REQUIRED", clinicalDenied.code());

		SessionPrincipal bosqueClient = client("veterinaria-bosque");
		ApiException crossTenant = assertThrows(ApiException.class,
				() -> ownerPetCareService.getClientPet(bosqueClient, createdPet.id()));
		assertEquals(HttpStatus.NOT_FOUND, crossTenant.status());

		var successor = ownerPetCareService.createOwner(auroraAdministrator,
				new OwnerCommand("Raul", "Sucesor", null, null, null));
		ownerPetCareService.transferPetOwnership(auroraAdministrator, createdPet.id(),
				new OwnershipTransferCommand(owner.id(), successor.id(), "Cambio verificado", true));
		ApiException formerOwnerDenied = assertThrows(ApiException.class,
				() -> ownerPetCareService.updateClientPet(client, createdPet.id(),
						new PetCommand("Mora", "Felino", null, "HEMBRA", null, 42, null)));
		assertEquals(HttpStatus.NOT_FOUND, formerOwnerDenied.status());
	}

	@Test
	void rejectsAnIncompatibleSecondClientLinkForTheSameOwner() {
		SessionPrincipal auroraAdministrator = administrator("veterinaria-aurora", "AV_DEMO_AURORA_PASSWORD");
		var owner = ownerPetCareService.createOwner(auroraAdministrator,
				new OwnerCommand("Carla", "Exclusiva", null, null, null));
		SessionPrincipal firstClient = client("veterinaria-aurora");
		var firstRequest = ownerPetCareService.requestClientOwnerLink(firstClient);
		var firstPending = ownerPetCareService.listClientOwnerLinkRequests(auroraAdministrator, null).stream()
				.filter(item -> item.requestReference().equals(firstRequest.requestReference())).findFirst().orElseThrow();
		ownerPetCareService.resolveClientOwnerLinkRequest(auroraAdministrator, firstPending.id(),
				new ClientOwnerLinkResolutionCommand("APROBADA", owner.id(), "Verificacion presencial", null, true));

		SessionPrincipal secondClient = client("veterinaria-aurora");
		var secondRequest = ownerPetCareService.requestClientOwnerLink(secondClient);
		var secondPending = ownerPetCareService.listClientOwnerLinkRequests(auroraAdministrator, null).stream()
				.filter(item -> item.requestReference().equals(secondRequest.requestReference())).findFirst().orElseThrow();

		ApiException conflict = assertThrows(ApiException.class,
				() -> ownerPetCareService.resolveClientOwnerLinkRequest(auroraAdministrator, secondPending.id(),
						new ClientOwnerLinkResolutionCommand("APROBADA", owner.id(), "Verificacion presencial", null, true)));
		assertEquals("B02_CLIENT_LINK_CONFLICT", conflict.code());
	}

	@Test
	void recordsClinicalFollowUpWithoutDuplicatingVaccinesOrOverwritingFinalizedEpisodes() {
		SessionPrincipal auroraAdministrator = administrator("veterinaria-aurora", "AV_DEMO_AURORA_PASSWORD");
		var owner = ownerPetCareService.createOwner(auroraAdministrator,
				new OwnerCommand("Vera", "Clinica", null, null, null));
		var pet = ownerPetCareService.createPet(auroraAdministrator, owner.id(),
				new PetCommand("Miel", "Canino", null, "HEMBRA", null, 36, null));
		SessionPrincipal veterinarian = veterinarian(auroraAdministrator);
		var draft = ownerPetCareService.createClinicalEpisode(veterinarian, pet.id(),
				new ClinicalEpisodeCommand(null, "Control de vacunacion", null, "Apta para vacuna", null, null, null));
		var finalized = ownerPetCareService.finalizeClinicalEpisode(veterinarian, draft.id(),
				new ClinicalEpisodeCommand(null, "Control de vacunacion", "Sin reacciones previas", "Apta para vacuna",
						"Sin contraindicaciones", "Prevencion anual", null));

		UUID operationId = UUID.randomUUID();
		var vaccine = ownerPetCareService.recordVaccination(veterinarian, pet.id(),
				new VaccinationCommand(finalized.id(), operationId, "Vacuna sintetica", LocalDate.of(2026, 10, 24),
						"1 ml", "Lote-Demo-01", LocalDate.of(2027, 10, 24)));
		var retry = ownerPetCareService.recordVaccination(veterinarian, pet.id(),
				new VaccinationCommand(finalized.id(), operationId, "Vacuna sintetica", LocalDate.of(2026, 10, 24),
						"1 ml", "Lote-Demo-01", LocalDate.of(2027, 10, 24)));
		assertEquals(vaccine.id(), retry.id());
		assertEquals(1, ownerPetCareService.listVaccinations(veterinarian, pet.id()).size());
		ApiException conflictingRetry = assertThrows(ApiException.class, () -> ownerPetCareService.recordVaccination(veterinarian,
				pet.id(), new VaccinationCommand(finalized.id(), operationId, "Otra vacuna", LocalDate.of(2026, 10, 24),
						"1 ml", "Lote-Demo-01", LocalDate.of(2027, 10, 24))));
		assertEquals("B02_VACCINATION_OPERATION_CONFLICT", conflictingRetry.code());

		var prescription = ownerPetCareService.recordClinicalPrescription(veterinarian, finalized.id(),
				new PrescriptionCommand("Medicamento sintetico", "Tableta", "Una unidad", "Oral", "Cada 24 horas",
						"Tres dias", "Administrar con alimento.", LocalDate.of(2026, 10, 24)));
		assertEquals(List.of(prescription.id()), ownerPetCareService.listClinicalPrescriptions(veterinarian, finalized.id())
				.stream().map(item -> item.id()).toList());

		var amendment = ownerPetCareService.amendClinicalEpisode(veterinarian, finalized.id(),
				new AmendmentCommand("Precisa indicacion", "Añadir control preventivo al plan."));
		assertTrue(amendment.previousContent().contains("Control de vacunacion"));
		assertEquals("FINALIZADO", ownerPetCareService.listClinicalHistory(veterinarian, pet.id()).getFirst().status());
		assertEquals(List.of(amendment.id()), ownerPetCareService.listClinicalEpisodeAmendments(veterinarian, finalized.id())
				.stream().map(item -> item.id()).toList());

		ApiException administrationDenied = assertThrows(ApiException.class, () -> ownerPetCareService.recordVaccination(
				auroraAdministrator, pet.id(), new VaccinationCommand(null, UUID.randomUUID(), "No autorizada",
						LocalDate.of(2026, 10, 24), null, null, null)));
		assertEquals("B02_CLINICAL_ROLE_REQUIRED", administrationDenied.code());

		SessionPrincipal bosqueVeterinarian = veterinarian(administrator("veterinaria-bosque", "AV_DEMO_BOSQUE_PASSWORD"),
				"veterinaria-bosque");
		ApiException crossTenant = assertThrows(ApiException.class,
				() -> ownerPetCareService.listVaccinations(bosqueVeterinarian, pet.id()));
		assertEquals(HttpStatus.NOT_FOUND, crossTenant.status());
	}

	@Test
	void resolvesOwnerDuplicateReviewsWithoutMergingOrLeavingPartialInactivation() {
		SessionPrincipal administrator = administrator("veterinaria-aurora", "AV_DEMO_AURORA_PASSWORD");
		var duplicate = ownerPetCareService.createOwner(administrator,
				new OwnerCommand("Rosa", "Coincidente", "999555100", null, null));
		var reference = ownerPetCareService.createOwner(administrator,
				new OwnerCommand("Rosa", "Coincidente", "999555200", null, null));

		assertTrue(ownerPetCareService.findPotentialOwnerDuplicates(administrator, duplicate.id()).stream()
				.map(item -> item.id()).toList().contains(reference.id()));
		var review = ownerPetCareService.reportOwnerDuplicate(administrator,
				new DuplicateReviewCommand(duplicate.id(), reference.id()));
		ownerPetCareService.resolveOwnerDuplicate(administrator, review.id(),
				new DuplicateResolutionCommand("DESCARTADA", "La revisión manual confirmó que son personas distintas."));
		assertEquals("DESCARTADA", ownerPetCareService.listOwnerDuplicateReviews(administrator).stream()
				.filter(item -> item.id().equals(review.id())).findFirst().orElseThrow().outcome());
		assertEquals("ACTIVO", ownerPetCareService.getOwner(administrator, duplicate.id()).status());
		assertEquals("B02_DUPLICATE_REVIEW_ALREADY_EXISTS", assertThrows(ApiException.class,
				() -> ownerPetCareService.reportOwnerDuplicate(administrator,
						new DuplicateReviewCommand(reference.id(), duplicate.id()))).code());

		var duplicateWithPet = ownerPetCareService.createOwner(administrator,
				new OwnerCommand("Rosa", "ConMascota", "999555300", null, null));
		var referenceWithoutPet = ownerPetCareService.createOwner(administrator,
				new OwnerCommand("Rosa", "ConMascota", "999555400", null, null));
		ownerPetCareService.createPet(administrator, duplicateWithPet.id(),
				new PetCommand("Nina", "Canino", null, "HEMBRA", null, 24, null));
		var blockedReview = ownerPetCareService.reportOwnerDuplicate(administrator,
				new DuplicateReviewCommand(duplicateWithPet.id(), referenceWithoutPet.id()));
		ApiException activeRelation = assertThrows(ApiException.class,
				() -> ownerPetCareService.resolveOwnerDuplicate(administrator, blockedReview.id(),
						new DuplicateResolutionCommand("CONFIRMADA_INACTIVA", "La revisión no puede dejar una responsabilidad activa.")));
		assertEquals("B02_DUPLICATE_OWNER_HAS_ACTIVE_RELATIONS", activeRelation.code());
		assertEquals("ACTIVO", ownerPetCareService.getOwner(administrator, duplicateWithPet.id()).status());
	}

	@Test
	void requiresVeterinaryEvaluationBeforeInactivatingDuplicatePetWithClinicalData() {
		SessionPrincipal administrator = administrator("veterinaria-aurora", "AV_DEMO_AURORA_PASSWORD");
		var duplicateOwner = ownerPetCareService.createOwner(administrator,
				new OwnerCommand("Marco", "Duplicado", null, null, null));
		var referenceOwner = ownerPetCareService.createOwner(administrator,
				new OwnerCommand("Marta", "Referencia", null, null, null));
		var duplicatePet = ownerPetCareService.createPet(administrator, duplicateOwner.id(),
				new PetCommand("Toby", "Canino", null, "MACHO", null, 30, null));
		var referencePet = ownerPetCareService.createPet(administrator, referenceOwner.id(),
				new PetCommand("Toby", "Canino", null, "MACHO", null, 30, null));
		SessionPrincipal veterinarian = veterinarian(administrator);
		var episode = ownerPetCareService.createClinicalEpisode(veterinarian, duplicatePet.id(),
				new ClinicalEpisodeCommand(null, "Revisión", null, "Sin alarma", null, null, null));
		ownerPetCareService.finalizeClinicalEpisode(veterinarian, episode.id(),
				new ClinicalEpisodeCommand(null, "Revisión", null, "Sin alarma", null, null, null));

		assertTrue(ownerPetCareService.findPotentialPetDuplicates(administrator, duplicatePet.id()).stream()
				.map(item -> item.id()).toList().contains(referencePet.id()));
		var review = ownerPetCareService.reportPetDuplicate(administrator,
				new DuplicateReviewCommand(duplicatePet.id(), referencePet.id()));
		ApiException requiredEvaluation = assertThrows(ApiException.class,
				() -> ownerPetCareService.resolvePetDuplicate(administrator, review.id(),
						new DuplicateResolutionCommand("CONFIRMADA_INACTIVA", "La revisión requiere evaluación clínica.")));
		assertEquals("B02_DUPLICATE_CLINICAL_EVALUATION_REQUIRED", requiredEvaluation.code());
		assertEquals("ACTIVA", ownerPetCareService.getPet(administrator, duplicatePet.id()).status());
		ownerPetCareService.evaluatePetDuplicateClinically(veterinarian, review.id());
		ownerPetCareService.resolvePetDuplicate(administrator, review.id(),
				new DuplicateResolutionCommand("CONFIRMADA_INACTIVA", "La revisión humana confirmó el registro duplicado."));
		assertEquals("INACTIVA", ownerPetCareService.getPet(administrator, duplicatePet.id()).status());
		assertEquals("CONFIRMADA_INACTIVA", ownerPetCareService.listPetDuplicateReviews(administrator).stream()
				.filter(item -> item.id().equals(review.id())).findFirst().orElseThrow().outcome());
		assertEquals("B02_PET_INACTIVE", assertThrows(ApiException.class,
				() -> ownerPetCareService.createClinicalEpisode(veterinarian, duplicatePet.id(),
						new ClinicalEpisodeCommand(null, "No procede", null, "No procede", null, null, null))).code());

		SessionPrincipal bosqueAdministrator = administrator("veterinaria-bosque", "AV_DEMO_BOSQUE_PASSWORD");
		assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class,
				() -> ownerPetCareService.resolvePetDuplicate(bosqueAdministrator, review.id(),
						new DuplicateResolutionCommand("DESCARTADA", "Intento entre veterinarias."))).status());
	}

	private SessionPrincipal veterinarian(SessionPrincipal administrator) {
		return veterinarian(administrator, "veterinaria-aurora");
	}

	private SessionPrincipal veterinarian(SessionPrincipal administrator, String tenantSlug) {
		String email = "veterinario-" + UUID.randomUUID() + "@demo.invalid";
		String password = generatedPassword();
		StaffInvitationResult invitation = administrationService.inviteStaff(administrator, email, List.of("VETERINARIO"));
		administrationService.acceptStaffInvitation(tokenFor(invitation.principalId()), password);
		return new SessionPrincipal(identityAccessService.authenticateStaff(tenantSlug, email, password).context());
	}

	private SessionPrincipal administrator(String tenantSlug, String passwordVariable) {
		String password = System.getenv(passwordVariable);
		assertNotNull(password, "La prueba requiere credenciales sintéticas locales.");
		return new SessionPrincipal(identityAccessService.authenticateStaff(tenantSlug, "administracion@demo.invalid", password).context());
	}

	private SessionPrincipal client(String tenantSlug) {
		String email = "cliente-" + UUID.randomUUID() + "@demo.invalid";
		String password = generatedPassword();
		var registration = administrationService.registerClient(tenantSlug, email, password);
		administrationService.verifyEmailLink(tokenFor(registration.principalId()));
		return new SessionPrincipal(identityAccessService.authenticateClient(tenantSlug, email, password).context());
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
