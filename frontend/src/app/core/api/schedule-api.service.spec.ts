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
});
