import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ScheduleApiService } from './schedule-api.service';

describe('ScheduleApiService', () => {
  it('requests the minimal protected assignable-professionals projection', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(ScheduleApiService);
    const http = TestBed.inject(HttpTestingController);

    service.listAssignableProfessionals().subscribe();

    const request = http.expectOne('/api/v1/schedule/assignable-professionals');
    expect(request.request.method).toBe('GET');
    request.flush({ data: [{ principalId: 'professional-1', roles: ['VETERINARIO'] }] });
    http.verify();
  });

  it('creates an offered service with the protected schedule endpoint', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(ScheduleApiService);
    const http = TestBed.inject(HttpTestingController);
    const body = {
      serviceType: 'CLINICA' as const,
      name: 'Consulta general',
      publicDescription: null,
      durationMinutes: 30,
      referencePrice: 45,
      published: true,
    };

    service.createOfferedService(body).subscribe();

    const request = http.expectOne('/api/v1/schedule/services');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({ data: { ...body, id: 'service-1', tenantId: 'tenant-1', status: 'ACTIVO' } });
    http.verify();
  });
});
