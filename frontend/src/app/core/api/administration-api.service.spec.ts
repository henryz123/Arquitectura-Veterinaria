import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { AdministrationApiService } from './administration-api.service';

describe('AdministrationApiService', () => {
  let service: AdministrationApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdministrationApiService);
    http = TestBed.inject(HttpTestingController);
  });

  it('uses the protected staff endpoints with the exact HTTP contracts', () => {
    service.listStaff().subscribe();
    const list = http.expectOne('/api/v1/staff-accounts');
    expect(list.request.method).toBe('GET');
    list.flush({ data: [] });

    service.inviteStaff('equipo@demo.invalid', ['RECEPCION']).subscribe();
    const invitation = http.expectOne('/api/v1/staff-invitations');
    expect(invitation.request.method).toBe('POST');
    expect(invitation.request.body).toEqual({ email: 'equipo@demo.invalid', roles: ['RECEPCION'] });
    invitation.flush({ data: { principalId: 'staff-1', status: 'INVITADA', roles: ['RECEPCION'] } });

    service.replaceStaffRoles('staff-1', ['CAJERO', 'RECEPCION']).subscribe();
    const roles = http.expectOne('/api/v1/staff-accounts/staff-1/roles');
    expect(roles.request.method).toBe('PATCH');
    expect(roles.request.body).toEqual({ roles: ['CAJERO', 'RECEPCION'] });
    roles.flush({ data: { principalId: 'staff-1', email: 'equipo@demo.invalid', status: 'ACTIVA', roles: ['CAJERO', 'RECEPCION'] } });

    service.deactivateStaff('staff-1').subscribe();
    const deactivation = http.expectOne('/api/v1/staff-accounts/staff-1/deactivations');
    expect(deactivation.request.method).toBe('POST');
    expect(deactivation.request.body).toEqual({});
    deactivation.flush(null);

    service.listOrganizations().subscribe();
    const organizations = http.expectOne('/api/v1/tenants');
    expect(organizations.request.method).toBe('GET');
    organizations.flush({ data: [] });

    service.suspendOrganization('tenant-1', 'Incumplimiento documentado').subscribe();
    const suspension = http.expectOne('/api/v1/tenants/tenant-1/suspensions');
    expect(suspension.request.method).toBe('POST');
    expect(suspension.request.body).toEqual({ reason: 'Incumplimiento documentado', confirmed: true });
    suspension.flush(null);

    service.reactivateOrganization('tenant-1', 'Revisión aprobada').subscribe();
    const reactivation = http.expectOne('/api/v1/tenants/tenant-1/reactivations');
    expect(reactivation.request.method).toBe('POST');
    expect(reactivation.request.body).toEqual({ reason: 'Revisión aprobada', confirmed: true });
    reactivation.flush(null);

    service.listRucReviews().subscribe();
    const rucReviews = http.expectOne('/api/v1/ruc-reviews');
    expect(rucReviews.request.method).toBe('GET');
    rucReviews.flush({ data: [] });

    service.resolveRucReview('review-1', 'ACREDITADA', 'Documentación validada', false).subscribe();
    const rucResolution = http.expectOne('/api/v1/ruc-reviews/review-1');
    expect(rucResolution.request.method).toBe('PATCH');
    expect(rucResolution.request.body).toEqual({ status: 'ACREDITADA', reason: 'Documentación validada', releaseRuc: false, confirmed: true });
    rucResolution.flush(null);

    service.listAdministrativeRecoveries().subscribe();
    const recoveries = http.expectOne('/api/v1/administrative-recoveries');
    expect(recoveries.request.method).toBe('GET');
    recoveries.flush({ data: [] });

    service.reviewAdministrativeRecovery('recovery-1', 'VERIFICANDO', 'Evidencia en revisión', false).subscribe();
    const recoveryReview = http.expectOne('/api/v1/administrative-recoveries/recovery-1');
    expect(recoveryReview.request.method).toBe('PATCH');
    expect(recoveryReview.request.body).toEqual({ status: 'VERIFICANDO', reason: 'Evidencia en revisión', legitimacyChecked: false });
    recoveryReview.flush(null);

    service.clientSpace().subscribe();
    const clientSpace = http.expectOne('/api/v1/client-space');
    expect(clientSpace.request.method).toBe('GET');
    clientSpace.flush({ data: { tenantSlug: 'veterinaria-aurora', commercialName: 'Aurora', message: 'Espacio protegido.' } });

    service.requestClientOwnerLink().subscribe();
    const linkRequest = http.expectOne('/api/v1/client/owner-link-requests');
    expect(linkRequest.request.method).toBe('POST');
    expect(linkRequest.request.body).toEqual({});
    linkRequest.flush({ data: { status: 'PENDIENTE', requestReference: 'VINC-123', requestedAt: '2026-10-24T00:00:00Z', resolvedAt: null, rejectionReason: null } });

    service.getClientOwnerLinkStatus().subscribe();
    const linkStatus = http.expectOne('/api/v1/client/owner-link-status');
    expect(linkStatus.request.method).toBe('GET');
    linkStatus.flush({ data: { status: 'SIN_SOLICITUD', requestReference: null, requestedAt: null, resolvedAt: null, rejectionReason: null } });

    service.listClientPets().subscribe();
    const clientPets = http.expectOne('/api/v1/client/pets');
    expect(clientPets.request.method).toBe('GET');
    clientPets.flush({ data: [] });

    service.getClientOwnerProfile().subscribe();
    const clientOwner = http.expectOne('/api/v1/client/owner-profile');
    expect(clientOwner.request.method).toBe('GET');
    clientOwner.flush({ data: { id: 'owner-1' } });

    const ownProfile = { givenNames: 'Cliente', familyNames: 'Sintetico', phone: null, email: 'cliente@demo.invalid', address: null };
    service.updateClientOwnerProfile(ownProfile).subscribe();
    const updateOwner = http.expectOne('/api/v1/client/owner-profile');
    expect(updateOwner.request.method).toBe('PUT');
    expect(updateOwner.request.body).toEqual(ownProfile);
    updateOwner.flush({ data: { id: 'owner-1' } });

    const clientPet = { name: 'Luna', species: 'Canino', breed: null, sex: 'HEMBRA' as const, birthDate: '2022-01-01', estimatedAgeMonths: null, distinguishingMarks: null };
    service.createClientPet(clientPet).subscribe();
    const createClientPet = http.expectOne('/api/v1/client/pets');
    expect(createClientPet.request.method).toBe('POST');
    expect(createClientPet.request.body).toEqual(clientPet);
    createClientPet.flush({ data: { id: 'pet-1' } });

    service.getClientPet('pet-1').subscribe();
    const getClientPet = http.expectOne('/api/v1/client/pets/pet-1');
    expect(getClientPet.request.method).toBe('GET');
    getClientPet.flush({ data: { id: 'pet-1' } });

    service.updateClientPet('pet-1', clientPet).subscribe();
    const updateClientPet = http.expectOne('/api/v1/client/pets/pet-1');
    expect(updateClientPet.request.method).toBe('PUT');
    expect(updateClientPet.request.body).toEqual(clientPet);
    updateClientPet.flush({ data: { id: 'pet-1' } });

    service.listClientOwnerLinkRequests().subscribe();
    const pendingLinks = http.expectOne('/api/v1/owner-link-requests?status=PENDIENTE');
    expect(pendingLinks.request.method).toBe('GET');
    pendingLinks.flush({ data: [] });

    const linkResolution = { status: 'APROBADA' as const, ownerId: 'owner-1', verificationMethod: 'Verificacion presencial', rejectionReason: null, identityVerified: true };
    service.resolveClientOwnerLinkRequest('link-1', linkResolution).subscribe();
    const resolvedLink = http.expectOne('/api/v1/owner-link-requests/link-1/resolution');
    expect(resolvedLink.request.method).toBe('POST');
    expect(resolvedLink.request.body).toEqual(linkResolution);
    resolvedLink.flush({ data: { id: 'link-1', status: 'APROBADA' } });

    service.registerClient('veterinaria-aurora', 'cliente@demo.invalid', 'ClaveSintetica123').subscribe();
    const registration = http.expectOne('/api/v1/tenants/veterinaria-aurora/client-registrations');
    expect(registration.request.method).toBe('POST');
    expect(registration.request.body).toEqual({ email: 'cliente@demo.invalid', password: 'ClaveSintetica123' });
    registration.flush(null);

    service.resendClientVerification('veterinaria-aurora', 'cliente@demo.invalid').subscribe();
    const resend = http.expectOne('/api/v1/tenants/veterinaria-aurora/client-verification-resends');
    expect(resend.request.method).toBe('POST');
    expect(resend.request.body).toEqual({ email: 'cliente@demo.invalid' });
    resend.flush(null);

    service.verifyEmail('opaque-test-token').subscribe();
    const verification = http.expectOne('/api/v1/email-verifications');
    expect(verification.request.method).toBe('POST');
    expect(verification.request.body).toEqual({ token: 'opaque-test-token' });
    verification.flush({ data: { status: 'ACTIVA', accountType: 'CLIENT', relatedId: 'client-1' } });

    service.requestClientPasswordRecovery('veterinaria-aurora', 'cliente@demo.invalid').subscribe();
    const recovery = http.expectOne('/api/v1/auth/password-recovery-requests');
    expect(recovery.request.method).toBe('POST');
    expect(recovery.request.body).toEqual({ accountType: 'CLIENT', tenantSlug: 'veterinaria-aurora', email: 'cliente@demo.invalid' });
    recovery.flush(null);

    service.resetPassword('opaque-test-token', 'ClaveNuevaSintetica123').subscribe();
    const completion = http.expectOne('/api/v1/auth/password-recoveries');
    expect(completion.request.method).toBe('POST');
    expect(completion.request.body).toEqual({ token: 'opaque-test-token', password: 'ClaveNuevaSintetica123' });
    completion.flush(null);

    service.listOwners('Luna').subscribe();
    const owners = http.expectOne('/api/v1/owners?query=Luna');
    expect(owners.request.method).toBe('GET');
    owners.flush({ data: [] });

    service.createOwner({ givenNames: 'Luna', familyNames: 'Rojas', phone: null, email: null, address: null }).subscribe();
    const owner = http.expectOne('/api/v1/owners');
    expect(owner.request.method).toBe('POST');
    expect(owner.request.body).toEqual({ givenNames: 'Luna', familyNames: 'Rojas', phone: null, email: null, address: null });
    owner.flush({ data: { id: 'owner-1' } });

    service.updateOwner('owner-1', {
      givenNames: 'Luna', familyNames: 'Actualizada', phone: '999100200', email: 'luna@demo.invalid', address: 'Dirección sintética',
    }).subscribe();
    const ownerUpdate = http.expectOne('/api/v1/owners/owner-1');
    expect(ownerUpdate.request.method).toBe('PUT');
    expect(ownerUpdate.request.body.familyNames).toBe('Actualizada');
    ownerUpdate.flush({ data: { id: 'owner-1' } });

    service.createPet('owner-1', {
      name: 'Nube', species: 'Canino', breed: null, sex: 'HEMBRA', birthDate: null, estimatedAgeMonths: 24, distinguishingMarks: null,
    }).subscribe();
    const pet = http.expectOne('/api/v1/owners/owner-1/pets');
    expect(pet.request.method).toBe('POST');
    expect(pet.request.body.name).toBe('Nube');
    pet.flush({ data: { id: 'pet-1' } });

    service.updatePet('pet-1', {
      name: 'Nube', species: 'Canino', breed: 'Mestizo', sex: 'HEMBRA', birthDate: null, estimatedAgeMonths: 24,
      distinguishingMarks: 'Collar azul',
    }).subscribe();
    const petUpdate = http.expectOne('/api/v1/pets/pet-1');
    expect(petUpdate.request.method).toBe('PUT');
    expect(petUpdate.request.body.distinguishingMarks).toBe('Collar azul');
    petUpdate.flush({ data: { id: 'pet-1' } });

    service.listPetsByOwner('owner-1').subscribe();
    const ownerPets = http.expectOne('/api/v1/owners/owner-1/pets');
    expect(ownerPets.request.method).toBe('GET');
    ownerPets.flush({ data: [] });

    service.getPet('pet-1').subscribe();
    const petDetail = http.expectOne('/api/v1/pets/pet-1');
    expect(petDetail.request.method).toBe('GET');
    petDetail.flush({ data: { id: 'pet-1' } });

    service.listOwnershipPeriods('pet-1').subscribe();
    const ownershipPeriods = http.expectOne('/api/v1/pets/pet-1/ownership-periods');
    expect(ownershipPeriods.request.method).toBe('GET');
    ownershipPeriods.flush({ data: [] });

    const transferRequest = { expectedOwnerId: 'owner-1', newOwnerId: 'owner-2', reason: 'Cambio verificado', identityVerified: true };
    service.transferPetOwnership('pet-1', transferRequest).subscribe();
    const transfer = http.expectOne('/api/v1/pets/pet-1/owner-transfers');
    expect(transfer.request.method).toBe('POST');
    expect(transfer.request.body).toEqual(transferRequest);
    transfer.flush({ data: { id: 'pet-1', currentOwnerId: 'owner-2' } });

    service.findPotentialOwnerDuplicates('owner-1').subscribe();
    const ownerCandidates = http.expectOne('/api/v1/owners/owner-1/duplicate-candidates');
    expect(ownerCandidates.request.method).toBe('GET');
    ownerCandidates.flush({ data: [] });

    const duplicateReview = { duplicateId: 'owner-1', referenceId: 'owner-2' };
    service.reportOwnerDuplicate(duplicateReview).subscribe();
    const ownerReview = http.expectOne('/api/v1/owner-duplicate-reviews');
    expect(ownerReview.request.method).toBe('POST');
    expect(ownerReview.request.body).toEqual(duplicateReview);
    ownerReview.flush({ data: { id: 'owner-review-1' } });

    service.listOwnerDuplicateReviews().subscribe();
    const ownerReviews = http.expectOne('/api/v1/owner-duplicate-reviews');
    expect(ownerReviews.request.method).toBe('GET');
    ownerReviews.flush({ data: [] });

    const duplicateResolution = { outcome: 'DESCARTADA' as const, reason: 'Fichas verificadas como distintas.' };
    service.resolveOwnerDuplicate('owner-review-1', duplicateResolution).subscribe();
    const ownerResolution = http.expectOne('/api/v1/owner-duplicate-reviews/owner-review-1/resolution');
    expect(ownerResolution.request.method).toBe('POST');
    expect(ownerResolution.request.body).toEqual(duplicateResolution);
    ownerResolution.flush(null);

    service.findPotentialPetDuplicates('pet-1').subscribe();
    const petCandidates = http.expectOne('/api/v1/pets/pet-1/duplicate-candidates');
    expect(petCandidates.request.method).toBe('GET');
    petCandidates.flush({ data: [] });

    service.reportPetDuplicate({ duplicateId: 'pet-1', referenceId: 'pet-2' }).subscribe();
    const petReview = http.expectOne('/api/v1/pet-duplicate-reviews');
    expect(petReview.request.method).toBe('POST');
    expect(petReview.request.body).toEqual({ duplicateId: 'pet-1', referenceId: 'pet-2' });
    petReview.flush({ data: { id: 'pet-review-1' } });

    service.listPetDuplicateReviews().subscribe();
    const petReviews = http.expectOne('/api/v1/pet-duplicate-reviews');
    expect(petReviews.request.method).toBe('GET');
    petReviews.flush({ data: [] });

    service.evaluatePetDuplicateClinically('pet-review-1').subscribe();
    const clinicalEvaluation = http.expectOne('/api/v1/pet-duplicate-reviews/pet-review-1/clinical-evaluation');
    expect(clinicalEvaluation.request.method).toBe('POST');
    expect(clinicalEvaluation.request.body).toEqual({});
    clinicalEvaluation.flush(null);

    service.resolvePetDuplicate('pet-review-1', duplicateResolution).subscribe();
    const petResolution = http.expectOne('/api/v1/pet-duplicate-reviews/pet-review-1/resolution');
    expect(petResolution.request.method).toBe('POST');
    expect(petResolution.request.body).toEqual(duplicateResolution);
    petResolution.flush(null);

    service.listClinicalEpisodes('pet-1').subscribe();
    const history = http.expectOne('/api/v1/pets/pet-1/clinical-episodes');
    expect(history.request.method).toBe('GET');
    history.flush({ data: [] });

    const clinicalRequest = { reason: 'Control', anamnesis: null, assessment: 'Sin hallazgos', diagnosis: null, treatmentPlan: null, observations: null };
    service.createClinicalEpisode('pet-1', clinicalRequest).subscribe();
    const episode = http.expectOne('/api/v1/pets/pet-1/clinical-episodes');
    expect(episode.request.method).toBe('POST');
    expect(episode.request.body).toEqual(clinicalRequest);
    episode.flush({ data: { id: 'episode-1' } });

    service.finalizeClinicalEpisode('episode-1', clinicalRequest).subscribe();
    const finalization = http.expectOne('/api/v1/clinical-episodes/episode-1/finalization');
    expect(finalization.request.method).toBe('POST');
    expect(finalization.request.body).toEqual(clinicalRequest);
    finalization.flush({ data: { id: 'episode-1', status: 'FINALIZADO' } });

    service.listVaccinations('pet-1').subscribe();
    const vaccinations = http.expectOne('/api/v1/pets/pet-1/vaccinations');
    expect(vaccinations.request.method).toBe('GET');
    vaccinations.flush({ data: [] });

    const vaccinationRequest = {
      episodeId: 'episode-1', operationId: 'operation-1', vaccineName: 'Vacuna sintética', administeredOn: '2026-10-24',
      dose: '1 ml', batchCode: 'Lote', nextDueOn: '2027-10-24',
    };
    service.recordVaccination('pet-1', vaccinationRequest).subscribe();
    const vaccination = http.expectOne('/api/v1/pets/pet-1/vaccinations');
    expect(vaccination.request.method).toBe('POST');
    expect(vaccination.request.body).toEqual(vaccinationRequest);
    vaccination.flush({ data: { id: 'vaccination-1' } });

    service.listClinicalPrescriptions('episode-1').subscribe();
    const prescriptions = http.expectOne('/api/v1/clinical-episodes/episode-1/prescriptions');
    expect(prescriptions.request.method).toBe('GET');
    prescriptions.flush({ data: [] });

    const prescriptionRequest = {
      medication: 'Medicamento', presentation: 'Tableta', dose: 'Una', route: 'Oral', frequency: 'Cada 24 horas',
      duration: 'Tres días', instructions: 'Con alimento', prescribedOn: '2026-10-24',
    };
    service.recordClinicalPrescription('episode-1', prescriptionRequest).subscribe();
    const prescription = http.expectOne('/api/v1/clinical-episodes/episode-1/prescriptions');
    expect(prescription.request.method).toBe('POST');
    expect(prescription.request.body).toEqual(prescriptionRequest);
    prescription.flush({ data: { id: 'prescription-1' } });

    service.listClinicalEpisodeAmendments('episode-1').subscribe();
    const amendments = http.expectOne('/api/v1/clinical-episodes/episode-1/amendments');
    expect(amendments.request.method).toBe('GET');
    amendments.flush({ data: [] });

    const amendmentRequest = { reason: 'Precisión clínica', addendum: 'Se añade una indicación.' };
    service.amendClinicalEpisode('episode-1', amendmentRequest).subscribe();
    const amendment = http.expectOne('/api/v1/clinical-episodes/episode-1/amendments');
    expect(amendment.request.method).toBe('POST');
    expect(amendment.request.body).toEqual(amendmentRequest);
    amendment.flush({ data: { id: 'amendment-1' } });
  });

  afterEach(() => http.verify());
});
