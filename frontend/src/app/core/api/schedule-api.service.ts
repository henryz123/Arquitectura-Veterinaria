import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ApiEnvelope,
  AssignableProfessional,
  OfferedService,
  OfferedServiceRequest,
  OperatingHours,
  OperatingHoursRequest,
  ProfessionalAvailability,
  ProfessionalAvailabilityRequest,
} from './api.models';

@Injectable({ providedIn: 'root' })
export class ScheduleApiService {
  private readonly http = inject(HttpClient);

  listAssignableProfessionals(): Observable<ApiEnvelope<AssignableProfessional[]>> {
    return this.http.get<ApiEnvelope<AssignableProfessional[]>>('/api/v1/schedule/assignable-professionals');
  }

  listOfferedServices(): Observable<ApiEnvelope<OfferedService[]>> {
    return this.http.get<ApiEnvelope<OfferedService[]>>('/api/v1/schedule/services');
  }

  createOfferedService(request: OfferedServiceRequest): Observable<ApiEnvelope<OfferedService>> {
    return this.http.post<ApiEnvelope<OfferedService>>('/api/v1/schedule/services', request);
  }

  listOperatingHours(): Observable<ApiEnvelope<OperatingHours[]>> {
    return this.http.get<ApiEnvelope<OperatingHours[]>>('/api/v1/schedule/operating-hours');
  }

  createOperatingHours(request: OperatingHoursRequest): Observable<ApiEnvelope<OperatingHours>> {
    return this.http.post<ApiEnvelope<OperatingHours>>('/api/v1/schedule/operating-hours', request);
  }

  listProfessionalAvailability(): Observable<ApiEnvelope<ProfessionalAvailability[]>> {
    return this.http.get<ApiEnvelope<ProfessionalAvailability[]>>('/api/v1/schedule/professional-availability');
  }

  createProfessionalAvailability(request: ProfessionalAvailabilityRequest): Observable<ApiEnvelope<ProfessionalAvailability>> {
    return this.http.post<ApiEnvelope<ProfessionalAvailability>>('/api/v1/schedule/professional-availability', request);
  }
}
