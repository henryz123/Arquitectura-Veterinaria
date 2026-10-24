package pe.arquitecturaveterinaria.ownerpetcare.api;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.arquitecturaveterinaria.identityaccess.application.SessionPrincipal;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.AmendmentCommand;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.ClinicalEpisodeCommand;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.ClientOwnerLinkResolutionCommand;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.ClientOwnerLinkStatus;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.OwnerCommand;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.OwnershipTransferCommand;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.PetCommand;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.PrescriptionCommand;
import pe.arquitecturaveterinaria.ownerpetcare.application.OwnerPetCareService.VaccinationCommand;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.ClinicalEpisode;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.ClinicalEpisodeAmendment;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.ClinicalPrescription;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.ClientOwnerLinkRequest;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.ClientPetProfile;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.OwnerProfile;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.PetProfile;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.OwnershipPeriod;
import pe.arquitecturaveterinaria.ownerpetcare.infrastructure.OwnerPetCareJdbcRepository.VaccinationRecord;
import pe.arquitecturaveterinaria.shared.api.ApiEnvelope;

@RestController
@RequestMapping("/api/v1")
@Validated
public class OwnerPetCareController {

	private final OwnerPetCareService service;

	public OwnerPetCareController(OwnerPetCareService service) {
		this.service = service;
	}

	@PostMapping("/owners")
	ResponseEntity<ApiEnvelope<OwnerProfile>> createOwner(@AuthenticationPrincipal SessionPrincipal principal,
			@Valid @RequestBody OwnerRequest request) {
		OwnerProfile owner = service.createOwner(principal,
				new OwnerCommand(request.givenNames(), request.familyNames(), request.phone(), request.email(), request.address()));
		return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/owners/" + owner.id()))
				.body(ApiEnvelope.of(owner));
	}

	@GetMapping("/owners")
	ApiEnvelope<List<OwnerProfile>> listOwners(@AuthenticationPrincipal SessionPrincipal principal,
			@RequestParam(required = false) @Size(max = 120) String query) {
		return ApiEnvelope.of(service.listOwners(principal, query));
	}

	@GetMapping("/owners/{ownerId}")
	ApiEnvelope<OwnerProfile> getOwner(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID ownerId) {
		return ApiEnvelope.of(service.getOwner(principal, ownerId));
	}

	@PostMapping("/owners/{ownerId}/pets")
	ResponseEntity<ApiEnvelope<PetProfile>> createPet(@AuthenticationPrincipal SessionPrincipal principal,
			@PathVariable UUID ownerId, @Valid @RequestBody PetRequest request) {
		PetProfile pet = service.createPet(principal, ownerId, new PetCommand(request.name(), request.species(), request.breed(),
				request.sex(), request.birthDate(), request.estimatedAgeMonths(), request.distinguishingMarks()));
		return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/pets/" + pet.id()))
				.body(ApiEnvelope.of(pet));
	}

	@GetMapping("/owners/{ownerId}/pets")
	ApiEnvelope<List<PetProfile>> listPetsByOwner(@AuthenticationPrincipal SessionPrincipal principal,
			@PathVariable UUID ownerId) {
		return ApiEnvelope.of(service.listPetsByOwner(principal, ownerId));
	}

	@GetMapping("/pets/{petId}")
	ApiEnvelope<PetProfile> getPet(@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID petId) {
		return ApiEnvelope.of(service.getPet(principal, petId));
	}

	@PostMapping("/pets/{petId}/owner-transfers")
	ApiEnvelope<PetProfile> transferPetOwnership(@AuthenticationPrincipal SessionPrincipal principal,
			@PathVariable UUID petId, @Valid @RequestBody OwnershipTransferRequest request) {
		return ApiEnvelope.of(service.transferPetOwnership(principal, petId,
				new OwnershipTransferCommand(request.expectedOwnerId(), request.newOwnerId(), request.reason(), request.identityVerified())));
	}

	@GetMapping("/pets/{petId}/ownership-periods")
	ApiEnvelope<List<OwnershipPeriod>> listOwnershipPeriods(@AuthenticationPrincipal SessionPrincipal principal,
			@PathVariable UUID petId) {
		return ApiEnvelope.of(service.listOwnershipPeriods(principal, petId));
	}

