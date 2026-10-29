import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PointOfSaleApiService } from './point-of-sale-api.service';

describe('PointOfSaleApiService', () => {
  let service: PointOfSaleApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PointOfSaleApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('keeps a cancellation explicit about the physical-delivery condition', () => {
    const body = { operationId: 'operation-1', reason: 'No salió del local.', itemsLeftPremises: false };
    service.cancelSale('sale-1', body).subscribe();
    const request = http.expectOne('/api/v1/pos/sales/sale-1/cancellations');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({ data: { id: 'cancellation-1', saleId: 'sale-1', ...body, stockRestocked: true } });
  });

  it('separates return request, physical receipt, inspection and closure routes', () => {
    const returnRequest = { operationId: 'operation-2', reason: 'Retorno físico.', lines: [{ saleLineId: 'sale-line-1', quantity: 2 }] };
    service.requestReturn('sale-1', returnRequest).subscribe();
    const request = http.expectOne('/api/v1/pos/sales/sale-1/returns');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(returnRequest);
    request.flush({ data: { id: 'return-1', status: 'SOLICITADA', lines: [] } });

    const receipt = [{ returnLineId: 'return-line-1', receivedQuantity: 2 }];
    service.receiveReturn('return-1', receipt).subscribe();
    const received = http.expectOne('/api/v1/pos/returns/return-1/receipts');
    expect(received.request.body).toEqual({ lines: receipt });
    received.flush({ data: { id: 'return-1', status: 'RECIBIDA', lines: [] } });

    const inspection = [{ returnLineId: 'return-line-1', restockedQuantity: 1 }];
    service.inspectReturn('return-1', inspection).subscribe();
    const inspected = http.expectOne('/api/v1/pos/returns/return-1/inspections');
    expect(inspected.request.body).toEqual({ lines: inspection });
    inspected.flush({ data: { id: 'return-1', status: 'INSPECCIONADA_APTA', lines: [] } });

    service.closeReturn('return-1').subscribe();
    const closed = http.expectOne('/api/v1/pos/returns/return-1/closures');
    expect(closed.request.method).toBe('POST');
    expect(closed.request.body).toEqual({});
    closed.flush({ data: { id: 'return-1', status: 'CERRADA', lines: [] } });
  });

  it('keeps return history tenant-scoped on the protected endpoint', () => {
    service.listReturns('sale-1').subscribe();
    const request = http.expectOne('/api/v1/pos/returns?saleId=sale-1');
    expect(request.request.method).toBe('GET');
    request.flush({ data: [] });
  });
});
