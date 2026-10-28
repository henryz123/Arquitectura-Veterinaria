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
  ScheduleBlock,
  ScheduleBlockRequest,
  ScheduleChangeEvent,
  ScheduleNoShowRequest,
  ClientScheduleRequest,
  ClientProposalDecisionRequest,
  ClientScheduleProposal,

  ClientScheduleCancellationRequest,
  GroomingAttention,
  GroomingCompletionRequest,
  GroomingTask,

  ScheduleCancellationResolutionRequest,
  ScheduleAvailability,
  ScheduleRequest,
  ScheduleReservation,
  ScheduleProposal,
  ScheduleProposalRequest,
  ScheduleReassignmentRequest,
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

  listScheduleBlocks(): Observable<ApiEnvelope<ScheduleBlock[]>> {
    return this.http.get<ApiEnvelope<ScheduleBlock[]>>('/api/v1/schedule/blocks');
  }

  createScheduleBlock(request: ScheduleBlockRequest): Observable<ApiEnvelope<ScheduleBlock>> {
    return this.http.post<ApiEnvelope<ScheduleBlock>>('/api/v1/schedule/blocks', request);
  }

  listScheduleChangeEvents(): Observable<ApiEnvelope<ScheduleChangeEvent[]>> {
    return this.http.get<ApiEnvelope<ScheduleChangeEvent[]>>('/api/v1/schedule/changes');
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

  proposeScheduleAlternative(requestId: string, request: ScheduleProposalRequest): Observable<ApiEnvelope<ScheduleProposal>> {
    return this.http.post<ApiEnvelope<ScheduleProposal>>(
      `/api/v1/schedule/requests/${encodeURIComponent(requestId)}/proposals`, request,
    );
  }

  reassignConfirmedScheduleProfessional(requestId: string, request: ScheduleReassignmentRequest): Observable<ApiEnvelope<ScheduleReservation>> {
    return this.http.post<ApiEnvelope<ScheduleReservation>>(
      `/api/v1/schedule/requests/${encodeURIComponent(requestId)}/reassignment`, request,
    );
  }

  resolveCancellation(requestId: string, request: ScheduleCancellationResolutionRequest): Observable<ApiEnvelope<ScheduleRequest>> {
    return this.http.post<ApiEnvelope<ScheduleRequest>>(
      `/api/v1/schedule/requests/${encodeURIComponent(requestId)}/cancellation-resolution`, request,
    );
  }

  markScheduleNoShow(requestId: string, request: ScheduleNoShowRequest): Observable<ApiEnvelope<ScheduleRequest>> {
    return this.http.post<ApiEnvelope<ScheduleRequest>>(
      `/api/v1/schedule/requests/${encodeURIComponent(requestId)}/no-show`, request,
    );
  }

  listGroomingTasks(): Observable<ApiEnvelope<GroomingTask[]>> {
    return this.http.get<ApiEnvelope<GroomingTask[]>>('/api/v1/grooming/tasks');
  }

  startGroomingAttention(reservationId: string): Observable<ApiEnvelope<GroomingAttention>> {
    return this.http.post<ApiEnvelope<GroomingAttention>>(
      `/api/v1/grooming/reservations/${encodeURIComponent(reservationId)}/start`, null,
    );
  }

  completeGroomingAttention(reservationId: string, request: GroomingCompletionRequest): Observable<ApiEnvelope<GroomingAttention>> {
    return this.http.post<ApiEnvelope<GroomingAttention>>(
      `/api/v1/grooming/reservations/${encodeURIComponent(reservationId)}/complete`, request,
    );
  }

  listClientScheduleServices(): Observable<ApiEnvelope<OfferedService[]>> {
    return this.http.get<ApiEnvelope<OfferedService[]>>('/api/v1/client/schedule/services');
  }

  listClientScheduleRequests(): Observable<ApiEnvelope<ScheduleRequest[]>> {
    return this.http.get<ApiEnvelope<ScheduleRequest[]>>('/api/v1/client/schedule/requests');
  }

  listClientScheduleProposals(): Observable<ApiEnvelope<ClientScheduleProposal[]>> {
    return this.http.get<ApiEnvelope<ClientScheduleProposal[]>>('/api/v1/client/schedule/proposals');
  }

  createClientScheduleRequest(request: ClientScheduleRequest): Observable<ApiEnvelope<ScheduleRequest>> {
    return this.http.post<ApiEnvelope<ScheduleRequest>>('/api/v1/client/schedule/requests', request);
  }

  cancelClientScheduleRequest(requestId: string, request: ClientScheduleCancellationRequest): Observable<ApiEnvelope<ScheduleRequest>> {
    return this.http.post<ApiEnvelope<ScheduleRequest>>(
      `/api/v1/client/schedule/requests/${encodeURIComponent(requestId)}/cancellation`, request,
    );
  }

  decideClientScheduleProposal(proposalId: string, request: ClientProposalDecisionRequest): Observable<ApiEnvelope<ScheduleRequest>> {
    return this.http.post<ApiEnvelope<ScheduleRequest>>(
      `/api/v1/client/schedule/proposals/${encodeURIComponent(proposalId)}/decision`, request,
    );
  }
}
