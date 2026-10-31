import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, vi } from 'vitest';
import { NotificationService } from '../notifications/notification.service';
import { requestErrorNotificationInterceptor } from './request-error-notification.interceptor';

describe('requestErrorNotificationInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let notifications: NotificationService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([requestErrorNotificationInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    vi.useFakeTimers();
    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
    notifications = TestBed.inject(NotificationService);
  });

  afterEach(() => {
    httpTesting.verify();
    vi.useRealTimers();
  });

  it('announces a recoverable local connectivity failure without hiding the HTTP error', () => {
    let receivedStatus: number | undefined;
    http.get('/api/v1/example').subscribe({ error: (error) => receivedStatus = error.status });

    httpTesting.expectOne('/api/v1/example').error(new ProgressEvent('error'));

    expect(receivedStatus).toBe(0);
    expect(notifications.notifications()[0]?.kind).toBe('error');
    expect(notifications.notifications()[0]?.message).toMatch(/No fue posible conectar/);
  });

  it('explains invalid credentials without presenting them as an expired session', () => {
    let receivedStatus: number | undefined;
    http.post('/api/v1/auth/tenants/veterinaria-aurora/staff-sessions', {}).subscribe({
      error: (error) => receivedStatus = error.status,
    });

    httpTesting.expectOne('/api/v1/auth/tenants/veterinaria-aurora/staff-sessions').flush(
      { code: 'UNAUTHORIZED' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(receivedStatus).toBe(401);
    expect(notifications.notifications()[0]?.message).toMatch(/No fue posible iniciar sesi.n/i);
  });

  it('treats an unavailable local backend behind the proxy as a connectivity failure', () => {
    http.post('/api/v1/auth/tenants/veterinaria-aurora/staff-sessions', {}).subscribe({ error: () => undefined });

    httpTesting.expectOne('/api/v1/auth/tenants/veterinaria-aurora/staff-sessions').flush(
      { code: 'UPSTREAM_UNAVAILABLE' },
      { status: 504, statusText: 'Gateway Timeout' },
    );

    expect(notifications.notifications()[0]?.message).toMatch(/No fue posible conectar/);
  });
});
