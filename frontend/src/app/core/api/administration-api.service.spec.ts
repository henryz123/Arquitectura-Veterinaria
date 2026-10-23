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
  });

  afterEach(() => http.verify());
});
