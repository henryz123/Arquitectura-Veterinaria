import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ApiEnvelope,
  PointOfSaleProduct,
  PosSale,
  PosSaleConfirmationRequest,
  PosSaleRequest,
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
}
