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

  it('keeps client requests separate from staff confirmation endpoints', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(ScheduleApiService);
    const http = TestBed.inject(HttpTestingController);
    const request = {
      petId: 'pet-1', serviceId: 'service-1', requestedStartsAt: '2030-01-02T14:00:00.000Z', observation: null,
      idempotencyKey: 'operation-1',
    };

    service.createClientScheduleRequest(request).subscribe();
    const create = http.expectOne('/api/v1/client/schedule/requests');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(request);
    create.flush({ data: { ...request, id: 'request-1', status: 'PENDIENTE' } });

    service.confirmScheduleRequest('request-1', 'professional-1').subscribe();
    const confirm = http.expectOne('/api/v1/schedule/requests/request-1/confirmation');
    expect(confirm.request.method).toBe('POST');
    expect(confirm.request.body).toEqual({ professionalPrincipalId: 'professional-1' });
    confirm.flush({ data: { id: 'reservation-1', requestId: 'request-1', status: 'CONFIRMADA' } });
    http.verify();
  });

  it('keeps CLIENT cancellation separate from an authorized staff resolution', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(ScheduleApiService);
    const http = TestBed.inject(HttpTestingController);

    service.cancelClientScheduleRequest('request-1', { reason: null }).subscribe();
    const clientCancellation = http.expectOne('/api/v1/client/schedule/requests/request-1/cancellation');
    expect(clientCancellation.request.method).toBe('POST');
    expect(clientCancellation.request.body).toEqual({ reason: null });
    clientCancellation.flush({ data: { id: 'request-1', status: 'CANCELACION_SOLICITADA' } });

    const resolution = { decision: 'CANCELADA' as const, reason: 'Cancelacion aprobada.' };
    service.resolveCancellation('request-1', resolution).subscribe();
    const staffResolution = http.expectOne('/api/v1/schedule/requests/request-1/cancellation-resolution');
    expect(staffResolution.request.method).toBe('POST');
    expect(staffResolution.request.body).toEqual(resolution);
    staffResolution.flush({ data: { id: 'request-1', status: 'CANCELADA' } });
    http.verify();
  });

  it('separates a time-changing proposal from a staff-only reassignment', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(ScheduleApiService);
    const http = TestBed.inject(HttpTestingController);
    const proposal = {
      proposedStartsAt: '2030-01-02T15:00:00.000Z', professionalPrincipalId: 'professional-2', reason: 'Cambio solicitado.',
    };
    const reassignment = { professionalPrincipalId: 'professional-3', reason: 'Reemplazo disponible.' };

    service.proposeScheduleAlternative('request-1', proposal).subscribe();
    const proposalRequest = http.expectOne('/api/v1/schedule/requests/request-1/proposals');
    expect(proposalRequest.request.method).toBe('POST');
    expect(proposalRequest.request.body).toEqual(proposal);
    proposalRequest.flush({ data: { id: 'proposal-1', requestId: 'request-1', status: 'ENVIADA' } });

    service.reassignConfirmedScheduleProfessional('request-1', reassignment).subscribe();
    const reassignmentRequest = http.expectOne('/api/v1/schedule/requests/request-1/reassignment');
    expect(reassignmentRequest.request.method).toBe('POST');
    expect(reassignmentRequest.request.body).toEqual(reassignment);
    reassignmentRequest.flush({ data: { id: 'reservation-1', requestId: 'request-1', status: 'CONFIRMADA' } });
    http.verify();
  });

  it('exposes proposals only through the CLIENT projection and decision route', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(ScheduleApiService);
    const http = TestBed.inject(HttpTestingController);

    service.listClientScheduleProposals().subscribe();
    const list = http.expectOne('/api/v1/client/schedule/proposals');
    expect(list.request.method).toBe('GET');
    list.flush({ data: [] });

    service.decideClientScheduleProposal('proposal-1', { decision: 'ACEPTADA' }).subscribe();
    const decision = http.expectOne('/api/v1/client/schedule/proposals/proposal-1/decision');
    expect(decision.request.method).toBe('POST');
    expect(decision.request.body).toEqual({ decision: 'ACEPTADA' });
    decision.flush({ data: { id: 'request-1', status: 'PENDIENTE_REPROGRAMACION' } });
    http.verify();
  });

  it('uses dedicated GROOMING routes for an assigned operational task', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(ScheduleApiService);
    const http = TestBed.inject(HttpTestingController);

    service.listGroomingTasks().subscribe();
    const list = http.expectOne('/api/v1/grooming/tasks');
    expect(list.request.method).toBe('GET');
    list.flush({ data: [] });

    service.startGroomingAttention('reservation-1').subscribe();
    const start = http.expectOne('/api/v1/grooming/reservations/reservation-1/start');
    expect(start.request.method).toBe('POST');
    expect(start.request.body).toBeNull();
    start.flush({ data: { id: 'attention-1', reservationId: 'reservation-1' } });

    const completion = { result: 'Servicio terminado', operationalNotes: null };
    service.completeGroomingAttention('reservation-1', completion).subscribe();
    const complete = http.expectOne('/api/v1/grooming/reservations/reservation-1/complete');
    expect(complete.request.method).toBe('POST');
    expect(complete.request.body).toEqual(completion);
    complete.flush({ data: { id: 'attention-1', reservationId: 'reservation-1', completedAt: '2030-01-01T00:00:00Z' } });
    http.verify();
  });

  it('keeps exceptional blocks and no-show registration on protected staff routes', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(ScheduleApiService);
    const http = TestBed.inject(HttpTestingController);
    const block = {
      blockScope: 'PROFESIONAL' as const,
      professionalPrincipalId: 'professional-1',
      startsAt: '2030-01-02T14:00:00.000Z',
      endsAt: '2030-01-02T14:30:00.000Z',
      reason: 'Ausencia registrada.',
    };

    service.listScheduleBlocks().subscribe();
    const list = http.expectOne('/api/v1/schedule/blocks');
    expect(list.request.method).toBe('GET');
    list.flush({ data: [] });

    service.createScheduleBlock(block).subscribe();
    const create = http.expectOne('/api/v1/schedule/blocks');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual(block);
    create.flush({ data: { ...block, id: 'block-1' } });

    service.markScheduleNoShow('request-1', { reason: 'No se presento.' }).subscribe();
    const noShow = http.expectOne('/api/v1/schedule/requests/request-1/no-show');
    expect(noShow.request.method).toBe('POST');
    expect(noShow.request.body).toEqual({ reason: 'No se presento.' });
    noShow.flush({ data: { id: 'request-1', status: 'INASISTENCIA' } });
    http.verify();
  });

  it('reads the protected administrative schedule history without sending identity data', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const service = TestBed.inject(ScheduleApiService);
    const http = TestBed.inject(HttpTestingController);

    service.listScheduleChangeEvents().subscribe();
    const request = http.expectOne('/api/v1/schedule/changes');
    expect(request.request.method).toBe('GET');
    request.flush({ data: [] });
    http.verify();
  });
});
