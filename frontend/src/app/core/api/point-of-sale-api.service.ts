import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ApiEnvelope,
  PointOfSaleProduct,
  PosSale,
  PosSaleCancellation,
  PosSaleCancellationRequest,
  PosSaleConfirmationRequest,
  PosSaleRequest,
  PosReturn,
  PosReturnInspectionLineRequest,
  PosReturnReceiptLineRequest,
  PosReturnRequest,
} from './api.models';

@Injectable({ providedIn: 'root' })
export class PointOfSaleApiService {
  private readonly http = inject(HttpClient);

  listProducts(query?: string): Observable<ApiEnvelope<PointOfSaleProduct[]>> {
    let params = new HttpParams();
    if (query?.trim()) {
      params = params.set('query', query.trim());
    }
    return this.http.get<ApiEnvelope<PointOfSaleProduct[]>>('/api/v1/pos/products', { params });
  }

  listSales(): Observable<ApiEnvelope<PosSale[]>> {
    return this.http.get<ApiEnvelope<PosSale[]>>('/api/v1/pos/sales');
  }

  findSale(saleId: string): Observable<ApiEnvelope<PosSale>> {
    return this.http.get<ApiEnvelope<PosSale>>(`/api/v1/pos/sales/${encodeURIComponent(saleId)}`);
  }

  createSale(request: PosSaleRequest): Observable<ApiEnvelope<PosSale>> {
    return this.http.post<ApiEnvelope<PosSale>>('/api/v1/pos/sales', request);
  }

  confirmSale(saleId: string, request: PosSaleConfirmationRequest): Observable<ApiEnvelope<PosSale>> {
    return this.http.post<ApiEnvelope<PosSale>>(
      `/api/v1/pos/sales/${encodeURIComponent(saleId)}/confirmations`, request,
    );
  }

  cancelSale(saleId: string, request: PosSaleCancellationRequest): Observable<ApiEnvelope<PosSaleCancellation>> {
    return this.http.post<ApiEnvelope<PosSaleCancellation>>(
      `/api/v1/pos/sales/${encodeURIComponent(saleId)}/cancellations`, request,
    );
  }

  listReturns(saleId?: string): Observable<ApiEnvelope<PosReturn[]>> {
    let params = new HttpParams();
    if (saleId) {
      params = params.set('saleId', saleId);
    }
    return this.http.get<ApiEnvelope<PosReturn[]>>('/api/v1/pos/returns', { params });
  }

  findReturn(returnId: string): Observable<ApiEnvelope<PosReturn>> {
    return this.http.get<ApiEnvelope<PosReturn>>(`/api/v1/pos/returns/${encodeURIComponent(returnId)}`);
  }

  requestReturn(saleId: string, request: PosReturnRequest): Observable<ApiEnvelope<PosReturn>> {
    return this.http.post<ApiEnvelope<PosReturn>>(`/api/v1/pos/sales/${encodeURIComponent(saleId)}/returns`, request);
  }

  receiveReturn(returnId: string, lines: PosReturnReceiptLineRequest[]): Observable<ApiEnvelope<PosReturn>> {
    return this.http.post<ApiEnvelope<PosReturn>>(`/api/v1/pos/returns/${encodeURIComponent(returnId)}/receipts`, { lines });
  }

  inspectReturn(returnId: string, lines: PosReturnInspectionLineRequest[]): Observable<ApiEnvelope<PosReturn>> {
    return this.http.post<ApiEnvelope<PosReturn>>(`/api/v1/pos/returns/${encodeURIComponent(returnId)}/inspections`, { lines });
  }

  closeReturn(returnId: string): Observable<ApiEnvelope<PosReturn>> {
    return this.http.post<ApiEnvelope<PosReturn>>(`/api/v1/pos/returns/${encodeURIComponent(returnId)}/closures`, {});
  }
}
