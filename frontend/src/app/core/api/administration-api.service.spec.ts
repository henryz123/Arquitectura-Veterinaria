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

    service.createPet('owner-1', {
      name: 'Nube', species: 'Canino', breed: null, sex: 'HEMBRA', birthDate: null, estimatedAgeMonths: 24, distinguishingMarks: null,
    }).subscribe();
    const pet = http.expectOne('/api/v1/owners/owner-1/pets');
    expect(pet.request.method).toBe('POST');
    expect(pet.request.body.name).toBe('Nube');
    pet.flush({ data: { id: 'pet-1' } });

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
  });

  afterEach(() => http.verify());
});
