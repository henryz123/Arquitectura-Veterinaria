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
  ClientScheduleRequest,

  ClientScheduleCancellationRequest,

  ScheduleCancellationResolutionRequest,
  ScheduleAvailability,
  ScheduleRequest,
  ScheduleReservation,
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

  listScheduleRequests(): Observable<ApiEnvelope<ScheduleRequest[]>> {
    return this.http.get<ApiEnvelope<ScheduleRequest[]>>('/api/v1/schedule/requests');
  }

  checkAvailability(serviceId: string, professionalPrincipalId: string, startsAt: string): Observable<ApiEnvelope<ScheduleAvailability>> {
    const query = new URLSearchParams({ serviceId, professionalPrincipalId, startsAt });
    return this.http.get<ApiEnvelope<ScheduleAvailability>>(`/api/v1/schedule/availability?${query.toString()}`);
  }

  confirmScheduleRequest(requestId: string, professionalPrincipalId: string): Observable<ApiEnvelope<ScheduleReservation>> {
    return this.http.post<ApiEnvelope<ScheduleReservation>>(
      `/api/v1/schedule/requests/${encodeURIComponent(requestId)}/confirmation`, { professionalPrincipalId },
    );
  }

  resolveCancellation(requestId: string, request: ScheduleCancellationResolutionRequest): Observable<ApiEnvelope<ScheduleRequest>> {
    return this.http.post<ApiEnvelope<ScheduleRequest>>(
      `/api/v1/schedule/requests/${encodeURIComponent(requestId)}/cancellation-resolution`, request,
    );
  }

  listClientScheduleServices(): Observable<ApiEnvelope<OfferedService[]>> {
    return this.http.get<ApiEnvelope<OfferedService[]>>('/api/v1/client/schedule/services');
  }

  listClientScheduleRequests(): Observable<ApiEnvelope<ScheduleRequest[]>> {
    return this.http.get<ApiEnvelope<ScheduleRequest[]>>('/api/v1/client/schedule/requests');
  }

  createClientScheduleRequest(request: ClientScheduleRequest): Observable<ApiEnvelope<ScheduleRequest>> {
    return this.http.post<ApiEnvelope<ScheduleRequest>>('/api/v1/client/schedule/requests', request);
  }

  cancelClientScheduleRequest(requestId: string, request: ClientScheduleCancellationRequest): Observable<ApiEnvelope<ScheduleRequest>> {
    return this.http.post<ApiEnvelope<ScheduleRequest>>(
      `/api/v1/client/schedule/requests/${encodeURIComponent(requestId)}/cancellation`, request,
    );
  }
}
