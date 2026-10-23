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
  });

  afterEach(() => http.verify());
});
