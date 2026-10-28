import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ApiEnvelope,
  CatalogCategory,
  CatalogCategoryRequest,
  CatalogCategoryStatus,
  CatalogProduct,
  CatalogProductRequest,
  CatalogProductStatus,
} from './api.models';

@Injectable({ providedIn: 'root' })
export class CatalogApiService {
  private readonly http = inject(HttpClient);

  listCategories(query?: string, status?: CatalogCategoryStatus): Observable<ApiEnvelope<CatalogCategory[]>> {
    let params = new HttpParams();
    if (query?.trim()) {
      params = params.set('query', query.trim());
    }
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<ApiEnvelope<CatalogCategory[]>>('/api/v1/catalog/categories', { params });
  }

  createCategory(request: CatalogCategoryRequest): Observable<ApiEnvelope<CatalogCategory>> {
    return this.http.post<ApiEnvelope<CatalogCategory>>('/api/v1/catalog/categories', request);
  }

  updateCategory(categoryId: string, request: CatalogCategoryRequest): Observable<ApiEnvelope<CatalogCategory>> {
    return this.http.put<ApiEnvelope<CatalogCategory>>(`/api/v1/catalog/categories/${encodeURIComponent(categoryId)}`, request);
  }

  listProducts(query?: string, status?: CatalogProductStatus, categoryId?: string): Observable<ApiEnvelope<CatalogProduct[]>> {
    let params = new HttpParams();
    if (query?.trim()) {
      params = params.set('query', query.trim());
    }
    if (status) {
      params = params.set('status', status);
    }
    if (categoryId) {
      params = params.set('categoryId', categoryId);
    }
    return this.http.get<ApiEnvelope<CatalogProduct[]>>('/api/v1/catalog/products', { params });
  }

  createProduct(request: CatalogProductRequest): Observable<ApiEnvelope<CatalogProduct>> {
    return this.http.post<ApiEnvelope<CatalogProduct>>('/api/v1/catalog/products', request);
  }

  updateProduct(productId: string, request: CatalogProductRequest): Observable<ApiEnvelope<CatalogProduct>> {
    return this.http.put<ApiEnvelope<CatalogProduct>>(`/api/v1/catalog/products/${encodeURIComponent(productId)}`, request);
  }
}