	@PostMapping("/client/owner-link-requests")
	ResponseEntity<ApiEnvelope<ClientOwnerLinkStatus>> requestClientOwnerLink(@AuthenticationPrincipal SessionPrincipal principal) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.of(service.requestClientOwnerLink(principal)));
	}

	@GetMapping("/client/owner-link-status")
	ApiEnvelope<ClientOwnerLinkStatus> getClientOwnerLinkStatus(@AuthenticationPrincipal SessionPrincipal principal) {
		return ApiEnvelope.of(service.getClientOwnerLinkStatus(principal));
	}

	@GetMapping("/client/pets")
	ApiEnvelope<List<ClientPetProfile>> listClientPets(@AuthenticationPrincipal SessionPrincipal principal) {
		return ApiEnvelope.of(service.listCurrentPetsForClient(principal));
	}

	@GetMapping("/owner-link-requests")
	ApiEnvelope<List<ClientOwnerLinkRequest>> listClientOwnerLinkRequests(@AuthenticationPrincipal SessionPrincipal principal,
			@RequestParam(required = false) @Pattern(regexp = "PENDIENTE|APROBADA|RECHAZADA", flags = Pattern.Flag.CASE_INSENSITIVE) String status) {
		return ApiEnvelope.of(service.listClientOwnerLinkRequests(principal, status == null ? null : status.toUpperCase()));
	}

	@PostMapping("/owner-link-requests/{requestId}/resolution")
	ApiEnvelope<ClientOwnerLinkRequest> resolveClientOwnerLinkRequest(@AuthenticationPrincipal SessionPrincipal principal,
			@PathVariable UUID requestId, @Valid @RequestBody ClientOwnerLinkResolutionRequest request) {
		return ApiEnvelope.of(service.resolveClientOwnerLinkRequest(principal, requestId,
				new ClientOwnerLinkResolutionCommand(request.status(), request.ownerId(), request.verificationMethod(),
						request.rejectionReason(), request.identityVerified())));
	}

	@PostMapping("/pets/{petId}/clinical-episodes")
	ResponseEntity<ApiEnvelope<ClinicalEpisode>> createClinicalEpisode(@AuthenticationPrincipal SessionPrincipal principal,
			@PathVariable UUID petId, @Valid @RequestBody ClinicalEpisodeRequest request) {
		ClinicalEpisode episode = service.createClinicalEpisode(principal, petId, command(request));
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(URI.create("/api/v1/clinical-episodes/" + episode.id()))
				.body(ApiEnvelope.of(episode));
	}

	@PostMapping("/clinical-episodes/{episodeId}/finalization")
	ApiEnvelope<ClinicalEpisode> finalizeClinicalEpisode(@AuthenticationPrincipal SessionPrincipal principal,
			@PathVariable UUID episodeId, @Valid @RequestBody ClinicalEpisodeRequest request) {
		return ApiEnvelope.of(service.finalizeClinicalEpisode(principal, episodeId, command(request)));
	}

	@GetMapping("/pets/{petId}/clinical-episodes")
	ApiEnvelope<List<ClinicalEpisode>> listClinicalHistory(@AuthenticationPrincipal SessionPrincipal principal,
			@PathVariable UUID petId) {
		return ApiEnvelope.of(service.listClinicalHistory(principal, petId));
	}

	@PostMapping("/pets/{petId}/vaccinations")
	ResponseEntity<ApiEnvelope<VaccinationRecord>> recordVaccination(@AuthenticationPrincipal SessionPrincipal principal,
			@PathVariable UUID petId, @Valid @RequestBody VaccinationRequest request) {
		VaccinationRecord vaccination = service.recordVaccination(principal, petId,
				new VaccinationCommand(request.episodeId(), request.operationId(), request.vaccineName(), request.administeredOn(),
						request.dose(), request.batchCode(), request.nextDueOn()));
		return ResponseEntity.status(HttpStatus.CREATED).location(URI.create("/api/v1/vaccinations/" + vaccination.id()))
				.body(ApiEnvelope.of(vaccination));
	}

	@GetMapping("/pets/{petId}/vaccinations")
	ApiEnvelope<List<VaccinationRecord>> listVaccinations(@AuthenticationPrincipal SessionPrincipal principal,
			@PathVariable UUID petId) {
		return ApiEnvelope.of(service.listVaccinations(principal, petId));
	}

	@PostMapping("/clinical-episodes/{episodeId}/prescriptions")
	ResponseEntity<ApiEnvelope<ClinicalPrescription>> recordClinicalPrescription(
			@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID episodeId,
			@Valid @RequestBody PrescriptionRequest request) {
		ClinicalPrescription prescription = service.recordClinicalPrescription(principal, episodeId,
				new PrescriptionCommand(request.medication(), request.presentation(), request.dose(), request.route(),
						request.frequency(), request.duration(), request.instructions(), request.prescribedOn()));
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(URI.create("/api/v1/clinical-prescriptions/" + prescription.id())).body(ApiEnvelope.of(prescription));
	}

	@GetMapping("/clinical-episodes/{episodeId}/prescriptions")
	ApiEnvelope<List<ClinicalPrescription>> listClinicalPrescriptions(@AuthenticationPrincipal SessionPrincipal principal,
			@PathVariable UUID episodeId) {
		return ApiEnvelope.of(service.listClinicalPrescriptions(principal, episodeId));
	}

	@PostMapping("/clinical-episodes/{episodeId}/amendments")
	ResponseEntity<ApiEnvelope<ClinicalEpisodeAmendment>> amendClinicalEpisode(
			@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID episodeId,
			@Valid @RequestBody AmendmentRequest request) {
		ClinicalEpisodeAmendment amendment = service.amendClinicalEpisode(principal, episodeId,
				new AmendmentCommand(request.reason(), request.addendum()));
		return ResponseEntity.status(HttpStatus.CREATED)
				.location(URI.create("/api/v1/clinical-episode-amendments/" + amendment.id())).body(ApiEnvelope.of(amendment));
	}

	@GetMapping("/clinical-episodes/{episodeId}/amendments")
	ApiEnvelope<List<ClinicalEpisodeAmendment>> listClinicalEpisodeAmendments(
			@AuthenticationPrincipal SessionPrincipal principal, @PathVariable UUID episodeId) {
		return ApiEnvelope.of(service.listClinicalEpisodeAmendments(principal, episodeId));
	}

	private ClinicalEpisodeCommand command(ClinicalEpisodeRequest request) {
		return new ClinicalEpisodeCommand(request.appointmentId(), request.reason(), request.anamnesis(), request.assessment(),
				request.diagnosis(), request.treatmentPlan(), request.observations());
	}

	public record OwnerRequest(
			@NotBlank @Size(max = 120) String givenNames,
			@NotBlank @Size(max = 120) String familyNames,
			@Size(max = 40) String phone,
			@Email @Size(max = 254) String email,
			@Size(max = 240) String address) { }

	public record PetRequest(
			@NotBlank @Size(max = 120) String name,
			@NotBlank @Size(max = 80) String species,
			@Size(max = 120) String breed,
			@Pattern(regexp = "HEMBRA|MACHO|DESCONOCIDO", flags = Pattern.Flag.CASE_INSENSITIVE) String sex,
			LocalDate birthDate,
			@Min(0) @Max(480) Integer estimatedAgeMonths,
			@Size(max = 500) String distinguishingMarks) { }

	public record OwnershipTransferRequest(
			@NotNull UUID expectedOwnerId,
			@NotNull UUID newOwnerId,
			@NotBlank @Size(min = 3, max = 500) String reason,
			boolean identityVerified) { }

	public record ClientOwnerLinkResolutionRequest(
			@NotBlank @Pattern(regexp = "APROBADA|RECHAZADA", flags = Pattern.Flag.CASE_INSENSITIVE) String status,
			UUID ownerId,
			@NotBlank @Size(min = 3, max = 120) String verificationMethod,
			@Size(max = 500) String rejectionReason,
			boolean identityVerified) { }

	public record ClinicalEpisodeRequest(
			UUID appointmentId,
			@Size(max = 4000) String reason,
			@Size(max = 12000) String anamnesis,
			@Size(max = 12000) String assessment,
			@Size(max = 4000) String diagnosis,
			@Size(max = 12000) String treatmentPlan,
			@Size(max = 12000) String observations) { }

	public record VaccinationRequest(
			UUID episodeId,
			@NotNull UUID operationId,
			@NotBlank @Size(max = 240) String vaccineName,
			@NotNull LocalDate administeredOn,
			@Size(max = 240) String dose,
			@Size(max = 240) String batchCode,
			LocalDate nextDueOn) { }

	public record PrescriptionRequest(
			@NotBlank @Size(max = 500) String medication,
			@NotBlank @Size(max = 500) String presentation,
			@NotBlank @Size(max = 500) String dose,
			@NotBlank @Size(max = 240) String route,
			@NotBlank @Size(max = 240) String frequency,
			@NotBlank @Size(max = 240) String duration,
			@NotBlank @Size(max = 12000) String instructions,
			@NotNull LocalDate prescribedOn) { }

	public record AmendmentRequest(
			@NotBlank @Size(min = 3, max = 500) String reason,
			@NotBlank @Size(max = 12000) String addendum) { }
}
