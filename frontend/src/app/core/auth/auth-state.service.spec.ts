import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { AuthStateService } from './auth-state.service';

describe('AuthStateService', () => {
  let service: AuthStateService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthStateService);
    http = TestBed.inject(HttpTestingController);
  });

  it('keeps the CSRF token only in memory after a successful login', () => {
    service
      .loginStaff('veterinaria-aurora', {
        email: 'administracion@demo.invalid',
        password: 'synthetic-password',
      })
      .subscribe();
    const request = http.expectOne('/api/v1/auth/tenants/veterinaria-aurora/staff-sessions');
    expect(request.request.withCredentials).toBe(true);
    request.flush({
      data: {
        csrfToken: 'csrf-memory-only',
        context: {
          accountType: 'STAFF',
          tenant: { slug: 'veterinaria-aurora', commercialName: 'Aurora', status: 'ACTIVO' },
          roles: ['ADMIN_VETERINARIA'],
        },
      },
    });
    expect(service.csrfToken()).toBe('csrf-memory-only');
    expect(service.context()?.tenant.slug).toBe('veterinaria-aurora');
  });

  afterEach(() => http.verify());
});
