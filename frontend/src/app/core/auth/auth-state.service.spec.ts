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
    expect(service.context()?.tenant?.slug).toBe('veterinaria-aurora');
  });

  it('represents a SaaS session without inventing a tenant context', () => {
    service.loginSaas({ email: 'saas@demo.invalid', password: 'synthetic-password' }).subscribe();
    const request = http.expectOne('/api/v1/auth/saas-sessions');
    expect(request.request.method).toBe('POST');
    request.flush({
      data: {
        csrfToken: 'csrf-memory-only',
        context: { accountType: 'SAAS', tenant: null, roles: ['ADMIN_SAAS'] },
      },
    });
    expect(service.context()).toEqual({ accountType: 'SAAS', tenant: null, roles: ['ADMIN_SAAS'] });
  });

  it('uses the CLIENT route without inheriting a STAFF context', () => {
    service.loginClient('veterinaria-aurora', { email: 'cliente@demo.invalid', password: 'synthetic-password' }).subscribe();
    const request = http.expectOne('/api/v1/auth/tenants/veterinaria-aurora/client-sessions');
    expect(request.request.withCredentials).toBe(true);
    request.flush({
      data: {
        csrfToken: 'csrf-memory-only',
        context: { accountType: 'CLIENT', tenant: { slug: 'veterinaria-aurora', commercialName: 'Aurora', status: 'ACTIVO' }, roles: [] },
      },
    });
    expect(service.context()?.accountType).toBe('CLIENT');
    expect(service.context()?.roles).toEqual([]);
  });

  it('restores an authenticated session and refreshes its CSRF token only in memory', () => {
    let restored;
    service.restoreContext().subscribe((response) => restored = response);
    const contextRequest = http.expectOne('/api/v1/session-context');
    expect(contextRequest.request.method).toBe('GET');
    contextRequest.flush({
      data: {
        accountType: 'STAFF',
        tenant: { slug: 'veterinaria-aurora', commercialName: 'Aurora', status: 'ACTIVO' },
        roles: ['ADMIN_VETERINARIA'],
      },
    });

    const csrfRequest = http.expectOne('/api/v1/auth/session-csrf-token');
    expect(csrfRequest.request.method).toBe('POST');
    expect(csrfRequest.request.headers.get('X-AV-CSRF-Bootstrap')).toBe('1');
    expect(csrfRequest.request.withCredentials).toBe(true);
    csrfRequest.flush({ data: { csrfToken: 'fresh-csrf-memory-only' } });

    expect(restored).toEqual({
      data: {
        accountType: 'STAFF',
        tenant: { slug: 'veterinaria-aurora', commercialName: 'Aurora', status: 'ACTIVO' },
        roles: ['ADMIN_VETERINARIA'],
      },
    });
    expect(service.csrfToken()).toBe('fresh-csrf-memory-only');
  });

  afterEach(() => http.verify());
});
